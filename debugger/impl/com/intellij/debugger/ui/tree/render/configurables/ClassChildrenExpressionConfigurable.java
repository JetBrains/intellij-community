package com.intellij.debugger.ui.tree.render.configurables;

import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.CompletionEditor;
import com.intellij.debugger.ui.tree.render.ExpressionChildrenRenderer;
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

public class ClassChildrenExpressionConfigurable implements UnnamedConfigurable{
  private final Project myProject;
  private final ExpressionChildrenRenderer myRenderer;

  private JPanel myPanel;
  private LabeledComponent<JPanel> myChildrenPanel;
  private LabeledComponent<JPanel> myExpandablePanel;

  private final CompletionEditor myChildrenEditor;
  private final CompletionEditor myExpandableEditor;

  public ClassChildrenExpressionConfigurable(Project project, ExpressionChildrenRenderer renderer) {
    myProject = project;
    myRenderer = renderer;

    PsiClass psiClass = DebuggerUtils.findClass(myRenderer.getClassName(), myProject, GlobalSearchScope.allScope(myProject));
    myChildrenEditor   = ((DebuggerUtilsEx)DebuggerUtils.getInstance()).createEditor(project, psiClass, "ClassChildrenExpression");
    myExpandableEditor = ((DebuggerUtilsEx)DebuggerUtils.getInstance()).createEditor(project, psiClass, "ClassChildrenExpression");

    myChildrenPanel.getComponent().setLayout(new BorderLayout());
    myChildrenPanel.getComponent().add(myChildrenEditor);

    myExpandablePanel.getComponent().setLayout(new BorderLayout());
    myExpandablePanel.getComponent().add(myExpandableEditor);    
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return !myRenderer.getChildrenExpression().equals(myChildrenEditor.getText()) ||
           !myRenderer.getChildrenExpandable().equals(myExpandableEditor.getText());
  }

  public void apply() throws ConfigurationException {
    myRenderer.setChildrenExpression(myChildrenEditor.getText());
    myRenderer.setChildrenExpandable(myExpandableEditor.getText());
  }

  public void reset() {
    myChildrenEditor.setText(myRenderer.getChildrenExpression());
    myExpandableEditor.setText(myRenderer.getChildrenExpandable());
  }

  public void disposeUIResources() {
    myChildrenEditor.dispose();
    myExpandableEditor.dispose();
  }
}
