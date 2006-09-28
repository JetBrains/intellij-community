package com.intellij.debugger.ui.tree.render.configurables;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.CompletionEditor;
import com.intellij.debugger.ui.tree.render.LabelRenderer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;

import javax.swing.*;
import java.awt.*;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ClassLabelExpressionConfigurable implements UnnamedConfigurable{
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.tree.render.configurables.ClassLabelExpressionConfigurable");
  private final LabelRenderer myRenderer;
  private LabeledComponent<CompletionEditor> myCompletionEditor;
  private final JPanel myPanel;

  public ClassLabelExpressionConfigurable(Project project, LabelRenderer renderer) {
    LOG.assertTrue(project != null);
    myRenderer = renderer;

    myCompletionEditor = new LabeledComponent<CompletionEditor>();
    final PsiClass psiClass = DebuggerUtils.findClass(myRenderer.getClassName(), project, GlobalSearchScope.allScope(project));
    myCompletionEditor.setComponent(((DebuggerUtilsEx)DebuggerUtils.getInstance()).createEditor(project, psiClass, "ClassLabelExpression"));
    myCompletionEditor.setText(DebuggerBundle.message("label.class.label.expression.configurable.node.label"));

    myPanel = new JPanel(new BorderLayout());
    myPanel.add(myCompletionEditor, BorderLayout.NORTH);
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return !myRenderer.getLabelExpression().equals(myCompletionEditor.getComponent().getText());
  }

  public void apply() throws ConfigurationException {
    myRenderer.setLabelExpression(myCompletionEditor.getComponent().getText());
  }

  public void reset() {
    myCompletionEditor.getComponent().setText(myRenderer.getLabelExpression());
  }

  public void disposeUIResources() {
    if (myCompletionEditor != null) {
      myCompletionEditor.getComponent().dispose();
      myCompletionEditor = null;
    }
  }
}
