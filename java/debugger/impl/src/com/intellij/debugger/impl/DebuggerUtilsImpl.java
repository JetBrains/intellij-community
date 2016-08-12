/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.apiAdapters.TransportServiceWrapper;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.Pair;
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
import com.sun.jdi.connect.spi.TransportService;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class DebuggerUtilsImpl extends DebuggerUtilsEx{
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
      Element option = JDOMExternalizerUtil.getOption(root, name);
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
  public static Pair<PsiClass, PsiType> getPsiClassAndType(String className, Project project) {
    PsiClass contextClass;
    PsiType contextType;
    PsiPrimitiveType primitiveType = PsiJavaParserFacadeImpl.getPrimitiveType(className);
    if (primitiveType != null) {
      contextClass = JavaPsiFacade.getInstance(project).findClass(primitiveType.getBoxedTypeName(), GlobalSearchScope.allScope(project));
      contextType = primitiveType;
    }
    else {
      contextClass = findClass(className, project, GlobalSearchScope.allScope(project));
      if (contextClass != null) {
        contextClass = (PsiClass)contextClass.getNavigationElement();
      }
      if (contextClass instanceof PsiCompiledElement) {
        contextClass = (PsiClass)((PsiCompiledElement)contextClass).getMirror();
      }
      contextType = getType(className, project);
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
      TransportServiceWrapper transportService = TransportServiceWrapper.getTransportService(false);
      try {
        return tryShmemConnect(transportService, null);
      }
      catch (IOException e) {
        int tryNum = 0;
        while (true) {
          try {
            return tryShmemConnect(transportService, "javadebug_" + (int)(Math.random() * 1000));
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

  private static String tryShmemConnect(TransportServiceWrapper transportService, String address) throws IOException {
    TransportService.ListenKey listenKey = transportService.startListening(address);
    address = listenKey.address();
    transportService.stopListening(listenKey);
    return address;
  }

  public static boolean isRemote(DebugProcess debugProcess) {
    return Boolean.TRUE.equals(debugProcess.getUserData(BatchEvaluator.REMOTE_SESSION_KEY));
  }

  public interface SupplierThrowing<T, E extends Throwable> {
    T get() throws E;
  }

  public static <T, E extends Exception> T suppressExceptions(SupplierThrowing<T, E> supplier, T defaultValue) throws E {
    return suppressExceptions(supplier, defaultValue, null);
  }

  public static <T, E extends Exception> T suppressExceptions(SupplierThrowing<T, E> supplier,
                                                              T defaultValue,
                                                              Class<E> rethrow) throws E {
    try {
      return supplier.get();
    }
    catch (ProcessCanceledException ignored) {}
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

}