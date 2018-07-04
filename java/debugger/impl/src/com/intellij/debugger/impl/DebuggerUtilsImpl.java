/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.ExecutionException;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.net.NetUtils;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.breakpoints.XExpressionState;
import com.sun.jdi.InternalException;
import com.sun.jdi.ObjectCollectedException;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public class DebuggerUtilsImpl extends DebuggerUtilsEx{
  public static final Key<PsiType> PSI_TYPE_KEY = Key.create("PSI_TYPE_KEY");
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerUtilsImpl");

  @Override
  public PsiExpression substituteThis(PsiExpression expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue, StackFrameContext context)
    throws EvaluateException {
    return DebuggerTreeNodeExpression.substituteThis(expressionWithThis, howToEvaluateThis, howToEvaluateThisValue);
  }

  @Override
  public EvaluatorBuilder getEvaluatorBuilder() {
    return EvaluatorBuilderImpl.getInstance();
  }

  @Override
  public DebuggerTreeNode getSelectedNode(DataContext context) {
    return DebuggerAction.getSelectedNode(context);
  }

  @Override
  public DebuggerContextImpl getDebuggerContext(DataContext context) {
    return DebuggerAction.getDebuggerContext(context);
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element writeTextWithImports(TextWithImports text) {
    Element element = new Element("TextWithImports");

    element.setAttribute("text", text.toExternalForm());
    element.setAttribute("type", text.getKind() == CodeFragmentKind.EXPRESSION ? "expression" : "code fragment");
    return element;
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
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
        XmlSerializer.serializeInto(new XExpressionState(expression), element, new SkipDefaultValuesSerializationFilters());
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
        XmlSerializer.deserializeInto(state, option);
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

  public static <T, E extends Exception> T suppressExceptions(ThrowableComputable<T, E> supplier, T defaultValue) throws E {
    return suppressExceptions(supplier, defaultValue, true, null);
  }

  public static <T, E extends Exception> T suppressExceptions(ThrowableComputable<T, E> supplier,
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

  public static <T> T runInReadActionWithWriteActionPriorityWithRetries(@NotNull Computable<T> action) {
    if (ApplicationManagerEx.getApplicationEx().holdsReadLock()) {
      return action.compute();
    }
    Ref<T> res = Ref.create();
    while (true) {
      if (ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> res.set(action.compute()))) {
        return res.get();
      }
      ProgressIndicatorUtils.yieldToPendingWriteActions();
    }
  }
}