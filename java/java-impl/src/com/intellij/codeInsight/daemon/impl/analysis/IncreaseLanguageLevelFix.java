// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.pom.java.AcceptedLanguageLevelsSettings;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class IncreaseLanguageLevelFix implements IntentionAction, LocalQuickFix {
  private final LanguageLevel myLevel;

  public IncreaseLanguageLevelFix(@NotNull LanguageLevel targetLevel) {
    myLevel = targetLevel;
  }

  @NotNull
  @Override
  public String getText() {
    return CodeInsightBundle.message("set.language.level.to.0", myLevel.getPresentableText());
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
    return CodeInsightBundle.message("set.language.level");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    invoke(project, null, element.getContainingFile());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file);
    return module != null &&
           JavaSdkUtil.isLanguageLevelAcceptable(project, module, myLevel) &&
           AcceptedLanguageLevelsSettings.isLanguageLevelAccepted(myLevel);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module != null) {
      JavaProjectModelModificationService.getInstance(project).changeLanguageLevel(module, myLevel);
    }
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}