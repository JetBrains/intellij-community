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
package com.intellij.openapi.roots.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.LocateLibraryDialog;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class IdeaProjectModelModifier extends JavaProjectModelModifier {
  private static final Logger LOG = Logger.getInstance(IdeaProjectModelModifier.class);

  private final Project myProject;

  public IdeaProjectModelModifier(Project project) {
    myProject = project;
  }

  @Override
  public Promise<Void> addModuleDependency(@NotNull Module from, @NotNull Module to, @NotNull DependencyScope scope, boolean exported) {
    ModuleRootModificationUtil.addDependency(from, to, scope, exported);
    return Promises.resolvedPromise(null);
  }

  @Override
  public Promise<Void> addLibraryDependency(@NotNull Module from, @NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
    OrderEntryUtil.addLibraryToRoots(from, library, scope, exported);
    return Promises.resolvedPromise(null);
  }

  @Override
  public Promise<Void> addExternalLibraryDependency(@NotNull final Collection<Module> modules,
                                                    @NotNull final ExternalLibraryDescriptor descriptor,
                                                    @NotNull final DependencyScope scope) {
    List<String> defaultRoots = descriptor.getLibraryClassesRoots();
    Module firstModule = ContainerUtil.getFirstItem(modules);
    LOG.assertTrue(firstModule != null);
    LocateLibraryDialog dialog = new LocateLibraryDialog(firstModule, defaultRoots, descriptor.getPresentableName());
    List<String> classesRoots = dialog.showAndGetResult();
    if (!classesRoots.isEmpty()) {
      String libraryName = classesRoots.size() > 1 ? descriptor.getPresentableName() : null;
      final List<String> urls = OrderEntryFix.refreshAndConvertToUrls(classesRoots);
      if (modules.size() == 1) {
        ModuleRootModificationUtil.addModuleLibrary(firstModule, libraryName, urls, Collections.emptyList(), scope);
      }
      else {
        new WriteAction() {
          protected void run(@NotNull Result result) {
            Library library =
              LibraryUtil.createLibrary(LibraryTablesRegistrar.getInstance().getLibraryTable(myProject), descriptor.getPresentableName());
            Library.ModifiableModel model = library.getModifiableModel();
            for (String url : urls) {
              model.addRoot(url, OrderRootType.CLASSES);
            }
            model.commit();
            for (Module module : modules) {
              ModuleRootModificationUtil.addDependency(module, library, scope, false);
            }
          }
        }.execute();
      }
    }
    return Promises.resolvedPromise(null);
  }

  @Override
  public Promise<Void> changeLanguageLevel(@NotNull Module module, @NotNull LanguageLevel level) {
    final LanguageLevel moduleLevel = LanguageLevelModuleExtensionImpl.getInstance(module).getLanguageLevel();
    if (moduleLevel != null && JavaSdkUtil.isLanguageLevelAcceptable(myProject, module, level)) {
      final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
      rootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
      rootModel.commit();
    }
    else {
      LanguageLevelProjectExtension.getInstance(myProject).setLanguageLevel(level);
      ProjectRootManagerEx.getInstanceEx(myProject).makeRootsChange(EmptyRunnable.INSTANCE, false, true);
    }
    return Promises.resolvedPromise(null);
  }
}