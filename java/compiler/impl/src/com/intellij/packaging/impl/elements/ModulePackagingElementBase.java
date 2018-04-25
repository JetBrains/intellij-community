// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.Generator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModulePointer;
import com.intellij.openapi.module.ModulePointerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class ModulePackagingElementBase extends PackagingElement<ModulePackagingElementState> implements ModulePackagingElement {
  protected final Project myProject;
  protected ModulePointer myModulePointer;

  public ModulePackagingElementBase(PackagingElementType type, Project project, ModulePointer modulePointer) {
    super(type);
    myProject = project;
    myModulePointer = modulePointer;
  }

  public ModulePackagingElementBase(PackagingElementType type, Project project) {
    super(type);
    myProject = project;
  }

  @NotNull
  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                          @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    String property = getDirectoryAntProperty(generationContext);
    if (myModulePointer != null && property != null) {
      final String moduleOutput = BuildProperties.propertyRef(property);
      return Collections.singletonList(creator.createDirectoryContentCopyInstruction(moduleOutput));
    }
    return Collections.emptyList();
  }

  @Nullable
  protected abstract String getDirectoryAntProperty(ArtifactAntGenerationContext generationContext);

  @NotNull
  @Override
  public PackagingElementOutputKind getFilesKind(PackagingElementResolvingContext context) {
    return PackagingElementOutputKind.DIRECTORIES_WITH_CLASSES;
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element.getClass() == getClass() && myModulePointer != null
           && myModulePointer.equals(((ModulePackagingElementBase)element).myModulePointer);
  }

  public ModulePackagingElementState getState() {
    final ModulePackagingElementState state = new ModulePackagingElementState();
    if (myModulePointer != null) {
      state.setModuleName(myModulePointer.getModuleName());
    }
    return state;
  }

  public void loadState(@NotNull ModulePackagingElementState state) {
    final String moduleName = state.getModuleName();
    myModulePointer = moduleName != null ? ModulePointerManager.getInstance(myProject).create(moduleName) : null;
  }

  @Override
  @Nullable
  public String getModuleName() {
    return myModulePointer != null ? myModulePointer.getModuleName() : null;
  }

  @Override
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
}
