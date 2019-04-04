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
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.visibility.EntryPointWithVisibilityLevel;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class Java9ModuleEntryPoint extends EntryPointWithVisibilityLevel {
  public static final String ID = "moduleInfo";
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
      return isServiceOrExported((PsiClass)psiElement);
    }
    if (psiElement instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)psiElement;
      if (isDefaultConstructor(method) || isProviderMethod(method)) {
        return isServiceOrExported(method.getContainingClass());
      }
    }
    return false;
  }

  @Override
  public int getMinVisibilityLevel(PsiMember member) {
    if (member instanceof PsiClass) {
      final PsiJavaModule javaModule = getJavaModule(member);
      if (javaModule != null &&
          !isServiceClass((PsiClass)member, javaModule) &&
          isInExportedPackage((PsiClass)member, javaModule)) {
        return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
      }
    }
    return ACCESS_LEVEL_INVALID;
  }

  @Override
  public String getTitle() {
    return "Suggest package-private visibility level for classes in exported packages (Java 9+)";
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean keepVisibilityLevel(boolean entryPointEnabled, @NotNull RefJavaElement refJavaElement) {
    if (refJavaElement instanceof RefClass) {
      RefClass refClass = (RefClass)refJavaElement;
      RefModule refModule = refClass.getModule();
      if (refModule != null) {
        RefJavaModule refJavaModule = RefJavaModule.JAVA_MODULE.get(refModule);
        if (refJavaModule != null) {
          return isServiceClass(refClass, refJavaModule) ||
                 !entryPointEnabled && isInExportedPackage(refClass, refJavaModule);
        }
      }
    }
    return false;
  }

  private static boolean isInExportedPackage(@Nullable RefClass refClass, @NotNull RefJavaModule refJavaModule) {
    RefEntity refOwner = refClass;
    while (refOwner instanceof RefClass) {
      String modifier = ((RefClass)refOwner).getAccessModifier();
      refOwner = PsiModifier.PUBLIC.equals(modifier) || PsiModifier.PROTECTED.equals(modifier) ? refOwner.getOwner() : null;
    }
    if (refOwner instanceof RefPackage) {
      Map<String, List<String>> exportedPackageNames = refJavaModule.getExportedPackageNames();
      if (exportedPackageNames.containsKey(refOwner.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }

  private static boolean isServiceClass(@Nullable RefClass refClass, @NotNull RefJavaModule refJavaModule) {
    return refJavaModule.getServiceInterfaces().contains(refClass) ||
           refJavaModule.getServiceImplementations().contains(refClass) ||
           refJavaModule.getUsedServices().contains(refClass);
  }


  private static boolean isDefaultConstructor(@NotNull PsiMethod method) {
    return method.isConstructor() &&
           method.getParameterList().isEmpty() &&
           method.hasModifierProperty(PsiModifier.PUBLIC);
  }

  private static boolean isProviderMethod(@NotNull PsiMethod method) {
    return "provider".equals(method.getName()) &&
           method.getParameterList().isEmpty() &&
           method.hasModifierProperty(PsiModifier.PUBLIC) &&
           method.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isServiceOrExported(@Nullable PsiClass psiClass) {
    PsiJavaModule javaModule = getJavaModule(psiClass);
    return javaModule != null && (isServiceClass(psiClass, javaModule) || isInExportedPackage(psiClass, javaModule));
  }

  private static boolean isInExportedPackage(@NotNull PsiClass psiClass, @NotNull PsiJavaModule javaModule) {
    String packageName = getPublicApiPackageName(psiClass);
    if (packageName != null) {
      Set<String> exportedPackageNames = getExportedPackageNames(javaModule);
      if (exportedPackageNames.contains(packageName)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isServiceClass(@NotNull PsiClass psiClass, @NotNull PsiJavaModule javaModule) {
    Set<String> serviceClassNames = CachedValuesManager.getCachedValue(
      javaModule, () -> CachedValueProvider.Result.create(collectServiceClassNames(javaModule), javaModule));

    return serviceClassNames.contains(psiClass.getQualifiedName());
  }

  @Contract("null -> null")
  @Nullable
  private static PsiJavaModule getJavaModule(@Nullable PsiElement element) {
    if (element != null) {
      final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(element);
      if (languageLevel.isAtLeast(LanguageLevel.JDK_1_9)) {
        return JavaModuleGraphUtil.findDescriptorByElement(element);
      }
    }
    return null;
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
  private static Set<String> collectServiceClassNames(@NotNull PsiJavaModule javaModule) {
    Set<String> classes = StreamEx.of(javaModule.getProvides().spliterator())
      .map(PsiProvidesStatement::getImplementationList)
      .nonNull()
      .map(PsiReferenceList::getReferenceElements)
      .flatMap(Arrays::stream)
      .map(PsiJavaCodeReferenceElement::getQualifiedName)
      .nonNull()
      .toCollection(THashSet::new);

    Set<String> usages = StreamEx.of(javaModule.getUses().iterator())
      .map(PsiUsesStatement::getClassReference)
      .nonNull()
      .map(PsiJavaCodeReferenceElement::getQualifiedName)
      .nonNull()
      .toCollection(THashSet::new);

    classes.addAll(usages);
    return classes;
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
