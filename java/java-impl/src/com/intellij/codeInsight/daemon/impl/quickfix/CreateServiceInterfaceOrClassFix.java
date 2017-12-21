/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author Pavel.Dolgov
 */
public class CreateServiceInterfaceOrClassFix extends CreateServiceClassFixBase {

  private String myInterfaceName;

  public CreateServiceInterfaceOrClassFix(PsiJavaCodeReferenceElement referenceElement) {
    referenceElement = findTopmostReference(referenceElement);
    PsiElement parent = referenceElement.getParent();

    if (parent instanceof PsiUsesStatement && ((PsiUsesStatement)parent).getClassReference() == referenceElement ||
        parent instanceof PsiProvidesStatement && ((PsiProvidesStatement)parent).getInterfaceReference() == referenceElement) {
      if (referenceElement.isQualified()) {
        myInterfaceName = referenceElement.getQualifiedName();
      }
    }
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("create.service.interface.fix.name", myInterfaceName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.service.interface.fix.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (myInterfaceName == null) {
      return false;
    }
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);

    return psiFacade.findClass(myInterfaceName, projectScope) == null &&
           isQualifierInProject(myInterfaceName, project);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    String qualifierText = StringUtil.getPackageName(myInterfaceName);
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);

    PsiClass outerClass = psiFacade.findClass(qualifierText, GlobalSearchScope.projectScope(project));
    if (outerClass != null) {
      createClassInOuter(qualifierText, outerClass);
      return;
    }

    PsiPackage psiPackage;
    do {
      psiPackage = psiFacade.findPackage(qualifierText);
      qualifierText = StringUtil.getPackageName(qualifierText);
    }
    while (psiPackage == null && !StringUtil.isEmpty(qualifierText));

    if (psiPackage != null) {
      ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
      PsiManager psiManager = PsiManager.getInstance(project);
      PsiDirectory[] directories = psiPackage.getDirectories();
      Arrays.stream(directories) // todo UI for choosing source root similar to AddModuleDependencyFix
        .map(directory -> index.getSourceRootForFile(directory.getVirtualFile()))
        .filter(Objects::nonNull)
        .map(psiManager::findDirectory)
        .filter(Objects::nonNull)
        .findAny()
        .ifPresent(this::createClassInRoot);
    }
  }

  private void createClassInOuter(@NotNull String qualifierText, @NotNull PsiClass outerClass) {
    String name = myInterfaceName.substring(qualifierText.length() + 1);
    PsiClass psiClass = WriteAction.compute(() -> createClassInOuterImpl(name, outerClass, null));
    positionCursor(psiClass);
  }

  private void createClassInRoot(@NotNull PsiDirectory rootDir) {
    PsiClass psiClass = WriteAction.compute(() -> createClassInRootImpl(myInterfaceName, rootDir, null));
    positionCursor(psiClass);
  }
}
