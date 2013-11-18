/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.java;

import com.intellij.find.impl.HelpID;
import com.intellij.ide.TypePresentationService;
import com.intellij.lang.LangBundle;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.search.ThrowSearchUtil;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class JavaFindUsagesProvider implements FindUsagesProvider {
  public static final String DEFAULT_PACKAGE_NAME = UsageViewBundle.message("default.package.presentable.name");

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
      return LangBundle.message("terms.directory");
    }
    if (element instanceof PsiFile) {
      return LangBundle.message("terms.file");
    }
    if (ThrowSearchUtil.isSearchable(element)) {
      return LangBundle.message("java.terms.exception");
    }
    if (element instanceof PsiPackage) {
      return LangBundle.message("java.terms.package");
    }
    if (element instanceof PsiLabeledStatement) {
      return LangBundle.message("java.terms.label");
    }
    if (element instanceof PsiClass) {
      if (((PsiClass)element).isAnnotationType()) {
        return LangBundle.message("java.terms.annotation.interface");
      }
      if (((PsiClass)element).isEnum()) {
        return LangBundle.message("java.terms.enum");
      }
      if (((PsiClass)element).isInterface()) {
        return LangBundle.message("java.terms.interface");
      }
      if (element instanceof PsiTypeParameter) {
        return LangBundle.message("java.terms.type.parameter");
      }
      return LangBundle.message("java.terms.class");
    }
    if (element instanceof PsiField) {
      return LangBundle.message("java.terms.field");
    }
    if (element instanceof PsiParameter) {
      return LangBundle.message("java.terms.parameter");
    }
    if (element instanceof PsiLocalVariable) {
      return LangBundle.message("java.terms.variable");
    }
    if (element instanceof PsiMethod) {
      final PsiMethod psiMethod = (PsiMethod)element;
      final boolean isConstructor = psiMethod.isConstructor();
      if (isConstructor) {
        return LangBundle.message("java.terms.constructor");
      }
      return LangBundle.message("java.terms.method");
    }
    if (element instanceof PsiExpression) {
      return LangBundle.message("java.terms.expression");
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
        return LangBundle.message("java.terms.anonymous.class");
      }
      else {
        final PsiClass aClass = (PsiClass)element;
        String qName =  aClass.getQualifiedName();
        return qName == null ? aClass.getName() : qName;
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

    return "";
  }

  private static String getContainingClassDescription(PsiClass aClass, String formatted) {
    if (aClass instanceof PsiAnonymousClass) {
      return LangBundle.message("java.terms.of.anonymous.class", formatted);
    }
    else {
      final String qualifiedName = aClass.getQualifiedName();
      final String className = qualifiedName != null ? qualifiedName : aClass.getName();
      if (aClass.isInterface()) {
        return LangBundle.message("java.terms.of.interface", formatted, className);
      }
      if (aClass.isEnum()) {
        return LangBundle.message("java.terms.of.enum", formatted, className);
      }
      if (aClass.isAnnotationType()) {
        return LangBundle.message("java.terms.of.annotation.type", formatted, className);
      }
      return LangBundle.message("java.terms.of.class", formatted, className);
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
        String s = PsiFormatUtil.formatMethod((PsiMethod)element,
                                              PsiSubstitutor.EMPTY, PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_TYPE |
                                                                    PsiFormatUtilBase.SHOW_NAME |
                                                                    PsiFormatUtilBase.SHOW_PARAMETERS,
                                              PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME);
        final PsiClass psiClass = psiMethod.getContainingClass();
        if (psiClass != null) {
          final String qName = psiClass.getQualifiedName();
          if (qName != null) {
            if (psiClass.isInterface()) {
              s = LangBundle.message("java.terms.of.interface", s, qName);
            }
            else {
              s = LangBundle.message("java.terms.of.class", s, qName);
            }
          }
        }
        return s;
      }
      else {
        return PsiFormatUtil.formatMethod(psiMethod,
                                          PsiSubstitutor.EMPTY,
                                          PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                          PsiFormatUtilBase.SHOW_TYPE);
      }
    }
    if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)((PsiParameter)element).getDeclarationScope();
      String s = LangBundle.message("java.terms.variable.of.method",
                                    PsiFormatUtil.formatVariable((PsiVariable)element,
                                                                 PsiFormatUtilBase.TYPE_AFTER |
                                                                 PsiFormatUtilBase.SHOW_TYPE |
                                                                 PsiFormatUtilBase.SHOW_NAME,
                                                                 PsiSubstitutor.EMPTY),
                                    PsiFormatUtil.formatMethod(method,
                                                               PsiSubstitutor.EMPTY,
                                                               PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                                               PsiFormatUtilBase.SHOW_TYPE));

      final PsiClass psiClass = method.getContainingClass();
      if (psiClass != null && psiClass.getQualifiedName() != null) {
        if (psiClass.isInterface()) {
          s = LangBundle.message("java.terms.of.interface", s, psiClass.getQualifiedName());
        }
        else {
          s = LangBundle.message("java.terms.of.class", s, psiClass.getQualifiedName());
        }
      }
      return s;
    }
    if (element instanceof PsiField) {
      PsiField psiField = (PsiField)element;
      String s = PsiFormatUtil.formatVariable(psiField,
                                              PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME,
                                              PsiSubstitutor.EMPTY);
      PsiClass psiClass = psiField.getContainingClass();
      if (psiClass != null) {
        String qName = psiClass.getQualifiedName();
        if (qName != null) {
          if (psiClass.isInterface()) {
            s = LangBundle.message("java.terms.of.interface", s, qName);
          }
          else {
            s = LangBundle.message("java.terms.of.class", s, qName);
          }
        }
      }
      return s;
    }
    if (element instanceof PsiVariable) {
      return PsiFormatUtil.formatVariable((PsiVariable)element,
                                          PsiFormatUtilBase.TYPE_AFTER | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME,
                                          PsiSubstitutor.EMPTY);
    }

    return "";
  }

  public static String getPackageName(PsiDirectory directory, boolean includeRootDir) {
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (aPackage == null) {
      return directory.getVirtualFile().getPresentableUrl();
    }
    else {
      String packageName = getPackageName(aPackage);
      if (includeRootDir) {
        String rootDir = getRootDirectoryForPackage(directory);
        if (rootDir != null) {
          return UsageViewBundle.message("usage.target.package.in.directory", packageName, rootDir);
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

  public static String getPackageName(PsiPackage psiPackage) {
    if (psiPackage == null) {
      return null;
    }
    String name = psiPackage.getQualifiedName();
    if (name.length() > 0) {
      return name;
    }
    return DEFAULT_PACKAGE_NAME;
  }

  @Override
  public WordsScanner getWordsScanner() {
    return null;
  }
}
