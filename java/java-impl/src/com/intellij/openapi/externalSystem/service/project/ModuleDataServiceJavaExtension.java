// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.manage.ModuleDataServiceExtension;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class ModuleDataServiceJavaExtension implements ModuleDataServiceExtension {
  private static final Logger LOG = Logger.getInstance(ModuleDataServiceJavaExtension.class);

  @Override
  public void importModule(@NotNull IdeModifiableModelsProvider modelsProvider, @NotNull Module module, @NotNull ModuleData data) {
    if (ModuleTypeId.JAVA_MODULE.equals(module.getModuleTypeName())) {
      ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
      setLanguageLevel(modifiableRootModel, data);

      setBytecodeTargetLevel(module, data);
    }
  }

  private static void setLanguageLevel(@NotNull ModifiableRootModel modifiableRootModel, ModuleData data) {
    LanguageLevel level = LanguageLevel.parse(data.getSourceCompatibility());
    if (level != null) {
      try {
        modifiableRootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
      }
      catch (IllegalArgumentException e) {
        LOG.debug(e);
      }
    }
  }

  private static void setBytecodeTargetLevel(@NotNull Module module, @NotNull ModuleData data) {
    String targetLevel = data.getTargetCompatibility();
    if (targetLevel != null) {
      CompilerConfiguration configuration = CompilerConfiguration.getInstance(module.getProject());
      configuration.setBytecodeTargetLevel(module, targetLevel);
    }
  }
}
