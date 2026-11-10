// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.newItemPopup.NewItemPopupUtil;
import com.intellij.ide.ui.newItemPopup.NewItemSimplePopupPanel;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTextField;
import java.util.function.Consumer;

/**
 * Shows the popup of {@link CreateFileAction}.
 * The action class does not refer to the validator and popup classes, so the JVM does not load them when it creates the action.
 */
final class CreateFilePopup {
  private CreateFilePopup() {
  }

  static void show(@NotNull CreateFileAction action,
                   @NotNull Project project,
                   @NotNull PsiDirectory directory,
                   @NotNull Consumer<? super PsiElement[]> elementsConsumer) {
    CreateElementActionBase.MyInputValidator validator = action.new MyValidator(project, directory);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      try {
        elementsConsumer.accept(validator.create("test"));
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    else {
      createLightWeightPopup(validator, elementsConsumer).showCenteredInCurrentWindow(project);
    }
  }

  private static @NotNull JBPopup createLightWeightPopup(@NotNull CreateElementActionBase.MyInputValidator validator,
                                                         @NotNull Consumer<? super PsiElement[]> consumer) {
    NewItemSimplePopupPanel contentPanel = new NewItemSimplePopupPanel();
    JTextField nameField = contentPanel.getTextField();
    JBPopup popup = NewItemPopupUtil.createNewItemPopup(IdeBundle.message("title.new.file"), contentPanel, nameField);
    contentPanel.setApplyAction(event -> {
      String name = nameField.getText();
      if (validator.checkInput(name) && validator.canClose(name)) {
        popup.closeOk(event);
        consumer.accept(validator.getCreatedElements());
      }
      else {
        String errorMessage = validator instanceof InputValidatorEx
                              ? ((InputValidatorEx)validator).getErrorText(name)
                              : LangBundle.message("incorrect.name");
        contentPanel.setError(errorMessage);
      }
    });

    return popup;
  }
}
