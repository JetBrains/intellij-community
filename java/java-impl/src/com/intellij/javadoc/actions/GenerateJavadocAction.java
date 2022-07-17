// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javadoc.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.analysis.dialog.ModelScopeItem;
import com.intellij.java.JavaBundle;
import com.intellij.javadoc.JavadocConfigurable;
import com.intellij.javadoc.JavadocGenerationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.List;

public final class GenerateJavadocAction extends BaseAnalysisAction{
  private JavadocConfigurable myConfigurable;

  public GenerateJavadocAction() {
    super(JavaBundle.messagePointer("javadoc.generate.title"), JavaBundle.messagePointer("javadoc.option.javadoc.title"));
  }

  @Override
  protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
    myConfigurable.apply();
    JavadocGenerationManager.getInstance(project).generateJavadoc(scope);
    dispose();
  }

  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    myConfigurable = new JavadocConfigurable(JavadocGenerationManager.getInstance(project).getConfiguration(), project);
    JComponent component = myConfigurable.createComponent();
    myConfigurable.reset();

    // Output field validation
    final JTextField outputField = myConfigurable.getOutputDirField();
    new ComponentValidator(dialog.getDisposable()).withValidator(() -> {
      return outputField.getText().isBlank()
             ? new ValidationInfo(JavaBundle.message("javadoc.generate.validation.error"), outputField)
             : null;
    }).installOn(outputField);

    outputField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        ComponentValidator.getInstance(outputField).ifPresent(v -> v.revalidate());
      }
    });

    return component;
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
  protected @NotNull String getDialogTitle() {
    return JavaBundle.message("javadoc.generate.title");
  }

  @Override
  protected String getHelpTopic() {
    return "reference.dialogs.generate.javadoc";
  }

  @Override
  public @NotNull BaseAnalysisActionDialog getAnalysisDialog(Project project,
                                                             String title,
                                                             String scopeTitle,
                                                             boolean rememberScope,
                                                             AnalysisUIOptions uiOptions,
                                                             List<ModelScopeItem> items) {
    return new BaseAnalysisActionDialog(title, scopeTitle, project, items, uiOptions, rememberScope) {
      @Override
      protected JComponent getAdditionalActionSettings(Project project) {
        return GenerateJavadocAction.this.getAdditionalActionSettings(project, this);
      }

      @Override
      protected void doOKAction() {
        ComponentValidator.getInstance(myConfigurable.getOutputDirField())
          .ifPresentOrElse(v -> {
                             v.revalidate();
                             if (v.getValidationInfo() == null) super.doOKAction();
                           },
                           () -> { super.doOKAction(); });
      }

      @Override
      protected String getHelpId() {
        return getHelpTopic();
      }

      @Nls
      @Override
      public @NotNull String getOKButtonText() {
        return JavaBundle.message("javadoc.generate.ok");
      }
    };
  }
}