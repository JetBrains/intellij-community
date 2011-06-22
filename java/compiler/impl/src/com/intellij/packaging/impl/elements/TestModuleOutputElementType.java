/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class TestModuleOutputElementType extends ModuleOutputElementTypeBase<TestModuleOutputPackagingElement> {
  public static final TestModuleOutputElementType ELEMENT_TYPE = new TestModuleOutputElementType();

  public TestModuleOutputElementType() {
    super("module-test-output", CompilerBundle.message("element.type.name.module.test.output"));
  }

  @NotNull
  @Override
  public TestModuleOutputPackagingElement createEmpty(@NotNull Project project) {
    return new TestModuleOutputPackagingElement(project);
  }

  protected ModuleOutputPackagingElementBase createElement(@NotNull Project project, @NotNull ModulePointer pointer) {
    return new TestModuleOutputPackagingElement(project, pointer);
  }

  @Override
  public Icon getCreateElementIcon() {
    return PlatformIcons.TEST_SOURCE_FOLDER;
  }

  protected List<Module> getSuitableModules(ArtifactEditorContext context) {
    ModulesProvider modulesProvider = context.getModulesProvider();
    ArrayList<Module> modules = new ArrayList<Module>();
    for (Module module : modulesProvider.getModules()) {
      if (hasTestSourceRoots(modulesProvider.getRootModel(module))) {
        modules.add(module);
      }
    }
    return modules;
  }

  private static boolean hasTestSourceRoots(final ModuleRootModel rootModel) {
    for (ContentEntry entry : rootModel.getContentEntries()) {
      for (SourceFolder folder : entry.getSourceFolders()) {
        if (folder.isTestSource()) return true;
      }
    }
    return false;
  }
}
