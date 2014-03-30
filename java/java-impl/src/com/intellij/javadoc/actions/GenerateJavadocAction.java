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
package com.intellij.javadoc.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.javadoc.JavadocBundle;
import com.intellij.javadoc.JavadocConfigurable;
import com.intellij.javadoc.JavadocGenerationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public final class GenerateJavadocAction extends BaseAnalysisAction{
  private JavadocConfigurable myConfigurable;

  public GenerateJavadocAction() {
    super(JavadocBundle.message("javadoc.generate.title"), JavadocBundle.message("javadoc.generate.title"));
  }

  @Override
  protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    myConfigurable.apply();
    JavadocGenerationManager.getInstance(project).generateJavadoc(scope);
    dispose();
  }

  @Override
  protected JComponent getAdditionalActionSettings(Project project, final BaseAnalysisActionDialog dialog) {
    myConfigurable = JavadocGenerationManager.getInstance(project).getConfiguration().createConfigurable();
    final JComponent component = myConfigurable.createComponent();
    myConfigurable.reset();
    myConfigurable.getOutputDirField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateAvailability(dialog);
      }
    });
    updateAvailability(dialog);
    return component;
  }

  private void updateAvailability(BaseAnalysisActionDialog dialog) {
    dialog.setOKActionEnabled(!myConfigurable.getOutputDir().isEmpty());
  }

  @Override
  protected void canceled() {
    super.canceled();
    dispose();
  }

  private void dispose() {
    if (myConfigurable != null) {
      myConfigurable.disposeUIResources();
      myConfigurable = null;
    }
  }

  @Override
  protected String getHelpTopic() {
    return "reference.dialogs.generate.javadoc";
  }
}
