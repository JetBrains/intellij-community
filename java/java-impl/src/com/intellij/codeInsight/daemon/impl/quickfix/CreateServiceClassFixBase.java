/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.scheduleFileOrPackageCreationFailedMessageBox;

/**
 * @author Pavel.Dolgov
 */
public abstract class CreateServiceClassFixBase implements IntentionAction {
  public static final Key<PsiDirectory> SERVICE_ROOT_DIR = Key.create("SERVICE_ROOT_DIR");
  public static final Key<Boolean> SERVICE_IS_CLASS = Key.create("SERVICE_IS_CLASS");
  public static final Key<Boolean> SERVICE_IS_SUBCLASS = Key.create("SERVICE_IS_SUBCLASS");

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  protected static PsiJavaCodeReferenceElement findTopmostReference(@NotNull PsiJavaCodeReferenceElement referenceElement){
    PsiElement parent = referenceElement.getParent();
    while (parent instanceof PsiJavaCodeReferenceElement) {
      referenceElement = (PsiJavaCodeReferenceElement)parent;
      parent = parent.getParent();
    }
    return referenceElement;
  }

  protected static boolean isQualifierInProject(@NotNull String classFQN, @NotNull Project project) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiManager psiManager = PsiManager.getInstance(project);

    String qualifierText = StringUtil.getPackageName(classFQN);
    PsiClass outerClass = psiFacade.findClass(qualifierText, GlobalSearchScope.projectScope(project));
    if (outerClass != null) {
      return psiManager.isInProject(outerClass);
    }
    for (; !StringUtil.isEmpty(qualifierText); qualifierText = StringUtil.getPackageName(qualifierText)) {
      PsiPackage psiPackage = psiFacade.findPackage(qualifierText);
      if (psiPackage != null) {
        return psiManager.isInProject(psiPackage);
      }
    }
    return false;
  }

  @NotNull
  protected static PsiClass createClassInOuterImpl(@NotNull String name, @NotNull PsiClass outerClass, @Nullable String superClassName) {
    Project project = outerClass.getProject();
    PsiClass psiClass = JavaPsiFacade.getElementFactory(project).createClass(name);
    psiClass = (PsiClass)outerClass.addBefore(psiClass, outerClass.getRBrace());
    PsiUtil.setModifierProperty(psiClass, PsiModifier.STATIC, true);
    PsiUtil.setModifierProperty(psiClass, PsiModifier.PUBLIC, true);
    if (superClassName != null) {
      CreateFromUsageUtils.setupSuperClassReference(psiClass, superClassName);
    }
    return psiClass;
  }

  @Nullable
  protected static PsiClass createClassInRoot(@NotNull String classFQN,
                                              boolean isClass,
                                              @NotNull PsiDirectory rootDir,
                                              @Nullable String superClassName) {
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
          scheduleFileOrPackageCreationFailedMessageBox(e, lastName, directory, true);
          return null;
        }
      }
    }

    PsiClass psiClass = createClass(lastName, isClass, directory);
    if (psiClass != null) {
      PsiUtil.setModifierProperty(psiClass, PsiModifier.PUBLIC, true);
      if (superClassName != null) {
        CreateFromUsageUtils.setupSuperClassReference(psiClass, superClassName);
      }
    }
    return psiClass;
  }

  @Nullable
  private static PsiClass createClass(String name, boolean isClass, PsiDirectory directory) {
    try {
      if (isClass) {
        return JavaDirectoryService.getInstance().createClass(directory, name);
      }
      return JavaDirectoryService.getInstance().createInterface(directory, name);
    }
    catch (final IncorrectOperationException e) {
      scheduleFileOrPackageCreationFailedMessageBox(e, name, directory, false);
    }
    return null;
  }

  protected static PsiDirectory[] getModuleRootDirs(Module module) {
    List<VirtualFile> roots = new ArrayList<>();
    JavaProjectRootsUtil.collectSuitableDestinationSourceRoots(module, roots);

    PsiManager psiManager = PsiManager.getInstance(module.getProject());
    return roots.stream()
      .map(psiManager::findDirectory)
      .filter(Objects::nonNull)
      .sorted(Comparator.comparing(psiDir -> psiDir.getVirtualFile().getPresentableUrl()))
      .toArray(PsiDirectory[]::new);
  }

  protected static void positionCursor(@Nullable PsiClass psiClass) {
    if (psiClass != null) {
      CodeInsightUtil.positionCursor(psiClass.getProject(), psiClass.getContainingFile(), psiClass);
    }
  }
}
