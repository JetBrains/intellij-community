// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.LocateLibraryDialog;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.ide.JavaUiBundle;
import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RepositoryAttachDialog;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiJavaModule;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class IdeaProjectModelModifier extends JavaProjectModelModifier {
  private static final Logger LOG = Logger.getInstance(IdeaProjectModelModifier.class);

  private final Project myProject;

  public IdeaProjectModelModifier(Project project) {
    myProject = project;
  }

  @Override
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    ModuleRootModificationUtil.addDependency(from, to, scope, exported);

    PsiJavaModule fromModule = ReadAction.compute(() -> JavaModuleGraphUtil.findDescriptorByModule(from, scope == DependencyScope.TEST));
    PsiJavaModule toModule = ReadAction.compute(() -> JavaModuleGraphUtil.findDescriptorByModule(to, scope == DependencyScope.TEST));
    addJigsawModule(fromModule, toModule, scope, exported);

    return Promises.resolvedPromise(null);
  }

  @Override
  public Promise<Void> addLibraryDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
    WriteAction.run(() -> OrderEntryUtil.addLibraryToRoots(from, library, scope, exported));

    PsiJavaModule fromModule = ReadAction.compute(() -> JavaModuleGraphUtil.findDescriptorByModule(from, scope == DependencyScope.TEST));
    PsiJavaModule toLibrary = ReadAction.compute(() -> JavaModuleGraphUtil.findDescriptorByLibrary(library, from.getProject()));
    addJigsawModule(fromModule, toLibrary, scope, exported);

    return Promises.resolvedPromise(null);
  }

  @Override
  public Promise<Void> addExternalLibraryDependency(@NotNull final Collection<? extends Module> modules,
                                                    @NotNull final ExternalLibraryDescriptor descriptor,
                                                    @NotNull final DependencyScope scope) {
    List<String> defaultRoots = descriptor.getLibraryClassesRoots();
    List<String> classesRoots;
    Module firstModule = ContainerUtil.getFirstItem(modules);
    if (!defaultRoots.isEmpty()) {
      LOG.assertTrue(firstModule != null);
      classesRoots = new LocateLibraryDialog(firstModule, defaultRoots, descriptor.getPresentableName()).showAndGetResult();
    }
    else {
      String version = descriptor.getPreferredVersion();
      String mavenCoordinates = descriptor.getLibraryGroupId() + ":" +
                                descriptor.getLibraryArtifactId() + ":" +
                                (version != null ? version : RepositoryLibraryDescription.ReleaseVersionId);
      RepositoryAttachDialog dialog = new RepositoryAttachDialog(myProject, mavenCoordinates, RepositoryAttachDialog.Mode.DOWNLOAD);
      if (!dialog.showAndGet()) {
        return Promises.rejectedPromise();
      }

      RepositoryLibraryProperties libraryProperties = new RepositoryLibraryProperties(dialog.getSelectedLibraryDescriptor());
      Collection<OrderRoot> roots =
        JarRepositoryManager.loadDependenciesModal(myProject, libraryProperties, dialog.getAttachSources(), dialog.getAttachJavaDoc(), dialog.getDirectoryPath(), null);
      if (roots.isEmpty()) {
        Messages.showErrorDialog(myProject, JavaUiBundle.message("dialog.mesage.0.was.not.loaded", descriptor.getPresentableName()),
                                 JavaUiBundle.message("dialog.title.failed.to.download.library"));
        return Promises.rejectedPromise();
      }
      classesRoots = roots.stream()
        .filter(root -> root.getType() == OrderRootType.CLASSES)
        .map(root -> PathUtil.getLocalPath(root.getFile()))
        .collect(Collectors.toList());
    }
    if (!classesRoots.isEmpty()) {
      String libraryName = classesRoots.size() > 1 ? descriptor.getPresentableName() : null;
      final List<String> urls = OrderEntryFix.refreshAndConvertToUrls(classesRoots);
      if (modules.size() == 1) {
        ModuleRootModificationUtil.addModuleLibrary(firstModule, libraryName, urls, Collections.emptyList(), scope);

        if (libraryName != null) {
          ReadAction.run(() -> {
            Library library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName(libraryName);
            if (library == null) return;

            for (Module module : modules) {
              PsiJavaModule fromModule = JavaModuleGraphUtil.findDescriptorByModule(module, scope == DependencyScope.TEST);
              PsiJavaModule toLibrary = JavaModuleGraphUtil.findDescriptorByLibrary(library, module.getProject());
              addJigsawModule(fromModule, toLibrary, scope, false);
            }
          });
        }
      }
      else {
        WriteAction.run(() -> {
          Library library =
            LibraryUtil.createLibrary(LibraryTablesRegistrar.getInstance().getLibraryTable(myProject), descriptor.getPresentableName());
          Library.ModifiableModel model = library.getModifiableModel();
          for (String url : urls) {
            model.addRoot(url, OrderRootType.CLASSES);
          }
          model.commit();
          for (Module module : modules) {
            ModuleRootModificationUtil.addDependency(module, library, scope, false);

            ReadAction.run(() -> {
              PsiJavaModule fromModule = JavaModuleGraphUtil.findDescriptorByModule(module, scope == DependencyScope.TEST);
              PsiJavaModule toLibrary = JavaModuleGraphUtil.findDescriptorByLibrary(library, module.getProject());
              addJigsawModule(fromModule, toLibrary, scope, false);
            });
          }
        });
      }
    }
    return Promises.resolvedPromise(null);
  }

  @Override
  public Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel level) {
    final LanguageLevel moduleLevel = LanguageLevelUtil.getCustomLanguageLevel(module);
    if (moduleLevel != null && JavaSdkUtil.isLanguageLevelAcceptable(myProject, module, level)) {
      final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
      rootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
      rootModel.commit();
    }
    else {
      LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(level);
      ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(EmptyRunnable.INSTANCE, RootsChangeRescanningInfo.TOTAL_RESCAN);
    }
    return Promises.resolvedPromise(null);
  }


  private static void addJigsawModule(@Nullable PsiJavaModule from,
                                      @Nullable PsiJavaModule to,
                                      @NotNull DependencyScope scope,
                                      boolean exported) {
    if (from == null || to == null) return;
    WriteAction.run(() -> JavaModuleGraphUtil.addDependency(from, to.getName(), scope, exported));
  }
}