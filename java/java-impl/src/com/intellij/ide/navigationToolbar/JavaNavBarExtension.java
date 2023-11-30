// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider;
import com.intellij.ide.structureView.impl.java.JavaLambdaNodeProvider;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.smartTree.NodeProvider;
import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.usageView.UsageViewShortNameLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import java.util.List;

import static com.intellij.psi.util.PsiFormatUtilBase.*;

/**
 * @author anna
 */
public class JavaNavBarExtension extends StructureAwareNavBarModelExtension {
  private static final List<NodeProvider<?>> myNodeProviders = List.of(new JavaLambdaNodeProvider(), new JavaAnonymousClassesNodeProvider());

  @Nullable
  @Override
  public String getPresentableText(Object object) {
    return getPresentableText(object, false);
  }

  @Override
  public String getPresentableText(final Object object, boolean forPopup) {
    if (object instanceof PsiMember member) {
      if (forPopup && object instanceof PsiMethod method) {
        return PsiFormatUtil.formatMethod(method,
                                          PsiSubstitutor.EMPTY,
                                          SHOW_NAME | TYPE_AFTER | SHOW_PARAMETERS,
                                          SHOW_TYPE);
      }
      return ElementDescriptionUtil.getElementDescription(member, UsageViewShortNameLocation.INSTANCE);
    }
    else if (object instanceof PsiPackage psiPackage) {
      final String name = psiPackage.getName();
      return name != null ? name : JavaBundle.message("dependencies.tree.node.default.package.abbreviation");
    }
    else if (object instanceof PsiDirectory directory && JrtFileSystem.isRoot(directory.getVirtualFile())) {
      return JavaBundle.message("jrt.node.short");
    }
    else if (object instanceof PsiLambdaExpression) {
      return JavaBundle.message("lambda.tree.node.presentation");
    }
    return null;
  }

  @Override
  public PsiElement getParent(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage psiPackage) {
      final PsiPackage parentPackage = psiPackage.getParentPackage();
      if (parentPackage != null && !parentPackage.getQualifiedName().isEmpty()) {
        return parentPackage;
      }
    }
    return super.getParent(psiElement);
  }

  @Nullable
  @Override
  public PsiElement adjustElement(@NotNull final PsiElement psiElement) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(psiElement.getProject()).getFileIndex();
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null) {
      final VirtualFile file = containingFile.getVirtualFile();
      if (file != null &&
          (index.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) || index.isInLibrary(file))) {
        if (psiElement instanceof PsiJavaFile psiJavaFile && psiJavaFile.getViewProvider().getBaseLanguage() == JavaLanguage.INSTANCE) {
          final PsiClass[] psiClasses = psiJavaFile.getClasses();
          if (psiClasses.length == 1) {
            return psiClasses[0];
          }
        }
        if (!UISettings.getInstance().getShowMembersInNavigationBar() && psiElement instanceof PsiClass) {
          return psiElement;
        }
      }
      if (!UISettings.getInstance().getShowMembersInNavigationBar()) {
        return containingFile;
      }
    }
    return psiElement;
  }

  @NotNull
  @Override
  protected Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @NotNull
  @Override
  protected List<NodeProvider<?>> getApplicableNodeProviders() {
    return myNodeProviders;
  }

  @Override
  protected boolean acceptParentFromModel(@Nullable PsiElement psiElement) {
    if (psiElement instanceof PsiJavaFile javaFile) {
      return javaFile.getClasses().length > 1;
    }
    return true;
  }
}
