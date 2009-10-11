/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.ui.tree.render.configurables;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.CompletionEditor;
import com.intellij.debugger.ui.tree.render.LabelRenderer;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ClassLabelExpressionConfigurable implements UnnamedConfigurable{
  private final LabelRenderer myRenderer;
  private LabeledComponent<CompletionEditor> myCompletionEditor;
  private final JPanel myPanel;

  public ClassLabelExpressionConfigurable(@NotNull Project project, LabelRenderer renderer) {
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
