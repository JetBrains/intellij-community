// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GeneralModuleType extends ModuleType<ModuleBuilder>{
  public final static String TYPE_ID = "GENERAL_MODULE";
  public final static GeneralModuleType INSTANCE = new GeneralModuleType();

  public GeneralModuleType() {
    super(TYPE_ID);
  }

  @Override
  public @NotNull ModuleBuilder createModuleBuilder() {
    return new GeneralModuleBuilder();
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public @NotNull String getName() {
    return ProjectBundle.message("module.type.general");
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Override
  public @NotNull String getDescription() {
    return ProjectBundle.message("general.purpose.type.to.support.any.kind.of.development");
  }

  @Override
  public @NotNull Icon getNodeIcon(boolean isOpened) {
    return AllIcons.Nodes.Module;
  }

  private static class GeneralModuleBuilder extends ModuleBuilder {
    private static final Logger LOG = Logger.getInstance(GeneralModuleBuilder.class);

    private GeneralModuleBuilder() {
      addModuleConfigurationUpdater(new ModuleConfigurationUpdater() {
        @Override
        public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
          String basePath = module.getProject().getBasePath();
          LOG.assertTrue(basePath != null);
          VirtualFile file = LocalFileSystem.getInstance().findFileByPath(basePath);
          LOG.assertTrue(file != null);
          rootModel.addContentEntry(file);
        }
      });
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext,
                                                @NotNull ModulesProvider modulesProvider) {
      return ModuleWizardStep.EMPTY_ARRAY;
    }


    @Override
    public ModuleType<?> getModuleType() {
      return INSTANCE;
    }

    @Override
    public boolean isAvailable() {
      return Registry.is("general.project.type", false);
    }
  }
}
