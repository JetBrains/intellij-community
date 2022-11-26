// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.visibility.EntryPointWithVisibilityLevel;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiUtil;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Pavel.Dolgov
 */
public class Java9ModuleEntryPoint extends EntryPointWithVisibilityLevel {
  public static final String ID = "moduleInfo";

  public boolean ADD_EXPORTED_PACKAGES_AND_SERVICES_TO_ENTRIES = true;

  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return JavaAnalysisBundle.message("html.classes.exposed.with.code.module.info.code.html");
  }

  @Override
  public String getTitle() {
    return JavaAnalysisBundle.message("suggest.package.private.visibility.level.for.classes.in.exported.packages.java.9");
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
      PsiJavaModule javaModule = getJavaModule(member);
      if (javaModule != null && !isServiceClass((PsiClass)member, javaModule) && isInExportedPackage((PsiClass)member, javaModule)) {
        return PsiUtil.ACCESS_LEVEL_PACKAGE_LOCAL;
      }
    }
    return ACCESS_LEVEL_INVALID;
  }

  @Override
  public boolean keepVisibilityLevel(boolean entryPointEnabled, @NotNull RefJavaElement refJavaElement) {
    if (refJavaElement instanceof RefClass) {
      RefClass refClass = (RefClass)refJavaElement;
      RefModule refModule = refClass.getModule();
      if (refModule != null) {
        RefJavaModule refJavaModule = RefJavaModule.JAVA_MODULE.get(refModule);
        if (refJavaModule != null) {
          return isServiceClass(refClass, refJavaModule) || !entryPointEnabled && isInExportedPackage(refClass, refJavaModule);
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

  private static @Nullable PsiJavaModule getJavaModule(@Nullable PsiElement element) {
    return element != null && PsiUtil.isLanguageLevel9OrHigher(element) ? JavaModuleGraphUtil.findDescriptorByElement(element) : null;
  }

  private static boolean isInExportedPackage(@NotNull PsiClass psiClass, @NotNull PsiJavaModule javaModule) {
    String packageName = getPublicApiPackageName(psiClass);
    return packageName != null && getExportedPackageNames(javaModule).contains(packageName);
  }

  private static boolean isServiceClass(@NotNull PsiClass psiClass, @NotNull PsiJavaModule javaModule) {
    String className = psiClass.getQualifiedName();
    return className != null && getServiceClassNames(javaModule).contains(className);
  }

  private static @Nullable String getPublicApiPackageName(@NotNull PsiClass psiClass) {
    if (psiClass.hasModifierProperty(PsiModifier.PUBLIC) || psiClass.hasModifierProperty(PsiModifier.PROTECTED)) {
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
    if (javaModule instanceof LightJavaModule) {
      return Collections.emptySet();
    }
    return CachedValuesManager.getCachedValue(javaModule, () -> {
      Set<String> packages = StreamEx.of(javaModule.getExports().spliterator())
        .map(PsiPackageAccessibilityStatement::getPackageName)
        .nonNull()
        .toCollection(HashSet::new);
      return CachedValueProvider.Result.create(packages, javaModule);
    });
  }

  private static Set<String> getServiceClassNames(@NotNull PsiJavaModule javaModule) {
    if (javaModule instanceof LightJavaModule) {
      return Collections.emptySet();
    }
    return CachedValuesManager.getCachedValue(javaModule, () -> {
      Set<String> classes = StreamEx.of(javaModule.getProvides().spliterator())
        .map(PsiProvidesStatement::getImplementationList)
        .nonNull()
        .flatMap(list -> StreamEx.of(list.getReferencedTypes()))
        .append(StreamEx.of(javaModule.getUses().spliterator()).map(PsiUsesStatement::getClassType).nonNull())
        .map(PsiClassType::resolve)
        .nonNull()
        .map(PsiClass::getQualifiedName)
        .nonNull()
        .toCollection(HashSet::new);
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
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void writeExternal(Element element) throws WriteExternalException {
    if (!ADD_EXPORTED_PACKAGES_AND_SERVICES_TO_ENTRIES) {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }
}