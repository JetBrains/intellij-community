// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.debugger.DebuggerGlobalSearchScope;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.impl.attach.PidRemoteConnection;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.debugger.ui.tree.render.NodeRenderer;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.rt.debugger.ExceptionDebugHelper;
import com.intellij.rt.execution.CommandLineWrapper;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.breakpoints.XExpressionState;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.jetbrains.jdi.MethodImpl;
import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

public final class DebuggerUtilsImpl extends DebuggerUtilsEx {
  public static final Key<PsiType> PSI_TYPE_KEY = Key.create("PSI_TYPE_KEY");
  private static final Logger LOG = Logger.getInstance(DebuggerUtilsImpl.class);

  @Override
  public PsiExpression substituteThis(PsiExpression expressionWithThis,
                                      PsiExpression howToEvaluateThis,
                                      Value howToEvaluateThisValue,
                                      StackFrameContext context)
    throws EvaluateException {
    return DebuggerTreeNodeExpression.substituteThis(expressionWithThis, howToEvaluateThis, howToEvaluateThisValue);
  }

  @Override
  public DebuggerContextImpl getDebuggerContext(DataContext context) {
    return DebuggerAction.getDebuggerContext(context);
  }

  @Override
  public Element writeTextWithImports(TextWithImports text) {
    Element element = new Element("TextWithImports");

    element.setAttribute("text", text.toExternalForm());
    element.setAttribute("type", text.getKind() == CodeFragmentKind.EXPRESSION ? "expression" : "code fragment");
    return element;
  }

