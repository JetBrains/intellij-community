// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UpgradeSdkFix implements IntentionAction {
  private final LanguageLevel myLevel;

  public UpgradeSdkFix(@NotNull LanguageLevel targetLevel) {
    myLevel = targetLevel;
  }

  @Override
  public @NotNull String getText() {
    return JavaBundle.message("intention.name.upgrade.jdk.to", myLevel.feature());
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.upgrade.jdk");
  }


  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    Module module = ModuleUtilCore.findModuleForFile(psiFile);
    return module != null && !JavaSdkUtil.isLanguageLevelAcceptable(project, module, myLevel);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    JavaSdkVersion required = JavaSdkVersion.fromLanguageLevel(myLevel);
    SdkPopupFactory
      .newBuilder()
      .withProject(project)
      .withSdkTypeFilter(type -> type instanceof JavaSdkType)
      .withSdkFilter(sdk -> {
        final var version = JavaSdkVersionUtil.getJavaSdkVersion(sdk);
        return version != null && version.isAtLeast(required);
      })
      .updateSdkForFile(psiFile)
      .onSdkSelected(sdk -> ApplicationManager.getApplication()
        .invokeLater(() -> new IncreaseLanguageLevelFix(myLevel)
        .invoke(project, editor, psiFile), ModalityState.nonModal(), project.getDisposed()))
      .buildPopup()
      .showInBestPositionFor(editor);
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile psiFile) {
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}