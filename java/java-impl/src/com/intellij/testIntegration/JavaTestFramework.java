// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testIntegration;

import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collections;
import java.util.concurrent.ConcurrentMap;

public abstract class JavaTestFramework implements TestFramework {
  @Override
  public boolean isLibraryAttached(@NotNull Module module) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    PsiClass c = JavaPsiFacade.getInstance(module.getProject()).findClass(getMarkerClassFQName(), scope);
    return c != null;
  }

  @Nullable
  @Override
  public String getLibraryPath() {
    ExternalLibraryDescriptor descriptor = getFrameworkLibraryDescriptor();
    if (descriptor != null) {
      return descriptor.getLibraryClassesRoots().get(0);
    }
    return null;
  }

  public ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return null;
  }

  protected abstract String getMarkerClassFQName();

  /**
   * Return {@code true} iff {@link #getMarkerClassFQName()} can be found in the resolve scope of {@code clazz} 
   */
  protected boolean isFrameworkAvailable(@NotNull PsiElement clazz) {
    return CachedValuesManager.<ConcurrentMap<String, PsiClass>>getCachedValue(clazz, () -> {
      var project = clazz.getProject();
      return new CachedValueProvider.Result<>(
        ConcurrentFactoryMap.createMap(
          markerInterfaceName -> JavaPsiFacade.getInstance(project).findClass(markerInterfaceName, clazz.getResolveScope())),
        ProjectRootManager.getInstance(project));
    }).get(getMarkerClassFQName()) != null;
  }
  
  @Override
  public boolean isTestClass(@NotNull PsiElement clazz) {
    return clazz instanceof PsiClass && isFrameworkAvailable(clazz) && isTestClass((PsiClass)clazz, false);
  }

  @Override
  public boolean isPotentialTestClass(@NotNull PsiElement clazz) {
    return clazz instanceof PsiClass && isTestClass((PsiClass)clazz, true);
  }

  protected abstract boolean isTestClass(PsiClass clazz, boolean canBePotential);

  protected boolean isUnderTestSources(PsiClass clazz) {
    PsiFile psiFile = clazz.getContainingFile();
    VirtualFile vFile = psiFile.getVirtualFile();
    if (vFile == null) return false;
    return ProjectRootManager.getInstance(clazz.getProject()).getFileIndex().isInTestSourceContent(vFile);
  }

  @Override
  @Nullable
  public PsiElement findSetUpMethod(@NotNull PsiElement clazz) {
    if (clazz instanceof PsiClass && isFrameworkAvailable(clazz)) {
      return findSetUpMethod((PsiClass)clazz);
    }
    return null;
  }

  @Nullable
  protected abstract PsiMethod findSetUpMethod(@NotNull PsiClass clazz);

  @Override
  @Nullable
  public PsiElement findTearDownMethod(@NotNull PsiElement clazz) {
    if (clazz instanceof PsiClass && isFrameworkAvailable(clazz)) {
      return findTearDownMethod((PsiClass)clazz);
    }
    return null;
  }

  @Nullable
  protected abstract PsiMethod findTearDownMethod(@NotNull PsiClass clazz);
  
  @Nullable
  @Override
  public PsiElement findBeforeClassMethod(@NotNull PsiElement clazz) {
    if (clazz instanceof PsiClass && isFrameworkAvailable(clazz)) {
      return findBeforeClassMethod((PsiClass)clazz);
    }
    return null;
  }

  @Nullable
  protected PsiMethod findBeforeClassMethod(@NotNull PsiClass clazz) {
    return null;
  }

  @Nullable
  @Override
  public PsiElement findAfterClassMethod(@NotNull PsiElement clazz) {
    if (clazz instanceof PsiClass && isFrameworkAvailable(clazz)) {
      return findAfterClassMethod((PsiClass)clazz);
    }
    return null;
  }

  @Nullable
  protected PsiMethod findAfterClassMethod(@NotNull PsiClass clazz) {
    return null;
  }

  @Override
  public PsiElement findOrCreateSetUpMethod(@NotNull PsiElement clazz) throws IncorrectOperationException {
    if (clazz instanceof PsiClass && isFrameworkAvailable(clazz)) {
      return findOrCreateSetUpMethod((PsiClass)clazz);
    }
    return null;
  }

  @Override
  public boolean isIgnoredMethod(PsiElement element) {
    return false;
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Nullable
  protected abstract PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException;

  public boolean isParameterized(PsiClass clazz) {
    return false;
  }

  @Nullable
  public PsiMethod findParametersMethod(PsiClass clazz) {
    return null;
  }

  @Nullable
  public FileTemplateDescriptor getParametersMethodFileTemplateDescriptor() {
    return null;
  }

  /**
   * @deprecated Mnemonics are not required anymore; frameworks are loaded in the combobox now
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @Deprecated
  public char getMnemonic() {
    return 0;
  }

  public PsiMethod createSetUpPatternMethod(JVMElementFactory factory) {
    final FileTemplate template = FileTemplateManager.getDefaultInstance().getCodeTemplate(getSetUpMethodFileTemplateDescriptor().getFileName());
    final String templateText = StringUtil.replace(StringUtil.replace(template.getText(), "${BODY}\n", ""), "${NAME}", "setUp");
    return factory.createMethodFromText(templateText, null);
  }

  public FileTemplateDescriptor getTestClassFileTemplateDescriptor() {
    return null;
  }

  public Promise<Void> setupLibrary(Module module) {
    ExternalLibraryDescriptor descriptor = getFrameworkLibraryDescriptor();
    if (descriptor != null) {
      return JavaProjectModelModificationService.getInstance(module.getProject()).addDependency(module, descriptor, DependencyScope.TEST);
    }
    else {
      String path = getLibraryPath();
      if (path != null) {
        OrderEntryFix.addJarsToRoots(Collections.singletonList(path), null, module, null);
        return Promises.resolvedPromise(null);
      }
    }
    return Promises.rejectedPromise();
  }

  public boolean isSingleConfig() {
    return false;
  }

  /**
   * @return true for junit 3 classes with suite method and for junit 4/junit 5 tests with @Suite annotation
   */
  public boolean isSuiteClass(PsiClass psiClass) {
    return false;
  }

  public boolean isTestMethod(PsiMethod method, PsiClass myClass) {
    return isTestMethod(method);
  }


  public boolean acceptNestedClasses() {
    return false;
  }

  @Override
  public boolean isTestMethod(PsiElement element) {
    return isTestMethod(element, true);
  }

  public boolean isMyConfigurationType(ConfigurationType type) {
    return false;
  }
}
