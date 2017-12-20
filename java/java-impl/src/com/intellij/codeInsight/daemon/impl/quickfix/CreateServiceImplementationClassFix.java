/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
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
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Pavel.Dolgov
 */
public class CreateServiceImplementationClassFix implements IntentionAction {
  private String mySuperClassName;
  private String myImplementationClassName;
  private String myModuleName;

  public CreateServiceImplementationClassFix(PsiJavaCodeReferenceElement referenceElement) {
    init(referenceElement);
  }

  private void init(PsiJavaCodeReferenceElement referenceElement) {
    PsiElement parent = referenceElement.getParent();
    while (parent instanceof PsiJavaCodeReferenceElement) {
      referenceElement = (PsiJavaCodeReferenceElement)parent;
      parent = parent.getParent();
    }

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
      if (psiFacade.findClass(mySuperClassName, GlobalSearchScope.projectScope(project)) != null) {
        Module module = ModuleManager.getInstance(project).findModuleByName(myModuleName);
        return module != null && findClassInModule(myImplementationClassName, module) == null;
      }
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
          String lastName = myImplementationClassName.substring(qualifierText.length() + 1);
          createClassInOuter(lastName, outerClass);
          return;
        }
      }

      List<VirtualFile> roots = new ArrayList<>();
      JavaProjectRootsUtil.collectSuitableDestinationSourceRoots(module, roots);
      PsiManager psiManager = file.getManager();
      roots.stream() // todo UI for choosing source root
        .map(psiManager::findDirectory)
        .filter(Objects::nonNull)
        .findAny()
        .ifPresent(this::createClassInRoot);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  private static PsiClass findClassInModule(@NotNull String className, @NotNull Module module) {
    Project project = module.getProject();
    ModulesScope scope = new ModulesScope(Collections.singleton(module), project);
    return JavaPsiFacade.getInstance(project).findClass(className, scope);
  }

  private void createClassInOuter(String lastName, PsiClass outerClass) {
    PsiClass psiClass = WriteAction.compute(() -> createClassInOuterImpl(lastName, outerClass, mySuperClassName));
    positionCursor(psiClass);
  }

  private void createClassInRoot(PsiDirectory rootDir) {
    PsiClass psiClass = WriteAction.compute(() -> createClassInRootImpl(myImplementationClassName, rootDir, mySuperClassName));
    positionCursor(psiClass);
  }

  private static PsiClass createClassInOuterImpl(@NotNull String name, @NotNull PsiClass outerClass, @NotNull String superClassName) {
    Project project = outerClass.getProject();
    PsiClass psiClass = JavaPsiFacade.getElementFactory(project).createClass(name);
    psiClass = (PsiClass)outerClass.addBefore(psiClass, outerClass.getRBrace());
    PsiUtil.setModifierProperty(psiClass, PsiModifier.STATIC, true);
    PsiUtil.setModifierProperty(psiClass, PsiModifier.PUBLIC, true);
    CreateFromUsageUtils.setupSuperClassReference(psiClass, superClassName);
    return psiClass;
  }

  private static PsiClass createClassInRootImpl(@NotNull String classFQN, @NotNull PsiDirectory rootDir, @NotNull String superClassName) {
    PsiDirectory directory = rootDir;
    String lastName;
    StringTokenizer st = new StringTokenizer(classFQN, ".");
    for (lastName = st.nextToken(); st.hasMoreTokens(); lastName = st.nextToken()) {
      PsiDirectory subdirectory = directory.findSubdirectory(lastName);
      if (subdirectory != null) {
        directory = subdirectory;
      }
      else {
        try {
          directory = directory.createSubdirectory(lastName);
        }
        catch (IncorrectOperationException e) {
          CreateFromUsageUtils.scheduleFileOrPackageCreationFailedMessageBox(e, lastName, directory, true);
          return null;
        }
      }
    }

    PsiClass psiClass = JavaDirectoryService.getInstance().createClass(directory, lastName);
    PsiUtil.setModifierProperty(psiClass, PsiModifier.PUBLIC, true);
    CreateFromUsageUtils.setupSuperClassReference(psiClass, superClassName);
    return psiClass;
  }

  private static void positionCursor(PsiClass psiClass) {
    if (psiClass != null) {
      CodeInsightUtil.positionCursor(psiClass.getProject(), psiClass.getContainingFile(), psiClass);
    }
  }
}
