// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class ModuleElementTypeBase<E extends ModulePackagingElementBase> extends PackagingElementType<E> {
  public ModuleElementTypeBase(String id, Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> presentableName) {
    super(id, presentableName);
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return !getSuitableModules(context).isEmpty();
  }

  @Override
  public @NotNull List<? extends PackagingElement<?>> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                      @NotNull CompositePackagingElement<?> parent) {
    List<Module> suitableModules = getSuitableModules(context);
    List<Module> selected = context.chooseModules(suitableModules, JavaCompilerBundle.message("dialog.title.packaging.choose.module"));

    final List<PackagingElement<?>> elements = new ArrayList<>();
    final ModulePointerManager pointerManager = ModulePointerManager.getInstance(context.getProject());
    for (Module module : selected) {
      elements.add(createElement(context.getProject(), pointerManager.create(module)));
    }
    return elements;
  }

  protected abstract ModulePackagingElementBase createElement(@NotNull Project project, @NotNull ModulePointer pointer);

  private List<Module> getSuitableModules(ArtifactEditorContext context) {
    ModulesProvider modulesProvider = context.getModulesProvider();
    ArrayList<Module> modules = new ArrayList<>();
    for (Module module : modulesProvider.getModules()) {
      if (isSuitableModule(modulesProvider, module)) {
        modules.add(module);
      }
    }
    return modules;
  }

  public abstract boolean isSuitableModule(@NotNull ModulesProvider modulesProvider, @NotNull Module module);

  /**
   * Provides element presentation text.
   * @param moduleName name of the module for which this presentation is requested.
   * @return text to display.
   */
  public abstract @NotNull String getElementText(@NotNull String moduleName);

  public Icon getElementIcon(@Nullable Module module) {
    return module != null ? ModuleType.get(module).getIcon() : AllIcons.Nodes.Module;
  }
}
