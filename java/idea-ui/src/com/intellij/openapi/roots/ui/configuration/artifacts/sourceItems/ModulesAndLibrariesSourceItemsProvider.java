// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.elements.ModuleElementTypeBase;
import com.intellij.packaging.impl.elements.ModulePackagingElementBase;
import com.intellij.packaging.impl.elements.PackagingElementFactoryImpl;
import com.intellij.packaging.impl.elements.ProductionModuleOutputElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.PackagingSourceItemsProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class ModulesAndLibrariesSourceItemsProvider extends PackagingSourceItemsProvider {

  @Override
  @NotNull
  public Collection<? extends PackagingSourceItem> getSourceItems(@NotNull ArtifactEditorContext editorContext, @NotNull Artifact artifact,
                                                                  PackagingSourceItem parent) {
    if (parent == null) {
      return createModuleItems(editorContext, Collections.emptyList());
    }
    else if (parent instanceof ModuleGroupItem) {
      return createModuleItems(editorContext, ((ModuleGroupItem)parent).getPath());
    }
    else if (parent instanceof ModuleSourceItemGroup) {
      return createAvailableItems(editorContext, artifact, ((ModuleSourceItemGroup)parent).getModule());
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Collection<? extends PackagingSourceItem> createAvailableItems(@NotNull ArtifactEditorContext editorContext,
                                                                                @NotNull Artifact artifact, @NotNull Module module) {
    final List<PackagingSourceItem> items = new ArrayList<>();

    for (Module toAdd : getAvailableModules(editorContext, artifact, ProductionModuleOutputElementType.ELEMENT_TYPE, module)) {
      items.add(new ModuleOutputSourceItem(toAdd));
    }

    List<Library> libraries = new ArrayList<>();
    final ModuleRootModel rootModel = editorContext.getModulesProvider().getRootModel(module);
    for (OrderEntry orderEntry : rootModel.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry libraryEntry) {
        final Library library = libraryEntry.getLibrary();
        final DependencyScope scope = libraryEntry.getScope();
        if (library != null && scope.isForProductionRuntime()) {
          libraries.add(library);
        }
      }
    }

    for (Library library : getNotAddedLibraries(editorContext, artifact, libraries)) {
      items.add(new LibrarySourceItem(library));
    }
    return items;
  }

  @NotNull
  private static Collection<? extends PackagingSourceItem> createModuleItems(@NotNull ArtifactEditorContext editorContext, @NotNull List<String> groupPath) {
    final List<PackagingSourceItem> items = new ArrayList<>();
    ModuleGrouper grouper = ModuleGrouper.instanceFor(editorContext.getProject(), editorContext.getModifiableModuleModel());
    Set<String> groups = new HashSet<>();
    for (Module module : grouper.getAllModules()) {
      List<String> path = grouper.getGroupPath(module);
      if (Comparing.equal(path, groupPath)) {
        items.add(new ModuleSourceItemGroup(module));
      }
      else if (ContainerUtil.startsWith(path, groupPath)) {
        groups.add(path.get(groupPath.size()));
      }
    }
    for (String group : groups) {
      items.add(0, new ModuleGroupItem(ContainerUtil.append(groupPath, group)));
    }
    return items;
  }

  @NotNull
  private static <E extends ModulePackagingElementBase> List<? extends Module> getAvailableModules(@NotNull final ArtifactEditorContext context,
                                                                                                   @NotNull Artifact artifact,
                                                                                                   @NotNull ModuleElementTypeBase<E> elementType,
                                                                                                   final Module... allModules) {
    final Set<Module> modules = new HashSet<>();
    for (Module module : allModules) {
      if (elementType.isSuitableModule(context.getModulesProvider(), module)) {
        modules.add(module);
      }
    }

    ArtifactUtil.processPackagingElements(artifact, elementType, moduleElement -> {
      modules.remove(moduleElement.findModule(context));
      return true;
    }, context, true);
    return new ArrayList<>(modules);
  }

  private static List<? extends Library> getNotAddedLibraries(@NotNull final ArtifactEditorContext context, @NotNull Artifact artifact,
                                                              List<? extends Library> librariesList) {
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
