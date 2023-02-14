// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @NotNull
  @Override
  public String getText() {
    return JavaBundle.message("intention.name.upgrade.jdk.to", myLevel.toJavaVersion().feature);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.family.name.upgrade.jdk");
  }


  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file);
    return module != null && !JavaSdkUtil.isLanguageLevelAcceptable(project, module, myLevel);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    JavaSdkVersion required = JavaSdkVersion.fromLanguageLevel(myLevel);
    SdkPopupFactory
      .newBuilder()
      .withProject(project)
      .withSdkTypeFilter(type -> type instanceof JavaSdkType)
      .withSdkFilter(sdk -> JavaSdkVersionUtil.getJavaSdkVersion(sdk).isAtLeast(required))
      .updateSdkForFile(file)
      .onSdkSelected(sdk -> ApplicationManager.getApplication()
        .invokeLater(() -> new IncreaseLanguageLevelFix(myLevel)
        .invoke(project, editor, file), ModalityState.NON_MODAL, project.getDisposed()))
      .buildPopup()
      .showInBestPositionFor(editor);
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