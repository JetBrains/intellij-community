// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.impl.attach.PidRemoteConnection;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.CommandLineWrapper;
import com.intellij.util.io.URLUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.breakpoints.XExpressionState;
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
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class DebuggerUtilsImpl extends DebuggerUtilsEx{
  public static final Key<PsiType> PSI_TYPE_KEY = Key.create("PSI_TYPE_KEY");
  private static final Logger LOG = Logger.getInstance(DebuggerUtilsImpl.class);

  @Override
  public PsiExpression substituteThis(PsiExpression expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue, StackFrameContext context)
    throws EvaluateException {
    return DebuggerTreeNodeExpression.substituteThis(expressionWithThis, howToEvaluateThis, howToEvaluateThisValue);
  }

  @Override
  public DebuggerContextImpl getDebuggerContext(DataContext context) {
    return DebuggerAction.getDebuggerContext(context);
  }

  @Override
  @SuppressWarnings("HardCodedStringLiteral")
  public Element writeTextWithImports(TextWithImports text) {
    Element element = new Element("TextWithImports");

    element.setAttribute("text", text.toExternalForm());
    element.setAttribute("type", text.getKind() == CodeFragmentKind.EXPRESSION ? "expression" : "code fragment");
    return element;
  }

  @Override
  @SuppressWarnings("HardCodedStringLiteral")
  public TextWithImports readTextWithImports(Element element) {
    LOG.assertTrue("TextWithImports".equals(element.getName()));

    String text = element.getAttributeValue("text");
    if ("expression".equals(element.getAttributeValue("type"))) {
      return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text);
    } else {
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
  public PsiClass chooseClassDialog(String title, Project project) {
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

  public static <T, E extends Exception> T suppressExceptions(ThrowableComputable<? extends T, ? extends E> supplier, T defaultValue) throws E {
    return suppressExceptions(supplier, defaultValue, true, null);
  }

  public static <T, E extends Exception> T suppressExceptions(ThrowableComputable<? extends T, ? extends E> supplier,
                                                              T defaultValue,
                                                              boolean ignorePCE,
                                                              Class<E> rethrow) throws E {
    try {
      return supplier.compute();
    }
    catch (ProcessCanceledException e) {
      if (!ignorePCE) {
        throw e;
      }
    }
    catch (VMDisconnectedException | ObjectCollectedException e) {throw e;}
    catch (InternalException e) {LOG.info(e);}
    catch (Exception | AssertionError e) {
      if (rethrow != null && rethrow.isInstance(e)) {
        throw e;
      }
      else {
        LOG.error(e);
      }
    }
    return defaultValue;
  }

  public static String getConnectionDisplayName(RemoteConnection connection) {
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
    return supertypes(type).anyMatch(t -> instanceOf(t, superType));
  }

  public static Stream<? extends ReferenceType> supertypes(ReferenceType type) {
    if (type instanceof InterfaceType) {
      return ((InterfaceType)type).superinterfaces().stream();
    } else if (type instanceof ClassType) {
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
}