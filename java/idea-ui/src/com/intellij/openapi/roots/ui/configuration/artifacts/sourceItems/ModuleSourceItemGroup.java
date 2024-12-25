// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleGrouper;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.ui.*;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ModuleSourceItemGroup extends PackagingSourceItem {
  private final Module myModule;

  public ModuleSourceItemGroup(@NotNull Module module) {
    super(true);
    myModule = module;
  }

  @Override
  public @NotNull SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new ModuleSourceItemPresentation(myModule, context);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ModuleSourceItemGroup && myModule.equals(((ModuleSourceItemGroup)obj).myModule);
  }

  @Override
  public int hashCode() {
    return myModule.hashCode();
  }

  @Override
  public @NotNull List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
    final Set<Module> modules = new LinkedHashSet<>();
    collectDependentModules(myModule, modules, context);

    final Artifact artifact = context.getArtifact();
    final ArtifactType artifactType = artifact.getArtifactType();
    Set<PackagingSourceItem> items = new LinkedHashSet<>();
    for (Module module : modules) {
      for (PackagingSourceItemsProvider provider : PackagingSourceItemsProvider.EP_NAME.getExtensions()) {
        final ModuleSourceItemGroup parent = new ModuleSourceItemGroup(module);
        for (PackagingSourceItem sourceItem : provider.getSourceItems(context, artifact, parent)) {
          if (artifactType.isSuitableItem(sourceItem) && sourceItem.isProvideElements()) {
            items.add(sourceItem);
          }
        }
      }
    }

    List<PackagingElement<?>> result = new ArrayList<>();
    final PackagingElementFactory factory = PackagingElementFactory.getInstance();
    for (PackagingSourceItem item : items) {
      final String path = artifactType.getDefaultPathFor(item.getKindOfProducedElements());
      if (path != null) {
        result.addAll(factory.createParentDirectories(path, item.createElements(context)));
      }
    }
    return result;
  }

  private static void collectDependentModules(final Module module, Set<? super Module> modules, ArtifactEditorContext context) {
    if (!modules.add(module)) return;
    
    for (OrderEntry entry : context.getModulesProvider().getRootModel(module).getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry moduleEntry) {
        final Module dependency = moduleEntry.getModule();
        final DependencyScope scope = moduleEntry.getScope();
        if (dependency != null && scope.isForProductionRuntime()) {
          collectDependentModules(dependency, modules, context);
        }
      }
    }
  }

  public @NotNull Module getModule() {
    return myModule;
  }

  private static class ModuleSourceItemPresentation extends SourceItemPresentation {
    private final Module myModule;
    private final ArtifactEditorContext myContext;

    ModuleSourceItemPresentation(@NotNull Module module, ArtifactEditorContext context) {
      myModule = module;
      myContext = context;
    }

    @Override
    public String getPresentableName() {
      return ModuleGrouper.instanceFor(myContext.getProject(), myContext.getModifiableModuleModel()).getShortenedName(myModule);
    }

    @Override
    public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                       SimpleTextAttributes commentAttributes) {
      presentationData.setIcon(ModuleType.get(myModule).getIcon());
      presentationData.addText(getPresentableName(), mainAttributes);
    }

    @Override
    public boolean canNavigateToSource() {
      return true;
    }

    @Override
    public void navigateToSource() {
      myContext.selectModule(myModule);
    }

    @Override
    public int getWeight() {
      return SourceItemWeights.MODULE_WEIGHT;
    }
  }
}
