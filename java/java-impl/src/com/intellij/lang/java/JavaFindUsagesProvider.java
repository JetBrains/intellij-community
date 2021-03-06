// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.core.JavaPsiBundle;
import com.intellij.find.impl.HelpID;
import com.intellij.ide.TypePresentationService;
import com.intellij.java.JavaBundle;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class JavaFindUsagesProvider implements FindUsagesProvider {
  @Override
  public boolean canFindUsagesFor(@NotNull PsiElement element) {
    if (element instanceof PsiDirectory) {
      PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)element);
      return psiPackage != null && psiPackage.getQualifiedName().length() != 0;
    }

    return element instanceof PsiClass ||
           element instanceof PsiVariable ||
           element instanceof PsiMethod ||
           element instanceof PsiPackage ||
           element instanceof PsiJavaModule ||
           element instanceof PsiLabeledStatement ||
           ThrowSearchUtil.isSearchable(element) ||
           element instanceof PsiMetaOwner && ((PsiMetaOwner)element).getMetaData() != null;
  }

  @Override
  public String getHelpId(@NotNull PsiElement element) {
    if (element instanceof PsiPackage) {
      return HelpID.FIND_PACKAGE_USAGES;
    }
    if (element instanceof PsiClass) {
      return HelpID.FIND_CLASS_USAGES;
    }
    if (element instanceof PsiMethod) {
      return HelpID.FIND_METHOD_USAGES;
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return HelpID.FIND_THROW_USAGES;
    }
    return com.intellij.lang.HelpID.FIND_OTHER_USAGES;
  }

  @Override
  @NotNull
  public String getType(@NotNull PsiElement element) {
    if (element instanceof PsiDirectory) {
      return IndexingBundle.message("terms.directory");
    }
    if (element instanceof PsiFile) {
      return IndexingBundle.message("terms.file");
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return JavaBundle.message("java.terms.exception");
    }
    JavaElementKind kind = JavaElementKind.fromElement(element);
    if (kind != JavaElementKind.UNKNOWN) {
      return kind.subject();
    }

    final String name = TypePresentationService.getService().getTypePresentableName(element.getClass());
    if (name != null) {
      return name;
    }
    return "";
  }

  @Override
  @NotNull
  public String getDescriptiveName(@NotNull final PsiElement element) {
    if (ThrowSearchUtil.isSearchable(element)) {
      return ThrowSearchUtil.getSearchableTypeName(element);
    }
    if (element instanceof PsiDirectory) {
      return getPackageName((PsiDirectory)element, false);
    }
    if (element instanceof PsiPackage) {
      return getPackageName((PsiPackage)element);
    }
    if (element instanceof PsiFile) {
      return ((PsiFile)element).getVirtualFile().getPresentableUrl();
    }
    if (element instanceof PsiLabeledStatement) {
      return ((PsiLabeledStatement)element).getLabelIdentifier().getText();
    }
    if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        String name = ((PsiAnonymousClass)element).getBaseClassReference().getReferenceName();
        return name != null ? JavaPsiBundle.message("java.terms.anonymous.class.base.ref", name) 
                            : JavaElementKind.ANONYMOUS_CLASS.subject();
      }
      else {
        PsiClass aClass = (PsiClass)element;
        String qName = aClass.getQualifiedName();
        @Nullable String value = aClass.getName();
        return qName != null ? qName : value == null ? "" : value;
      }
    }
    if (element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      String formatted = PsiFormatUtil.formatMethod(psiMethod,
                                                    PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                                    PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_RAW_NON_TOP_TYPE);
      PsiClass psiClass = psiMethod.getContainingClass();
      if (psiClass != null) {
        return getContainingClassDescription(psiClass, formatted);
      }

      return formatted;
    }
    if (element instanceof PsiField) {
      PsiField psiField = (PsiField)element;
      String formatted = PsiFormatUtil.formatVariable(psiField, PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
      PsiClass psiClass = psiField.getContainingClass();
      if (psiClass != null) {
        return getContainingClassDescription(psiClass, formatted);
      }

      return formatted;
    }
    if (element instanceof PsiVariable) {
      return PsiFormatUtil.formatVariable((PsiVariable)element, PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY);
    }
    if (element instanceof PsiLiteralExpression) {
      return element.getText();
    }
    if (element instanceof PsiJavaModule) {
      return ((PsiJavaModule)element).getName();
    }

    return "";
  }

  private static @NlsSafe String getContainingClassDescription(PsiClass aClass, String formatted) {
    if (aClass instanceof PsiAnonymousClass) {
      return JavaBundle.message("java.terms.of.anonymous.class", formatted);
    }
    else {
      final String qualifiedName = aClass.getQualifiedName();
      final String className = qualifiedName != null ? qualifiedName : aClass.getName();
      if (aClass.isInterface()) {
        return JavaBundle.message("java.terms.of.interface", formatted, className);
      }
      if (aClass.isEnum()) {
        return JavaBundle.message("java.terms.of.enum", formatted, className);
      }
      if (aClass.isAnnotationType()) {
        return JavaBundle.message("java.terms.of.annotation.type", formatted, className);
      }
      return JavaBundle.message("java.terms.of.class", formatted, className);
    }
  }

  @Override
  @NotNull
  public String getNodeText(@NotNull PsiElement element, boolean useFullName) {
    if (element instanceof PsiDirectory) {
      return getPackageName((PsiDirectory)element, false);
    }

    if (element instanceof PsiPackage) {
      return getPackageName((PsiPackage)element);
    }

    if (element instanceof PsiFile) {
      return useFullName ? ((PsiFile)element).getVirtualFile().getPresentableUrl() : ((PsiFile)element).getName();
    }

    if (element instanceof PsiLabeledStatement) {
      return ((PsiLabeledStatement)element).getLabelIdentifier().getText();
    }

    if (ThrowSearchUtil.isSearchable(element)) {
      return ThrowSearchUtil.getSearchableTypeName(element);
    }

    if (element instanceof PsiClass) {
      String name = ((PsiClass)element).getQualifiedName();
      if (name == null || !useFullName) {
        name = ((PsiClass)element).getName();
      }
      if (name != null) return name;
    }

    if (element instanceof PsiMethod) {
      PsiMethod psiMethod = (PsiMethod)element;
      if (useFullName) {
        int options = PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS;
        String s = PsiFormatUtil.formatMethod((PsiMethod)element, PsiSubstitutor.EMPTY, options, PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME);
        return appendClassName(s, psiMethod.getContainingClass());
      }
      else {
        int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS;
        return PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, options, PsiFormatUtilBase.SHOW_TYPE);
      }
    }

    if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)((PsiParameter)element).getDeclarationScope();
      int varOptions = PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME;
      int methodOptions = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS;
      String s = JavaBundle.message("java.terms.variable.of.method",
                                    PsiFormatUtil.formatVariable((PsiVariable)element, varOptions, PsiSubstitutor.EMPTY),
                                    PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, methodOptions, PsiFormatUtilBase.SHOW_TYPE));
      return appendClassName(s, method.getContainingClass());
    }

    if (element instanceof PsiField) {
      PsiField psiField = (PsiField)element;
      int options = PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME;
      String s = PsiFormatUtil.formatVariable(psiField, options, PsiSubstitutor.EMPTY);
      return appendClassName(s, psiField.getContainingClass());
    }

    if (element instanceof PsiVariable) {
      int options = PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME;
      return PsiFormatUtil.formatVariable((PsiVariable)element, options, PsiSubstitutor.EMPTY);
    }

    return "";
  }

  private static @Nls String appendClassName(@Nls String s, PsiClass psiClass) {
    if (psiClass != null) {
      String qName = psiClass.getQualifiedName();
      if (qName != null) {
        s = JavaBundle.message(psiClass.isInterface() ? "java.terms.of.interface" : "java.terms.of.class", s, qName);
      }
    }
    return s;
  }

  public static @NlsSafe String getPackageName(PsiDirectory directory, boolean includeRootDir) {
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage == null) {
      return directory.getVirtualFile().getPresentableUrl();
    }
    else {
      String packageName = getPackageName(aPackage);
      if (includeRootDir) {
        String rootDir = getRootDirectoryForPackage(directory);
        if (rootDir != null) {
          return JavaBundle.message("usage.target.package.in.directory", packageName, rootDir);
        }
      }
      return packageName;
    }
  }

  public static String getRootDirectoryForPackage(PsiDirectory directory) {
    PsiManager manager = directory.getManager();
    final VirtualFile virtualFile = directory.getVirtualFile();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(manager.getProject()).getFileIndex();
    VirtualFile root = fileIndex.getSourceRootForFile(virtualFile);

    if (root == null) {
      root = fileIndex.getClassRootForFile(virtualFile);
    }
    if (root != null) {
      return root.getPresentableUrl();
    }
    return null;
  }

  public static @NlsSafe String getPackageName(PsiPackage psiPackage) {
    if (psiPackage == null) {
      return null;
    }
    String name = psiPackage.getQualifiedName();
    if (name.length() > 0) {
      return name;
    }
    return getDefaultPackageName();
  }

  public static String getDefaultPackageName() {
    return JavaBundle.message("default.package.presentable.name");
  }
}