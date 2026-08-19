// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateClassKind;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateServiceClassFixBase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class CreateClassInPackageInModuleFix implements IntentionAction {
  public static final Key<Boolean> IS_INTERFACE = Key.create("CREATE_CLASS_IN_PACKAGE_IS_INTERFACE");
  public static final Key<PsiDirectory> ROOT_DIR = Key.create("CREATE_CLASS_IN_PACKAGE_ROOT_DIR");
  public static final Key<String> NAME = Key.create("CREATE_CLASS_IN_PACKAGE_NAME");

  private final String myModuleName;
  private final String myPackageName;

  private CreateClassInPackageInModuleFix(String moduleName, String packageName) {
    myModuleName = moduleName;
    myPackageName = packageName;
  }

  @Override
  public @Nls @NotNull String getText() {
    return JavaBundle.message("intention.text.create.a.class.in.0", myPackageName);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.create.a.class.in.package");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return ModuleManager.getInstance(project).findModuleByName(myModuleName) != null;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return new IntentionPreviewInfo.Html(JavaBundle.message("intention.text.create.a.class.in.package.preview", StringUtil.escapeXmlEntities(myPackageName)));
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Boolean isInterface = IS_INTERFACE.get(psiFile);
      PsiDirectory rootDir = ROOT_DIR.get(psiFile);
      String name = NAME.get(psiFile);
      if (isInterface != null && rootDir != null && name != null) {
        WriteAction.run(() -> createClassInPackage(isInterface ? CreateClassKind.INTERFACE : CreateClassKind.CLASS, rootDir, name, psiFile));
      }
      return;
    }
    Module module = ModuleManager.getInstance(project).findModuleByName(myModuleName);
    if (module != null) {
      List<VirtualFile> roots = new ArrayList<>();
      JavaProjectRootsUtil.collectSuitableDestinationSourceRoots(module, roots);
      PsiManager psiManager = PsiManager.getInstance(project);
      PsiDirectory[] rootDirs = roots.stream()
        .sorted(Comparator.comparing(VirtualFile::getPresentableUrl))
        .map(psiManager::findDirectory)
        .filter(Objects::nonNull)
        .toArray(PsiDirectory[]::new);

      if (rootDirs.length != 0) {
        CreateClassInPackageDialog dialog = new CreateClassInPackageDialog(project, rootDirs, myPackageName);
        if (dialog.showAndGet()) {
          CreateClassKind kind = dialog.getKind();
          PsiDirectory rootDir = dialog.getRootDir();
          String name = dialog.getName();
          if (rootDir != null) {
            PsiClass psiClass = WriteAction.compute(() -> createClassInPackage(kind, rootDir, name, psiFile));
            CreateServiceClassFixBase.positionCursor(psiClass);
          }
        }
      }
    }
  }

  private @Nullable PsiClass createClassInPackage(@NotNull CreateClassKind kind,
                                                  @NotNull PsiDirectory rootDir,
                                                  @NotNull String name,
                                                  @NotNull PsiElement contextElement) {
    PsiDirectory psiPackageDir = CreateServiceClassFixBase.getOrCreatePackageDirInRoot(myPackageName, rootDir);
    if (psiPackageDir != null) {
      return CreateFromUsageUtils.createClass(kind, psiPackageDir, name, contextElement.getManager(), contextElement, null, null);
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static @Nullable IntentionAction createFix(@NotNull Module module, @Nullable String packageName) {
    return StringUtil.isEmpty(packageName) ? null : new CreateClassInPackageInModuleFix(module.getName(), packageName);
  }
}
