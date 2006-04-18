package com.intellij.debugger.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.apiAdapters.TransportServiceWrapper;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.StackFrameContext;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.ui.CompletionEditor;
import com.intellij.debugger.ui.DebuggerExpressionComboBox;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeExpression;
import com.intellij.debugger.ui.tree.DebuggerTreeNode;
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
import org.jdom.Element;

import java.io.IOException;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class DebuggerUtilsImpl extends DebuggerUtilsEx{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerUtilsImpl");

  public String getComponentName() {
    return "DebuggerUtils";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public PsiExpression substituteThis(PsiExpression expressionWithThis, PsiExpression howToEvaluateThis, Value howToEvaluateThisValue, StackFrameContext context)
    throws EvaluateException {
    return DebuggerTreeNodeExpression.substituteThis(expressionWithThis, howToEvaluateThis, howToEvaluateThisValue);
  }

  public EvaluatorBuilder getEvaluatorBuilder() {
    return EvaluatorBuilderImpl.getInstance();
  }

  public DebuggerTreeNode getSelectedNode(DataContext context) {
    return DebuggerAction.getSelectedNode(context);
  }

  public DebuggerContextImpl getDebuggerContext(DataContext context) {
    return DebuggerAction.getDebuggerContext(context);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public Element writeTextWithImports(TextWithImports text) {
    Element element = new Element("TextWithImports");

    element.setAttribute("text", text.toString());
    element.setAttribute("type", text.getKind() == CodeFragmentKind.EXPRESSION ? "expression" : "code fragment");
    return element;
  }

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

  public void writeTextWithImports(Element root, String name, TextWithImports value) {
    LOG.assertTrue(value.getKind() == CodeFragmentKind.EXPRESSION);
    JDOMExternalizerUtil.writeField(root, name, value.toString());
  }

  public TextWithImports readTextWithImports(Element root, String name) {
    String s = JDOMExternalizerUtil.readField(root, name);
    if(s == null) return null;
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, s);
  }

  public TextWithImports createExpressionWithImports(String expression) {
    return new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, expression);
  }

  public PsiElement getContextElement(StackFrameContext context) {
    return PositionUtil.getContextElement(context);
  }

  public PsiClass chooseClassDialog(String title, Project project) {
    TreeClassChooser dialog = TreeClassChooserFactory.getInstance(project).createAllProjectScopeChooser(title);
    dialog.showDialog();
    return dialog.getSelectedClass();
  }

  public CompletionEditor createEditor(Project project, PsiElement context, String recentsId) {
    return new DebuggerExpressionComboBox(project, context, recentsId);
  }

  public String findAvailableDebugAddress(final boolean useSockets) throws ExecutionException {
    final TransportServiceWrapper transportService = TransportServiceWrapper.getTransportService(useSockets);

    if(useSockets) {
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
      String address  = transportService.startListening();
      transportService.stopListening(address);
      return address;
    }
    catch (IOException e) {
      throw new ExecutionException(DebugProcessImpl.processError(e));
    }
  }
}
