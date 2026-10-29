// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.roots.JavaProjectModelModifier;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaModule;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

public final class JavaProjectModelModificationServiceImpl extends JavaProjectModelModificationService {
  private final Project myProject;

  public JavaProjectModelModificationServiceImpl(Project project) {
    myProject = project;
  }

  @Override
  public Promise<Void> addDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    ModalityState modality = ModalityState.defaultModalityState();
    Promise<Void> promise = null;
    List<JavaProjectModelModifier> modifiers = getModelModifiers();
    for (int i = 0; i < modifiers.size() && promise == null; i++) {
      promise = modifiers.get(i).addModuleDependency(from, to, scope, exported);
    }

    promise = promise == null ? Promises.rejectedPromise() : promise;
    return promise.thenAsync(_ -> addJigsawModule(from, to, scope, modality));
  }

  @Override
  public Promise<Void> addDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
    ModalityState modality = ModalityState.defaultModalityState();
    Promise<Void> promise = null;
    List<JavaProjectModelModifier> modifiers = getModelModifiers();
    for (int i = 0; i < modifiers.size() && promise == null; i++) {
      promise = modifiers.get(i).addLibraryDependency(from, library, scope, exported);
    }

    promise = promise == null ? Promises.rejectedPromise() : promise;
    return promise.thenAsync(_ -> addJigsawModule(from, library, scope, modality));
  }

  @Override
  public Promise<Void> addDependency(@NotNull Collection<? extends Module> from,
                                     @NotNull ExternalLibraryDescriptor libraryDescriptor,
                                     @NotNull DependencyScope scope) {
    ModalityState modality = ModalityState.defaultModalityState();
    Promise<Void> promise = null;
    List<JavaProjectModelModifier> modifiers = getModelModifiers();
    for (int i = 0; i < modifiers.size() && promise == null; i++) {
      promise = modifiers.get(i).addExternalLibraryDependency(from, libraryDescriptor, scope);
    }

    promise = promise == null ? Promises.rejectedPromise() : promise;
    return promise.thenAsync(_ -> {
      Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject)
        .getLibraryByName(libraryDescriptor.getPresentableName());
      List<Promise<?>> jigsawPromises = ContainerUtil.map(from, m -> addJigsawModule(m, library, scope, modality));
      return Promises.all(jigsawPromises, null);
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

  private static Promise<Void> addJigsawModule(@NotNull Module from,
                                               @NotNull Module to,
                                               @NotNull DependencyScope scope,
                                               @NotNull ModalityState modality) {
    return addJigsawModule(from, () -> JavaModuleGraphUtil.findDescriptorByModule(to, scope == DependencyScope.TEST), scope, modality);
  }

  private static Promise<Void> addJigsawModule(@NotNull Module from,
                                               @Nullable Library library,
                                               @NotNull DependencyScope scope,
                                               @NotNull ModalityState modality) {
    if (library == null) return Promises.resolvedPromise();
    return addJigsawModule(from, () -> JavaModuleGraphUtil.findDescriptorByLibrary(library, from.getProject()), scope, modality);
  }

  private static Promise<Void> addJigsawModule(@NotNull Module from,
                                               @NotNull Callable<@Nullable PsiJavaModule> findTo,
                                               @NotNull DependencyScope scope,
                                               @NotNull ModalityState modality) {
    JavaSourceRootType rootType = scope == DependencyScope.TEST ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
    VirtualFile moduleInfo = ReadAction.computeBlocking(
      () -> from.isDisposed() ? null : JBIterable.from(ModuleRootManager.getInstance(from).getSourceRoots(rootType))
        .filterMap(root -> root.findChild(PsiJavaModule.MODULE_INFO_FILE))
        .first());
    if (moduleInfo == null) return Promises.resolvedPromise();
    AsyncPromise<Void> result = new AsyncPromise<>();
    ReadAction.nonBlocking(() -> {
        PsiJavaModule to = findTo.call();
        if (to == null) return null;
        PsiJavaModule fromModule = JavaModuleGraphUtil.findDescriptorByModule(from, scope == DependencyScope.TEST);
        return fromModule == null ? null : Couple.of(fromModule, to);
      })
      .expireWhen(from::isDisposed)
      .finishOnUiThread(modality, modules -> {
        try {
          if (modules != null) {
            WriteCommandAction.writeCommandAction(from.getProject(), modules.first.getContainingFile())
              .withName(QuickFixBundle.message("module.info.add.requires.command", modules.second.getName()))
              .run(() -> JavaModuleGraphUtil.addDependency(modules.first, modules.second, scope));
          }
          result.setResult(null);
        }
        catch (Throwable e) {
          result.setError(e);
        }
      })
      .submit(AppExecutorUtil.getAppExecutorService())
      .onError(result::setError);
    return result;
  }
}