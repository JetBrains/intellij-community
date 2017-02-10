/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.elements.FileCopyPackagingElement;
import com.intellij.packaging.impl.elements.ProductionModuleOutputElementType;
import com.intellij.packaging.impl.elements.ModuleOutputPackagingElement;
import com.intellij.packaging.impl.elements.PackagingElementFactoryImpl;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.PackagingSourceItemsProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class ModulesAndLibrariesSourceItemsProvider extends PackagingSourceItemsProvider {

  @Override
  @NotNull
  public Collection<? extends PackagingSourceItem> getSourceItems(@NotNull ArtifactEditorContext editorContext, @NotNull Artifact artifact,
                                                                  PackagingSourceItem parent) {
    if (parent == null) {
      return createModuleItems(editorContext, ArrayUtil.EMPTY_STRING_ARRAY);
    }
    else if (parent instanceof ModuleGroupItem) {
      return createModuleItems(editorContext, ((ModuleGroupItem)parent).getPath());
    }
    else if (parent instanceof ModuleSourceItemGroup) {
      return createClasspathItems(editorContext, artifact, ((ModuleSourceItemGroup)parent).getModule());
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Collection<? extends PackagingSourceItem> createClasspathItems(@NotNull ArtifactEditorContext editorContext,
                                                                                @NotNull Artifact artifact, @NotNull Module module) {
    final List<PackagingSourceItem> items = new ArrayList<>();
    final ModuleRootModel rootModel = editorContext.getModulesProvider().getRootModel(module);
    List<Library> libraries = new ArrayList<>();
    for (OrderEntry orderEntry : rootModel.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryEntry = (LibraryOrderEntry)orderEntry;
        final Library library = libraryEntry.getLibrary();
        final DependencyScope scope = libraryEntry.getScope();
        if (library != null && scope.isForProductionRuntime()) {
          libraries.add(library);
        }
      }
    }

    for (Module toAdd : getNotAddedModules(editorContext, artifact, module)) {
      items.add(new ModuleOutputSourceItem(toAdd));
    }

    for (Library library : getNotAddedLibraries(editorContext, artifact, libraries)) {
      items.add(new LibrarySourceItem(library));
    }
    return items;
  }

  @NotNull
  private static Collection<? extends PackagingSourceItem> createModuleItems(@NotNull ArtifactEditorContext editorContext, @NotNull String[] groupPath) {
    final Module[] modules = editorContext.getModulesProvider().getModules();
    final List<PackagingSourceItem> items = new ArrayList<>();
    Set<String> groups = new HashSet<>();
    for (Module module : modules) {
      String[] path = ModuleManager.getInstance(editorContext.getProject()).getModuleGroupPath(module);
      if (path == null) {
        path = ArrayUtil.EMPTY_STRING_ARRAY;
      }

      if (Comparing.equal(path, groupPath)) {
        items.add(new ModuleSourceItemGroup(module));
      }
      else if (ArrayUtil.startsWith(path, groupPath)) {
        groups.add(path[groupPath.length]);
      }
    }
    for (String group : groups) {
      items.add(0, new ModuleGroupItem(ArrayUtil.append(groupPath, group)));
    }
    return items;
  }

  @NotNull
  private static List<? extends Module> getNotAddedModules(@NotNull final ArtifactEditorContext context, @NotNull Artifact artifact,
                                                          final Module... allModules) {
    final Set<Module> modules = new HashSet<>(Arrays.asList(allModules));
    ArtifactUtil.processPackagingElements(artifact, ProductionModuleOutputElementType.ELEMENT_TYPE, new Processor<ModuleOutputPackagingElement>() {
      @Override
      public boolean process(ModuleOutputPackagingElement moduleOutputPackagingElement) {
        modules.remove(moduleOutputPackagingElement.findModule(context));
        return true;
      }
    }, context, true);
    return new ArrayList<>(modules);
  }

  private static List<? extends Library> getNotAddedLibraries(@NotNull final ArtifactEditorContext context, @NotNull Artifact artifact,
                                                             List<Library> librariesList) {
    final Set<VirtualFile> roots = new HashSet<>();
    ArtifactUtil.processPackagingElements(artifact, PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE, fileCopyPackagingElement -> {
      final VirtualFile root = fileCopyPackagingElement.getLibraryRoot();
      if (root != null) {
        roots.add(root);
      }
      return true;
    }, context, true);
    final List<Library> result = new ArrayList<>();
    for (Library library : librariesList) {
      if (!roots.containsAll(Arrays.asList(library.getFiles(OrderRootType.CLASSES)))) {
        result.add(library);
      }
    }
    return result;
  }
}
