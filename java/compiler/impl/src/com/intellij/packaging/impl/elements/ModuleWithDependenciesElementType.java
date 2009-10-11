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
package com.intellij.packaging.impl.elements;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.ComplexPackagingElementType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ModuleWithDependenciesElementType extends ComplexPackagingElementType<ModuleWithDependenciesPackagingElement> {
  public static final ModuleWithDependenciesElementType MODULE_WITH_DEPENDENCIES_TYPE = new ModuleWithDependenciesElementType();

  public ModuleWithDependenciesElementType() {
    super("module-with-dependencies", "Module With Dependencies");
  }

  @Override
  public String getShowContentActionText() {
    return "Module with dependencies";
  }

  @Override
  public Icon getCreateElementIcon() {
    return IconLoader.getIcon("/nodes/ModuleOpen.png");
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return context.getModulesProvider().getModules().length > 0;
  }

  @NotNull
  public List<? extends ModuleWithDependenciesPackagingElement> chooseAndCreate(@NotNull ArtifactEditorContext context,
                                                                                 @NotNull Artifact artifact,
                                                                                 @NotNull CompositePackagingElement<?> parent) {
    final List<Module> modules = ModuleOutputElementType.chooseModules(context);
    final List<ModuleWithDependenciesPackagingElement> elements = new ArrayList<ModuleWithDependenciesPackagingElement>();
    for (Module module : modules) {
      elements.add(new ModuleWithDependenciesPackagingElement(module.getName()));
    }
    return elements;
  }

  @NotNull
  public ModuleWithDependenciesPackagingElement createEmpty(@NotNull Project project) {
    return new ModuleWithDependenciesPackagingElement();
  }
}
