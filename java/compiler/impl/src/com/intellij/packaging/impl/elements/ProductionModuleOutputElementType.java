// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ProductionModuleOutputElementType extends ModuleOutputElementTypeBase<ProductionModuleOutputPackagingElement> {
  public static final ProductionModuleOutputElementType ELEMENT_TYPE = new ProductionModuleOutputElementType();

  private ProductionModuleOutputElementType() {
    super("module-output", JavaCompilerBundle.message("element.type.name.module.output"));
  }

  @Override
  @NotNull
  public ProductionModuleOutputPackagingElement createEmpty(@NotNull Project project) {
    return new ProductionModuleOutputPackagingElement(project);
  }

  @Override
  protected ModuleOutputPackagingElementBase createElement(@NotNull Project project, @NotNull ModulePointer pointer) {
    return new ProductionModuleOutputPackagingElement(project, pointer);
  }

  @Override
  public Icon getCreateElementIcon() {
    return AllIcons.Nodes.Module;
  }

  @NotNull
  @Override
  public String getElementText(@NotNull String moduleName) {
    return JavaCompilerBundle.message("node.text.0.compile.output", moduleName);
  }

  @Override
  public boolean isSuitableModule(@NotNull ModulesProvider modulesProvider, @NotNull Module module) {
    return modulesProvider.getRootModel(module).getSourceRootUrls(false).length > 0;
  }
}
