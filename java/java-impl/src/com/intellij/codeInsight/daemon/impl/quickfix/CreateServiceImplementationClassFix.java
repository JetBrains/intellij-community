/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.impl.scopes.ModulesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Pavel.Dolgov
 */
public class CreateServiceImplementationClassFix extends CreateServiceClassFixBase {
  private String mySuperClassName;
  private String myImplementationClassName;
  private String myModuleName;

  public CreateServiceImplementationClassFix(PsiJavaCodeReferenceElement referenceElement) {
    init(referenceElement);
  }

  private void init(@NotNull PsiJavaCodeReferenceElement referenceElement) {
    referenceElement = findTopmostReference(referenceElement);
    PsiElement parent = referenceElement.getParent();

    if (parent != null && referenceElement.isQualified()) {
      PsiProvidesStatement providesStatement = ObjectUtils.tryCast(parent.getParent(), PsiProvidesStatement.class);
      if (providesStatement != null && providesStatement.getImplementationList() == parent) {
        myImplementationClassName = referenceElement.getQualifiedName();
        if (myImplementationClassName != null) {
          mySuperClassName = getSuperClassName(providesStatement);
          if (mySuperClassName != null) {
            Module module = ModuleUtilCore.findModuleForFile(referenceElement.getContainingFile());
            myModuleName = module != null ? module.getName() : null;
          }
        }
      }
    }
  }

  @Nullable
  private static String getSuperClassName(@NotNull PsiProvidesStatement providesStatement) {
    PsiJavaCodeReferenceElement interfaceReference = providesStatement.getInterfaceReference();
    if (interfaceReference != null) {
      if (interfaceReference.isQualified()) {
        return interfaceReference.getQualifiedName();
      }
      PsiClass superClass = ObjectUtils.tryCast(interfaceReference.resolve(), PsiClass.class);
      if (superClass != null) {
        return superClass.getQualifiedName();
      }
    }
    return null;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("create.service.implementation.fix.name", myImplementationClassName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("create.service.implementation.fix.family.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (mySuperClassName != null && myImplementationClassName != null && myModuleName != null) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
      return psiFacade.findClass(myImplementationClassName, projectScope) == null &&
             psiFacade.findClass(mySuperClassName, projectScope) != null &&
             isQualifierInProject(myImplementationClassName, project);
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    Module module = ModuleManager.getInstance(project).findModuleByName(myModuleName);
    if (module != null) {
      String qualifierText = StringUtil.getPackageName(myImplementationClassName);
      if (!StringUtil.isEmpty(qualifierText)) {
        PsiClass outerClass = findClassInModule(qualifierText, module);
        if (outerClass != null) {
          createClassInOuter(qualifierText, outerClass);
          return;
        }
      }

      List<VirtualFile> roots = new ArrayList<>();
      JavaProjectRootsUtil.collectSuitableDestinationSourceRoots(module, roots);
      PsiManager psiManager = file.getManager();
      roots.stream() // todo UI for choosing source root similar to AddModuleDependencyFix
        .map(psiManager::findDirectory)
        .filter(Objects::nonNull)
        .findAny()
        .ifPresent(this::createClassInRoot);
    }
  }

  @Nullable
  private static PsiClass findClassInModule(@NotNull String className, @NotNull Module module) {
    Project project = module.getProject();
    ModulesScope scope = new ModulesScope(Collections.singleton(module), project);
    return JavaPsiFacade.getInstance(project).findClass(className, scope);
  }

  private void createClassInOuter(String qualifierText, PsiClass outerClass) {
    String name = myImplementationClassName.substring(qualifierText.length() + 1);
    PsiClass psiClass = WriteAction.compute(() -> createClassInOuterImpl(name, outerClass, mySuperClassName));
    positionCursor(psiClass);
  }

  private void createClassInRoot(PsiDirectory rootDir) {
    PsiClass psiClass = WriteAction.compute(() -> createClassInRootImpl(myImplementationClassName, rootDir, mySuperClassName));
    positionCursor(psiClass);
  }
}
