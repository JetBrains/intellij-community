// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IncreaseLanguageLevelFix implements IntentionAction, LocalQuickFix, HighPriorityAction {
  private final LanguageLevel myLevel;

  public IncreaseLanguageLevelFix(@NotNull LanguageLevel targetLevel) {
    myLevel = targetLevel;
  }

  @NotNull
  @Override
  public String getText() {
    return JavaBundle.message("set.language.level.to.0", myLevel.getPresentableText());
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return getText();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("set.language.level");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    invoke(project, null, element.getContainingFile());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file);
    return module != null && JavaSdkUtil.isLanguageLevelAcceptable(project, module, myLevel);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!AcceptedLanguageLevelsSettings.isLanguageLevelAccepted(myLevel)) {
      JComponent component = editor == null ? null : editor.getComponent();
      if (AcceptedLanguageLevelsSettings.checkAccepted(component, myLevel) == null) {
        return;
      }
    }
    WriteCommandAction.runWriteCommandAction(project, getText(), null, () -> {
      Module module = ModuleUtilCore.findModuleForPsiElement(file);
      if (module != null) {
        LanguageLevel oldLevel = LanguageLevelUtil.getCustomLanguageLevel(module);
        JavaProjectModelModificationService.getInstance(project).changeLanguageLevel(module, myLevel);
        VirtualFile vFile = file.getVirtualFile();
        if (oldLevel != null) {
          UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(vFile) {
            @Override
            public void undo() {
              JavaProjectModelModificationService.getInstance(project).changeLanguageLevel(module, oldLevel);
            }

            @Override
            public void redo() {
              JavaProjectModelModificationService.getInstance(project).changeLanguageLevel(module, myLevel);
            }
          });
        }
      }
    });
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}