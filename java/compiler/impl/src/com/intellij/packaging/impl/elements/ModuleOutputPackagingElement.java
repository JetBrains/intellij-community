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

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.Generator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.ui.DelegatedPackagingElementPresentation;
import com.intellij.packaging.impl.ui.ModuleElementPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ModuleOutputPackagingElement extends PackagingElement<ModuleOutputPackagingElement.ModuleOutputPackagingElementState> {
  @NonNls public static final String MODULE_NAME_ATTRIBUTE = "name";
  private ModulePointer myModulePointer;
  private final Project myProject;

  public ModuleOutputPackagingElement(@NotNull Project project) {
    super(ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE);
    myProject = project;
  }

  public ModuleOutputPackagingElement(@NotNull Project project, @NotNull ModulePointer modulePointer) {
    super(ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE);
    myProject = project;
    myModulePointer = modulePointer;
  }

  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new DelegatedPackagingElementPresentation(new ModuleElementPresentation(myModulePointer, context));
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext, @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    if (myModulePointer != null) {
      final String moduleOutput = BuildProperties.propertyRef(generationContext.getModuleOutputPath(myModulePointer.getModuleName()));
      return Collections.singletonList(creator.createDirectoryContentCopyInstruction(moduleOutput));
    }
    return Collections.emptyList();
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext, @NotNull ArtifactType artifactType) {
    final Module module = findModule(resolvingContext);
    if (module != null) {
      final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
      if (extension != null) {
        final VirtualFile output = extension.getCompilerOutputPath();
        if (output != null) {
          creator.addDirectoryCopyInstructions(output, null);
        }
      }
    }
  }

  @NotNull
  @Override
  public PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    return PackagingElementOutputKind.DIRECTORIES_WITH_CLASSES;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ModuleOutputPackagingElement && myModulePointer != null
           && myModulePointer.equals(((ModuleOutputPackagingElement)element).myModulePointer);
  }

  public ModuleOutputPackagingElementState getState() {
    final ModuleOutputPackagingElementState state = new ModuleOutputPackagingElementState();
    if (myModulePointer != null) {
      state.setModuleName(myModulePointer.getModuleName());
    }
    return state;
  }

  public void loadState(ModuleOutputPackagingElementState state) {
    final String moduleName = state.getModuleName();
    myModulePointer = moduleName != null ? ModulePointerManager.getInstance(myProject).create(moduleName) : null;
  }

  @NonNls @Override
  public String toString() {
    return "module:" + getModuleName();
  }

  @Nullable
  public String getModuleName() {
    return myModulePointer != null ? myModulePointer.getModuleName() : null;
  }

  @Nullable
  public Module findModule(PackagingElementResolvingContext context) {
    if (myModulePointer != null) {
      final Module module = myModulePointer.getModule();
      final ModulesProvider modulesProvider = context.getModulesProvider();
      if (module != null) {
        if (modulesProvider instanceof DefaultModulesProvider//optimization
           || ArrayUtil.contains(module, modulesProvider.getModules())) {
          return module;
        }
      }
      return modulesProvider.getModule(myModulePointer.getModuleName());
    }
    return null;
  }

  public static class ModuleOutputPackagingElementState {
    private String myModuleName;

    @Attribute(MODULE_NAME_ATTRIBUTE)
    public String getModuleName() {
      return myModuleName;
    }

    public void setModuleName(String moduleName) {
      myModuleName = moduleName;
    }
  }
}
