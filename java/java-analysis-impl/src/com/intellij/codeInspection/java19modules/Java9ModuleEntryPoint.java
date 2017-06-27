/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class Java9ModuleEntryPoint extends EntryPoint {
  public boolean ADD_EXPORTED_PACKAGES_AND_SERVICES_TO_ENTRIES = true;

  @NotNull
  @Override
  public String getDisplayName() {
    return "<html>Classes exposed with <code>module-info</code></html>";
  }

  @Override
  public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) {
    return isEntryPoint(psiElement);
  }

  @Override
  public boolean isEntryPoint(@NotNull PsiElement psiElement) {
    if (psiElement instanceof PsiClass) {
      return isExported((PsiClass)psiElement);
    }
    if (psiElement instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)psiElement;
      if (isDefaultConstructor(method) || isProviderMethod(method)) {
        return isExported(method.getContainingClass());
      }
    }
    return false;
  }

  private static boolean isDefaultConstructor(PsiMethod method) {
    return method.isConstructor() &&
           method.getParameterList().getParametersCount() == 0 &&
           method.hasModifierProperty(PsiModifier.PUBLIC);
  }

  private static boolean isProviderMethod(PsiMethod method) {
    return "provider".equals(method.getName()) &&
           method.getParameterList().getParametersCount() == 0 &&
           method.hasModifierProperty(PsiModifier.PUBLIC) &&
           method.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isExported(@Nullable PsiClass psiClass) {
    if (psiClass != null) {
      String className = psiClass.getQualifiedName();
      if (className != null) {
        final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(psiClass);
        if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
          PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(psiClass);
          if (javaModule != null) {
            String packageName = getPublicApiPackageName(psiClass);
            if (packageName != null) {
              Set<String> exportedPackageNames = getExportedPackageNames(javaModule);
              if (exportedPackageNames.contains(packageName)) {
                return true;
              }
            }
            Set<String> serviceImplementationNames = getServiceImplementationNames(javaModule);
            return serviceImplementationNames.contains(className);
          }
        }
      }
    }
    return false;
  }

  private static String getPublicApiPackageName(PsiClass psiClass) {
    if (psiClass != null && (psiClass.hasModifierProperty(PsiModifier.PUBLIC) || psiClass.hasModifierProperty(PsiModifier.PROTECTED))) {
      PsiElement parent = psiClass.getParent();
      if (parent instanceof PsiClass) {
        return getPublicApiPackageName((PsiClass)parent);
      }
      if (parent instanceof PsiJavaFile) {
        return ((PsiJavaFile)parent).getPackageName();
      }
    }
    return null;
  }

  private static Set<String> getExportedPackageNames(@NotNull PsiJavaModule javaModule) {
    return CachedValuesManager.getCachedValue(javaModule, () -> {
      Set<String> packages = StreamEx.of(javaModule.getExports().iterator())
        .map(e -> e.getPackageName())
        .nonNull()
        .toCollection(THashSet::new);
      return CachedValueProvider.Result.create(packages, javaModule);
    });
  }

  @NotNull
  private static Set<String> getServiceImplementationNames(@NotNull PsiJavaModule javaModule) {
    return CachedValuesManager.getCachedValue(javaModule, () -> {
      Set<String> classes = StreamEx.of(javaModule.getProvides().iterator())
        .map(PsiProvidesStatement::getImplementationList)
        .nonNull()
        .map(PsiReferenceList::getReferenceElements)
        .flatMap(Arrays::stream)
        .map(PsiJavaCodeReferenceElement::getQualifiedName)
        .nonNull()
        .toCollection(THashSet::new);
      return CachedValueProvider.Result.create(classes, javaModule);
    });
  }

  @Override
  public boolean isSelected() {
    return ADD_EXPORTED_PACKAGES_AND_SERVICES_TO_ENTRIES;
  }

  @Override
  public void setSelected(boolean selected) {
    ADD_EXPORTED_PACKAGES_AND_SERVICES_TO_ENTRIES = selected;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    XmlSerializer.deserializeInto(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    XmlSerializer.serializeInto(this, element, new SkipDefaultValuesSerializationFilters());
  }
}
