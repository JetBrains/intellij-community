// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;
import java.util.List;

public final class JavaProjectModelModificationServiceImpl extends JavaProjectModelModificationService {
  private final Project myProject;

  public JavaProjectModelModificationServiceImpl(Project project) {
    myProject = project;
  }

  @Override
  public Promise<Void> addDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    Promise<Void> promise = null;
    List<JavaProjectModelModifier> modifiers = getModelModifiers();
    for (int i = 0; i < modifiers.size() && promise == null; i++) {
      promise = modifiers.get(i).addModuleDependency(from, to, scope, exported);
    }

    promise = promise == null ? Promises.rejectedPromise() : promise;
    return promise.onSuccess(v -> addJigsawModule(from, to, scope));
  }

  @Override
  public Promise<Void> addDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
    Promise<Void> promise = null;
    List<JavaProjectModelModifier> modifiers = getModelModifiers();
    for (int i = 0; i < modifiers.size() && promise == null; i++) {
      promise = modifiers.get(i).addLibraryDependency(from, library, scope, exported);
    }

    promise = promise == null ? Promises.rejectedPromise() : promise;
    return promise.onSuccess(v -> addJigsawModule(from, library, scope));
  }

  @Override
  public Promise<Void> addDependency(@NotNull Collection<? extends Module> from,
                                     @NotNull ExternalLibraryDescriptor libraryDescriptor,
                                     @NotNull DependencyScope scope) {
    Promise<Void> promise = null;
    List<JavaProjectModelModifier> modifiers = getModelModifiers();
    for (int i = 0; i < modifiers.size() && promise == null; i++) {
      promise = modifiers.get(i).addExternalLibraryDependency(from, libraryDescriptor, scope);
    }

    promise = promise == null ? Promises.rejectedPromise() : promise;
    return promise.onSuccess(v -> {
      Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject)
        .getLibraryByName(libraryDescriptor.getPresentableName());
      from.forEach(m -> addJigsawModule(m, library, scope));
    });
  }

  @Override
  public Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel languageLevel, boolean modifySource) {
    if (modifySource) {
      for (JavaProjectModelModifier modifier : getModelModifiers()) {
        Promise<Void> promise = modifier.changeLanguageLevel(module, languageLevel);
        if (promise != null) {
          return promise;
        }
      }
    }
    else {
      final LanguageLevel moduleLevel = LanguageLevelUtil.getCustomLanguageLevel(module);
      if (moduleLevel != null && JavaSdkUtil.isLanguageLevelAcceptable(myProject, module, languageLevel)) {
        final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
        rootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(languageLevel);
        rootModel.commit();
      }
      else {
        LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(languageLevel);
        ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(EmptyRunnable.INSTANCE, RootsChangeRescanningInfo.TOTAL_RESCAN);
      }
      return Promises.resolvedPromise(null);
    }

    return Promises.rejectedPromise();
  }

  private List<JavaProjectModelModifier> getModelModifiers() {
    return JavaProjectModelModifier.EP_NAME.getExtensionList(myProject);
  }

  private static void addJigsawModule(@NotNull Module from,
                                      @NotNull Module to,
                                      @NotNull DependencyScope scope) {
    PsiJavaModule toModule = ReadAction.compute(() -> JavaModuleGraphUtil.findDescriptorByModule(to, scope == DependencyScope.TEST));
    addJigsawModule(from, toModule, scope);
  }

  private static void addJigsawModule(@NotNull Module from,
                                      @Nullable Library library,
                                      @NotNull DependencyScope scope) {
    if (library == null) return;
    PsiJavaModule toModule = ReadAction.compute(() -> JavaModuleGraphUtil.findDescriptorByLibrary(library, from.getProject()));
    addJigsawModule(from, toModule, scope);
  }

  private static void addJigsawModule(@NotNull Module from,
                                      @Nullable PsiJavaModule to,
                                      @NotNull DependencyScope scope) {
    if (to == null) return;
    PsiJavaModule fromModule = ReadAction.compute(() -> JavaModuleGraphUtil.findDescriptorByModule(from, scope == DependencyScope.TEST));
    if (fromModule == null) return;
    CommandProcessor.getInstance().runUndoTransparentAction(() -> {
      WriteAction.run(() -> {
        JavaModuleGraphUtil.addDependency(fromModule, to, scope);
      });
    });
  }
}