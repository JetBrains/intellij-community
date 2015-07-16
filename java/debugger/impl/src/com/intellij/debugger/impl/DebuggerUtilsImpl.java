/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.net.NetUtils;
import com.sun.jdi.Value;
import com.sun.jdi.connect.spi.TransportService;
import org.jdom.Element;

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
    LOG.assertTrue(value.getKind() == CodeFragmentKind.EXPRESSION);
    JDOMExternalizerUtil.writeField(root, name, value.toExternalForm());
  }

  @Override
  public TextWithImports readTextWithImports(Element root, String name) {
    String s = JDOMExternalizerUtil.readField(root, name);
    if(s == null) return null;
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, s);
  }

  @Override
  public TextWithImports createExpressionWithImports(String expression) {
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression);
  }

  @Override
  public PsiElement getContextElement(StackFrameContext context) {
    return PositionUtil.getContextElement(context);
  }

  @Override
  public PsiClass chooseClassDialog(String title, Project project) {
    TreeClassChooser dialog = TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser(title);
    dialog.showDialog();
    return dialog.getSelected();
  }

  @Override
  public String findAvailableDebugAddress(final boolean useSockets) throws ExecutionException {
    final TransportServiceWrapper transportService = TransportServiceWrapper.getTransportService(useSockets);
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

    try {
      TransportService.ListenKey listenKey = transportService.startListening();
      final String address = listenKey.address();
      transportService.stopListening(listenKey);
      return address;
    }
    catch (IOException e) {
      int tryNum = 0;
      while (true) {
        try {
          TransportService.ListenKey listenKey = transportService.startListening("javadebug_" + (int)(Math.random()*1000));
          final String address = listenKey.address();
          transportService.stopListening(listenKey);
          return address;
        }
        catch (Exception ex) {
          if (tryNum++ > 10) {
            throw new ExecutionException(DebugProcessImpl.processError(ex));
          }
        }
      }
    }
  }

  public static boolean isRemote(DebugProcess debugProcess) {
    return Boolean.TRUE.equals(debugProcess.getUserData(BatchEvaluator.REMOTE_SESSION_KEY));
  }
}