// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.io.FileUtil;
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

  public static @NotNull Pair<PsiElement, PsiType> getPsiClassAndType(@Nullable String className, Project project) {
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
    if (superType.equals(type) || (!(type instanceof InterfaceType) && CommonClassNames.JAVA_LANG_OBJECT.equals(superType.name()))) {
      return true;
    }
    if (type instanceof ArrayType arrayType) {
      if (superType instanceof ArrayType superArrayType) {
        try {
          Type componentType = arrayType.componentType();
          Type superComponentType = superArrayType.componentType();
          if (componentType instanceof PrimitiveType) {
            return componentType.equals(superComponentType);
          }
          else {
            if (superComponentType instanceof PrimitiveType) {
              return false;
            }
            return instanceOf((ReferenceType)componentType, (ReferenceType)superComponentType);
          }
        }
        catch (ClassNotLoadedException e) {
          LOG.info(e);
          return false;
        }
      }
      else {
        String superName = superType.name();
        return CommonClassNames.JAVA_LANG_CLONEABLE.equals(superName) || CommonClassNames.JAVA_IO_SERIALIZABLE.equals(superName);
      }
    }
    if (superType instanceof ClassType) { // may check superclass only
      if (type instanceof InterfaceType) {
        return false;
      }
      if (type instanceof ClassType classType) {
        ClassType superclass = classType.superclass();
        return superclass != null && instanceOf(superclass, superType);
      }
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
          return suspendAllAndEvaluate(valueComputable, processor, evaluationContext);
        }
      }
      catch (EvaluateException e) {
        if (e.getCause() instanceof ObjectCollectedException) {
          if (--retries < 0) {
            return suspendAllAndEvaluate(valueComputable, processor, evaluationContext);
          }
        }
        else {
          throw e;
        }
      }
    }
  }

  private static <R, T> R suspendAllAndEvaluate(@NotNull ThrowableComputable<? extends T, ? extends EvaluateException> valueComputable,
                                                @NotNull Function<? super T, ? extends R> processor,
                                                @NotNull EvaluationContext evaluationContext) throws EvaluateException {
    LOG.error("Retries exhausted, applying suspend-all evaluation");
    VirtualMachineProxyImpl virtualMachineProxy = ((EvaluationContextImpl)evaluationContext).getSuspendContext().getVirtualMachineProxy();
    virtualMachineProxy.suspend();
    try {
      return processor.apply(valueComputable.compute());
    }
    finally {
      virtualMachineProxy.resume();
    }
  }

  private static final Key<Method> TO_STRING_METHOD_KEY = new Key<>("CachedToStringMethod");

  @Override
  protected String getValueAsStringImpl(@NotNull EvaluationContext evaluationContext, @Nullable Value value) throws EvaluateException {
    try {
      if (value == null) {
        return "null";
      }
      if (value instanceof StringReference) {
        ensureNotInsideObjectConstructor((ObjectReference)value, evaluationContext);
        return ((StringReference)value).value();
      }
      if (isInteger(value)) {
        return String.valueOf(((PrimitiveValue)value).longValue());
      }
      if (value instanceof FloatValue) {
        return String.valueOf(((FloatValue)value).floatValue());
      }
      if (value instanceof DoubleValue) {
        return String.valueOf(((DoubleValue)value).doubleValue());
      }
      if (value instanceof BooleanValue) {
        return String.valueOf(((PrimitiveValue)value).booleanValue());
      }
      if (value instanceof CharValue) {
        return String.valueOf(((PrimitiveValue)value).charValue());
      }
      if (value instanceof ObjectReference objRef) {
        // We can not pretty print arrays here, otherwise evaluation may fail unexpectedly, check IDEA-358202
        //if (value instanceof ArrayReference arrayRef) {
        //  final StringJoiner joiner = new StringJoiner(",", "[", "]");
        //  for (final Value element : arrayRef.getValues()) {
        //    joiner.add(getValueAsString(evaluationContext, element));
        //  }
        //  return joiner.toString();
        //}

        EvaluationContextImpl evaluationContextImpl = (EvaluationContextImpl)evaluationContext;
        VirtualMachineProxyImpl virtualMachineProxy = evaluationContextImpl.getVirtualMachineProxy();
        Method toStringMethod = virtualMachineProxy.getUserData(TO_STRING_METHOD_KEY);
        if (toStringMethod == null) {
          try {
            ReferenceType refType = getObjectClassType(objRef.virtualMachine());
            toStringMethod = findMethod(refType, "toString", "()Ljava/lang/String;");
            virtualMachineProxy.putUserData(TO_STRING_METHOD_KEY, toStringMethod);
          }
          catch (Exception ignored) {
            throw EvaluateExceptionUtil.createEvaluateException(
              JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", objRef.referenceType().name()));
          }
        }
        if (toStringMethod == null) {
          throw EvaluateExceptionUtil.createEvaluateException(
            JavaDebuggerBundle.message("evaluation.error.cannot.evaluate.tostring", objRef.referenceType().name()));
        }
        Method finalToStringMethod = toStringMethod;
        return processCollectibleValue(
          () -> evaluationContextImpl.getDebugProcess()
            .invokeInstanceMethod(evaluationContext, objRef, finalToStringMethod, Collections.emptyList(), 0, true),
          result -> {
            // while result must be of com.sun.jdi.StringReference type, it turns out that sometimes (jvm bugs?)
            // it is a plain com.sun.tools.jdi.ObjectReferenceImpl
            if (result == null) {
              return "null";
            }
            return result instanceof StringReference ? ((StringReference)result).value() : result.toString();
          },
          evaluationContext);
      }
      throw EvaluateExceptionUtil.createEvaluateException(JavaDebuggerBundle.message("evaluation.error.unsupported.expression.type"));
    }
    catch (ObjectCollectedException ignored) {
      throw EvaluateExceptionUtil.OBJECT_WAS_COLLECTED;
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

  /**
   * Searches for rt.jar path preferring the source version to the installation version.
   */
  public static @NotNull String getIdeaRtPath() {
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
            String testPath = FileUtil.toSystemIndependentName(path);
            String testResourcePath = FileUtil.toSystemIndependentName(resourcePath);
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

  public static @NotNull CompletableFuture<NodeRenderer> getFirstApplicableRenderer(List<NodeRenderer> renderers, Type type) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    return getFirstApplicableRenderer(ContainerUtil.map(renderers, r -> r.isApplicableAsync(type)), 0, renderers);
  }

  public static @NotNull CompletableFuture<List<NodeRenderer>> getApplicableRenderers(List<? extends NodeRenderer> renderers, Type type) {
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

  public static @Nullable XValueMarkers<?, ?> getValueMarkers(@Nullable DebugProcess process) {
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

  public static @Nullable Value invokeClassMethod(@NotNull EvaluationContext evaluationContext,
                                                  @NotNull ClassType type,
                                                  @NotNull String methodName,
                                                  @Nullable String signature,
                                                  @NotNull List<Value> arguments) throws EvaluateException {
    Method method = findMethod(type, methodName, signature);
    if (method == null) {
      throw new MethodNotFoundException("Method " + methodName + ", signature " + signature + " not found in class " + type.name());
    }
    return evaluationContext.getDebugProcess().invokeMethod(evaluationContext, type, method, arguments);
  }

  public static @Nullable Value invokeObjectMethod(@NotNull EvaluationContextImpl evaluationContext,
                                                   @NotNull ObjectReference value,
                                                   @NotNull String methodName,
                                                   @Nullable String signature,
                                                   @NotNull List<Value> arguments) throws EvaluateException {
    ReferenceType type = value.referenceType();
    Method method = findMethod(type, methodName, signature);
    if (method == null) {
      throw new MethodNotFoundException("Method " + methodName + ", signature " + signature + " not found in class " + type.name());
    }
    return evaluationContext.getDebugProcess().invokeMethod(evaluationContext, value, method, arguments);
  }

  /**
   * Invokes a specified helper method from a given class
   *
   * @return the result of the invoked helper method as a {@link Value} object
   * @throws HelperClassNotAvailableException if the helper class cannot be loaded
   * @throws MethodNotFoundException if the method is not found in the helper class
   * @throws EvaluateException any other exception during the evaluation
   */
  public static Value invokeHelperMethod(EvaluationContextImpl evaluationContext,
                                         Class<?> cls,
                                         String methodName,
                                         List<Value> arguments,
                                         boolean keepResult,
                                         String... additionalClassesToLoad) throws EvaluateException {
    ClassType helperClass = ClassLoadingUtils.getHelperClass(cls, evaluationContext, additionalClassesToLoad);
    if (helperClass == null) {
      throw new HelperClassNotAvailableException("Unable to load helper class " + cls.getName());
    }
    Method method = findMethod(helperClass, methodName, null);
    if (method == null) {
      throw new MethodNotFoundException("Unable to find helper class " + cls.getName() + " method " + methodName);
    }
    DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
    ThrowableComputable<Value, EvaluateException> invoker =
      () -> debugProcess.invokeMethod(evaluationContext, helperClass, method, arguments, MethodImpl.SKIP_ASSIGNABLE_CHECK, true);
    return keepResult ? evaluationContext.computeAndKeep(invoker) : invoker.compute();
  }

  /**
   * Invokes a specified helper method from a given class
   *
   * @return the result of the invoked helper method as a {@link Value} object
   * @throws HelperClassNotAvailableException if the helper class cannot be loaded
   * @throws MethodNotFoundException if the method is not found in the helper class
   * @throws EvaluateException any other exception during the evaluation
   */
  public static Value invokeHelperMethod(EvaluationContextImpl evaluationContext,
                                         Class<?> cls,
                                         String methodName,
                                         List<Value> arguments) throws EvaluateException {
    return invokeHelperMethod(evaluationContext, cls, methodName, arguments, true);
  }

  public static @Nullable String getExceptionText(EvaluationContextImpl evaluationContext, @NotNull ObjectReference exceptionObject)
    throws EvaluateException {
    try {
      Value value = invokeHelperMethod(evaluationContext,
                                 ExceptionDebugHelper.class,
                                 "getThrowableText",
                                 Collections.singletonList(exceptionObject));
      return ((StringReference)value).value();
    }
    catch (HelperClassNotAvailableException | MethodNotFoundException e) {
      LOG.error(e);
    }
    // fallback to slow impl
    return MethodInvokeUtils.getExceptionTextViaArray(evaluationContext, exceptionObject);
  }

  public static @Nullable ArrayReference invokeThrowableGetStackTrace(@NotNull ObjectReference exceptionObj,
                                                                      @NotNull EvaluationContextImpl evaluationContext,
                                                                      boolean keepResult) throws EvaluateException {
    if (instanceOf(exceptionObj.type(), "java.lang.Throwable")) {
      Method method = findMethod(exceptionObj.referenceType(), "getStackTrace", "()[Ljava/lang/StackTraceElement;");
      DebugProcessImpl debugProcess = evaluationContext.getDebugProcess();
      ThrowableComputable<ArrayReference, EvaluateException> invoker = () -> (ArrayReference)debugProcess.invokeInstanceMethod(
        evaluationContext, exceptionObj, Objects.requireNonNull(method), Collections.emptyList(), 0, true);
      return keepResult ? evaluationContext.computeAndKeep(invoker) : invoker.compute();
    }
    return null;
  }
}