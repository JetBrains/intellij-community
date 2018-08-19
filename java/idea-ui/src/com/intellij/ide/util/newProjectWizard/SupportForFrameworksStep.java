// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.newProjectWizard;

import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class SupportForFrameworksStep extends ModuleWizardStep {
  private final AddSupportForFrameworksPanel mySupportForFrameworksPanel;
  private final FrameworkSupportModelBase myFrameworkSupportModel;
  private final WizardContext myContext;
  private final ModuleBuilder myBuilder;
  private final ModuleBuilder.ModuleConfigurationUpdater myConfigurationUpdater;
  private boolean myCommitted;

  public SupportForFrameworksStep(WizardContext context, final ModuleBuilder builder,
                                  @NotNull LibrariesContainer librariesContainer) {
    myContext = context;
    myBuilder = builder;
    List<FrameworkSupportInModuleProvider> providers = FrameworkSupportUtil.getProviders(builder);
    myFrameworkSupportModel = new FrameworkSupportModelInWizard(librariesContainer, builder);
    mySupportForFrameworksPanel = new AddSupportForFrameworksPanel(providers, myFrameworkSupportModel, false, null);
    myConfigurationUpdater = new ModuleBuilder.ModuleConfigurationUpdater() {
      @Override
      public void update(@NotNull final Module module, @NotNull final ModifiableRootModel rootModel) {
        mySupportForFrameworksPanel.addSupport(module, rootModel);
      }
    };
    builder.addModuleConfigurationUpdater(myConfigurationUpdater);
  }

  private static String getBaseDirectory(final ModuleBuilder builder) {
    final String path = builder.getContentEntryPath();
    return path != null ? FileUtil.toSystemIndependentName(path) : "";
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(mySupportForFrameworksPanel);
    super.disposeUIResources();
  }

  @Override
  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.technologies";
  }

  @Override
  public void _commit(final boolean finishChosen) throws CommitStepException {
    if (finishChosen && !myCommitted) {
      if (!mySupportForFrameworksPanel.validate()) {
        throw new CommitStepException(null);
      }
      if (!mySupportForFrameworksPanel.downloadLibraries(mySupportForFrameworksPanel.getMainPanel())) {
        throw new CommitStepException(null);
      }
      myCommitted = true;
    }
  }

  @Override
  public void onWizardFinished() throws CommitStepException {
    _commit(true);
  }

  @Override
  public JComponent getComponent() {
    return mySupportForFrameworksPanel.getMainPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySupportForFrameworksPanel.getFrameworksTree();
  }

  @Override
  public void updateStep() {
    ProjectBuilder builder = myContext.getProjectBuilder();
    if (builder instanceof ModuleBuilder) {
      myBuilder.updateFrom((ModuleBuilder)builder);
      ((ModuleBuilder)builder).addModuleConfigurationUpdater(myConfigurationUpdater);
    }
    myFrameworkSupportModel.fireWizardStepUpdated();
  }

  @Override
  public void updateDataModel() {
  }

  @Override
  public String getName() {
    return "Add Frameworks";
  }

  private static class FrameworkSupportModelInWizard extends FrameworkSupportModelBase {
    private final ModuleBuilder myBuilder;

    public FrameworkSupportModelInWizard(LibrariesContainer librariesContainer, ModuleBuilder builder) {
      super(librariesContainer.getProject(), builder, librariesContainer);
      myBuilder = builder;
    }

    @NotNull
    @Override
    public String getBaseDirectoryForLibrariesPath() {
      return getBaseDirectory(myBuilder);
    }
  }
}