  @Override
  public TextWithImports readTextWithImports(Element element) {
    LOG.assertTrue("TextWithImports".equals(element.getName()));

    String text = element.getAttributeValue("text");
    if ("expression".equals(element.getAttributeValue("type"))) {
      return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text);
    }
    else {
      return new TextWithImportsImpl(CodeFragmentKind.CODE_BLOCK, text);
    }
  }

  @Override
  public void writeTextWithImports(Element root, String name, TextWithImports value) {
    if (value.getKind() == CodeFragmentKind.EXPRESSION) {
      JDOMExternalizerUtil.writeField(root, name, value.toExternalForm());
    }
    else {
      Element element = JDOMExternalizerUtil.writeOption(root, name);
      XExpression expression = TextWithImportsImpl.toXExpression(value);
      if (expression != null) {
        XmlSerializer.serializeObjectInto(new XExpressionState(expression), element);
      }
    }
  }

  @Override
  public TextWithImports readTextWithImports(Element root, String name) {
    String s = JDOMExternalizerUtil.readField(root, name);
    if (s != null) {
      return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, s);
    }
    else {
      Element option = JDOMExternalizerUtil.readOption(root, name);
      if (option != null) {
        XExpressionState state = new XExpressionState();
        XmlSerializer.deserializeInto(option, state);
        return TextWithImportsImpl.fromXExpression(state.toXExpression());
      }
    }
    return null;
  }

  @Override
  public TextWithImports createExpressionWithImports(String expression) {
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression);
  }

  @Override
  public PsiElement getContextElement(StackFrameContext context) {
    return PositionUtil.getContextElement(context);
  }

  @NotNull
  public static Pair<PsiElement, PsiType> getPsiClassAndType(@Nullable String className, Project project) {
    PsiElement contextClass = null;
    PsiType contextType = null;
    if (!StringUtil.isEmpty(className)) {
      PsiPrimitiveType primitiveType = PsiJavaParserFacadeImpl.getPrimitiveType(className);
      if (primitiveType != null) {
        contextClass = JavaPsiFacade.getInstance(project).findClass(primitiveType.getBoxedTypeName(), GlobalSearchScope.allScope(project));
        contextType = primitiveType;
      }
      else {
        contextClass = findClass(className, project, GlobalSearchScope.allScope(project));
        if (contextClass != null) {
          contextClass = contextClass.getNavigationElement();
        }
        if (contextClass instanceof PsiCompiledElement) {
          contextClass = ((PsiCompiledElement)contextClass).getMirror();
        }
        contextType = getType(className, project);
      }
      if (contextClass != null) {
        contextClass.putUserData(PSI_TYPE_KEY, contextType);
      }
    }
    return Pair.create(contextClass, contextType);
  }

  @Override
  public PsiClass chooseClassDialog(@NlsContexts.DialogTitle String title, Project project) {
    TreeClassChooser dialog = TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser(title);
    dialog.showDialog();
    return dialog.getSelected();
  }

  @Override
  public String findAvailableDebugAddress(boolean useSockets) throws ExecutionException {
    if (useSockets) {
      final int freePort;
      try {
        freePort = NetUtils.findAvailableSocketPort();
      }
      catch (IOException e) {
        throw new ExecutionException(DebugProcessImpl.processError(e));
      }
      return Integer.toString(freePort);
    }
    else {
      ListeningConnector connector = (ListeningConnector)DebugProcessImpl.findConnector(false, true);
      try {
        return tryShmemConnect(connector, "");
      }
      catch (Exception e) {
        int tryNum = 0;
        while (true) {
          try {
            return tryShmemConnect(connector, "javadebug_" + (int)(Math.random() * 1000));
          }
          catch (Exception ex) {
            if (tryNum++ > 10) {
              throw new ExecutionException(DebugProcessImpl.processError(ex));
            }
          }
        }
      }
    }
  }

  private static String tryShmemConnect(ListeningConnector connector, String address)
    throws IOException, IllegalConnectorArgumentsException {
    Map<String, Connector.Argument> map = connector.defaultArguments();
    map.get("name").setValue(address);
    address = connector.startListening(map);
    connector.stopListening(map);
    return address;
  }

  public static boolean isRemote(DebugProcess debugProcess) {
    return Boolean.TRUE.equals(debugProcess.getUserData(BatchEvaluator.REMOTE_SESSION_KEY));
  }

  public static void logError(@NotNull Throwable e) {
    logIfNeeded(e, false, LOG::error);
  }

  public static void logError(String message, Throwable e) {
    logIfNeeded(e, false, t -> LOG.error(message, t));
  }

  public static void logError(String message, @Nullable Throwable e, String @NotNull ... details) {
    logIfNeeded(e, false, t -> LOG.error(message, t, details));
  }

  static void logError(String message, Throwable e, boolean wrapIntoThrowable) {
    logIfNeeded(e, wrapIntoThrowable, t -> LOG.error(message, t));
  }

  private static void logIfNeeded(Throwable e, boolean wrapIntoThrowable, Consumer<Throwable> action) {
    if (e instanceof VMDisconnectedException || e instanceof ProcessCanceledException) {
      throw (RuntimeException)e;
    }
    if (e instanceof InterruptedException) {
      throw new RuntimeException(e);
    }
    action.accept(wrapIntoThrowable ? new Throwable(e) : e);
  }

  public static @NlsContexts.Label String getConnectionWaitStatus(@NotNull RemoteConnection connection) {
    String connectionName = ObjectUtils.doIfNotNull(connection, DebuggerUtilsImpl::getConnectionDisplayName);
    return connection instanceof RemoteConnectionStub
           ? JavaDebuggerBundle.message("status.waiting.attach")
           : connection.isServerMode()
             ? JavaDebuggerBundle.message("status.listening", connectionName)
             : JavaDebuggerBundle.message("status.connecting", connectionName);
  }

  public static String getConnectionDisplayName(@NotNull RemoteConnection connection) {
    if (connection instanceof PidRemoteConnection) {
      return "pid " + ((PidRemoteConnection)connection).getPid();
    }
    String addressDisplayName = JavaDebuggerBundle.getAddressDisplayName(connection);
    String transportName = JavaDebuggerBundle.getTransportName(connection);
    return JavaDebuggerBundle.message("string.connection", addressDisplayName, transportName);
  }

  public static boolean instanceOf(@Nullable ReferenceType type, @NotNull ReferenceType superType) {
    if (type == null) {
      return false;
    }
    if (superType.equals(type) || CommonClassNames.JAVA_LANG_OBJECT.equals(superType.name())) {
      return true;
    }
    if (type instanceof ArrayType) {
      String superName = superType.name();
      return CommonClassNames.JAVA_LANG_CLONEABLE.equals(superName) || CommonClassNames.JAVA_IO_SERIALIZABLE.equals(superName);
    }
    return supertypes(type).anyMatch(t -> instanceOf(t, superType));
  }

  public static Stream<? extends ReferenceType> supertypes(ReferenceType type) {
    if (type instanceof InterfaceType) {
      return ((InterfaceType)type).superinterfaces().stream();
    }
    else if (type instanceof ClassType) {
      return StreamEx.<ReferenceType>ofNullable(((ClassType)type).superclass()).prepend(((ClassType)type).interfaces());
    }
    return StreamEx.empty();
  }

  public static byte @Nullable [] readBytesArray(Value bytesArray) {
    if (bytesArray instanceof ArrayReference) {
      List<Value> values = ((ArrayReference)bytesArray).getValues();
      byte[] res = new byte[values.size()];
      int idx = 0;
      for (Value value : values) {
        if (value instanceof ByteValue) {
          res[idx++] = ((ByteValue)value).value();
        }
        else {
          return null;
        }
      }
      return res;
    }
    return null;
  }

  @Override
  protected Location getLocation(SuspendContext context) {
    return ((SuspendContextImpl)context).getLocation();
  }

  @Override
  public <R, T> R processCollectibleValue(
    @NotNull ThrowableComputable<? extends T, ? extends EvaluateException> valueComputable,
    @NotNull Function<? super T, ? extends R> processor,
    @NotNull EvaluationContext evaluationContext) throws EvaluateException {
    int retries = 3;
    while (true) {
      try {
        T result = valueComputable.compute();
        return processor.apply(result);
      }
      catch (ObjectCollectedException oce) {
        if (--retries < 0) {
          LOG.error("Retries exhausted, apply suspend-all evaluation");
          if (evaluationContext.getSuspendContext() instanceof SuspendContextImpl suspendContextImpl) {
            VirtualMachineProxyImpl virtualMachineProxy = suspendContextImpl.getVirtualMachineProxy();
            virtualMachineProxy.suspend();
            try {
              return processor.apply(valueComputable.compute());
            } finally {
              virtualMachineProxy.resume();
            }
          }
          else {
            throw oce;
          }
        }
      }
    }
  }

  // compilable version of array class for compiling evaluator
  private static final String ARRAY_CLASS_NAME = "__Dummy_Array__";
  private static final String ARRAY_CLASS_TEXT =
    "public class " + ARRAY_CLASS_NAME + "<T> {" +
    "  public final int length;" +
    "  private " + ARRAY_CLASS_NAME + "(int l) {length = l;}" +
    "  public T[] clone() {return null;}" +
    "}";

  @Override
  protected PsiClass createArrayClass(Project project, LanguageLevel level) {
    PsiFile psiFile =
      PsiFileFactory.getInstance(project).createFileFromText(ARRAY_CLASS_NAME + "." + JavaFileType.INSTANCE.getDefaultExtension(),
                                                             JavaFileType.INSTANCE.getLanguage(),
                                                             ARRAY_CLASS_TEXT);
    PsiUtil.FILE_LANGUAGE_LEVEL_KEY.set(psiFile, level);
    return ((PsiJavaFile)psiFile).getClasses()[0];
  }

  @Override
  protected @Nullable GlobalSearchScope getFallbackAllScope(@NotNull GlobalSearchScope scope, @NotNull Project project) {
    if (scope instanceof DebuggerGlobalSearchScope) {
      return ((DebuggerGlobalSearchScope)scope).fallbackAllScope();
    }
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    return !allScope.equals(scope) ? allScope : null;
  }

  @NotNull
  public static String getIdeaRtPath() {
    if (PluginManagerCore.isRunningFromSources()) {
      Class<?> aClass = CommandLineWrapper.class;
      try {
        String resourcePath = aClass.getName().replace('.', '/') + ".class";
        Enumeration<URL> urls = aClass.getClassLoader().getResources(resourcePath);
        while (urls.hasMoreElements()) {
          URL url = urls.nextElement();
          // prefer dir
          if (url.getProtocol().equals(URLUtil.FILE_PROTOCOL)) {
            String path = URLUtil.urlToFile(url).getPath();
            String testPath = path.replace('\\', '/');
            String testResourcePath = resourcePath.replace('\\', '/');
            if (StringUtilRt.endsWithIgnoreCase(testPath, testResourcePath)) {
              return path.substring(0, path.length() - resourcePath.length() - 1);
            }
          }
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return JavaSdkUtil.getIdeaRtJarPath();
  }

  public static <T> List<List<T>> partition(List<T> list, int size) {
    List<List<T>> res = new ArrayList<>();
    int loaded = 0, total = list.size();
    while (loaded < total) {
      int chunkSize = Math.min(size, total - loaded);
      res.add(list.subList(loaded, loaded + chunkSize));
      loaded += chunkSize;
    }
    return res;
  }

  private static CompletableFuture<NodeRenderer> getFirstApplicableRenderer(List<CompletableFuture<Boolean>> futures,
                                                                            int index,
                                                                            List<NodeRenderer> renderers) {
    if (index >= futures.size()) {
      return CompletableFuture.completedFuture(null);
    }
    return futures.get(index).thenCompose(res -> {
      if (res) {
        return CompletableFuture.completedFuture(renderers.get(index));
      }
      else {
        return getFirstApplicableRenderer(futures, index + 1, renderers);
      }
    });
  }

  @NotNull
  public static CompletableFuture<NodeRenderer> getFirstApplicableRenderer(List<NodeRenderer> renderers, Type type) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return getFirstApplicableRenderer(ContainerUtil.map(renderers, r -> r.isApplicableAsync(type)), 0, renderers);
  }

  @NotNull
  public static CompletableFuture<List<NodeRenderer>> getApplicableRenderers(List<? extends NodeRenderer> renderers, Type type) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    CompletableFuture<Boolean>[] futures = renderers.stream().map(r -> r.isApplicableAsync(type)).toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures).thenApply(__ -> {
      List<NodeRenderer> res = new SmartList<>();
      for (int i = 0; i < futures.length; i++) {
        try {
          if (futures[i].join()) {
            res.add(renderers.get(i));
          }
        }
        catch (Exception e) {
          LOG.debug(e);
        }
      }
      return res;
    });
  }

  @Nullable
  public static XValueMarkers<?, ?> getValueMarkers(@Nullable DebugProcess process) {
    if (process instanceof DebugProcessImpl) {
      XDebugSession session = ((DebugProcessImpl)process).getSession().getXDebugSession();
      if (session instanceof XDebugSessionImpl) {
        return ((XDebugSessionImpl)session).getValueMarkers();
      }
    }
    return null;
  }

  // do not catch VMDisconnectedException
  public static <T> void forEachSafe(ExtensionPointName<T> ep, Consumer<? super T> action) {
    forEachSafe(ep.getIterable(), action);
  }

  // do not catch VMDisconnectedException
  public static <T> void forEachSafe(Iterable<? extends T> iterable, Consumer<? super T> action) {
    for (T o : iterable) {
      try {
        action.accept(o);
      }
      catch (Throwable e) {
        logError(e);
      }
    }
  }

  // do not catch VMDisconnectedException
  public static <T, R> R computeSafeIfAny(ExtensionPointName<T> ep, @NotNull Function<? super T, ? extends R> processor) {
    for (T t : ep.getIterable()) {
      if (t == null) {
        return null;
      }

      try {
        R result = processor.apply(t);
        if (result != null) {
          return result;
        }
      }
      catch (VMDisconnectedException | ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    return null;
  }

  public static @Nullable Value invokeClassMethod(@NotNull EvaluationContext evaluationContext,
                                                  @NotNull ClassType type,
                                                  @NotNull String methodName,
                                                  @Nullable String signature) throws EvaluateException {
    Method method = findMethodOrLogError(type, methodName, signature);
    if (method == null) return null;
    return evaluationContext.getDebugProcess().invokeMethod(evaluationContext, type, method, Collections.emptyList());
  }

  public static @Nullable Value invokeObjectMethod(@NotNull EvaluationContextImpl evaluationContext,
                                                   @NotNull ObjectReference value,
                                                   @NotNull String methodName,
                                                   @Nullable String signature,
                                                   @NotNull List<Value> arguments) throws EvaluateException {
    ReferenceType type = value.referenceType();
    Method method = findMethodOrLogError(type, methodName, signature);
    if (method == null) return null;
    return evaluationContext.getDebugProcess().invokeMethod(evaluationContext, value, method, arguments);
  }

  private static @Nullable Method findMethodOrLogError(ReferenceType type, @NotNull String methodName, @Nullable String signature) {
    Method method = findMethod(type, methodName, signature);
    if (method == null) {
      LOG.error("Method " + methodName + ", signature " + signature + " not found in class " + type.name());
    }
    return method;
  }

  public static Value invokeHelperMethod(EvaluationContextImpl evaluationContext,
                                         Class<?> cls,
                                         String methodName,
                                         List<Value> arguments,
                                         boolean keepResult) throws EvaluateException {
    ClassType helperClass = ClassLoadingUtils.getHelperClass(cls, evaluationContext);
    if (helperClass != null) {
      Method method = findMethod(helperClass, methodName, null);
      if (method != null) {
        DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
        ThrowableComputable<Value, EvaluateException> invoker =
          () -> debugProcess.invokeMethod(evaluationContext, helperClass, method, arguments, MethodImpl.SKIP_ASSIGNABLE_CHECK, true);
        return keepResult ? evaluationContext.computeAndKeep(invoker) : invoker.compute();
      }
    }
    return null;
  }

  public static Value invokeHelperMethod(EvaluationContextImpl evaluationContext,
                                         Class<?> cls,
                                         String methodName,
                                         List<Value> arguments) throws EvaluateException {
    return invokeHelperMethod(evaluationContext, cls, methodName, arguments, true);
  }

  @Nullable
  public static String getExceptionText(EvaluationContextImpl evaluationContext, @NotNull ObjectReference exceptionObject)
    throws EvaluateException {
    Value value = invokeHelperMethod(evaluationContext,
                                     ExceptionDebugHelper.class,
                                     "getThrowableText",
                                     Collections.singletonList(exceptionObject));
    return value != null ? ((StringReference)value).value() : null;
  }
}