
package com.intellij.ide.util.newProjectWizard;

import com.intellij.CommonBundle;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportUtil;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.Messages;
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
  private boolean myCommitted;

  public SupportForFrameworksStep(final ModuleBuilder builder, @NotNull LibrariesContainer librariesContainer) {
    List<FrameworkSupportProvider> providers = FrameworkSupportUtil.getProviders(builder);
    myFrameworkSupportModel = new FrameworkSupportModelInWizard(librariesContainer, builder);
    mySupportForFrameworksPanel = new AddSupportForFrameworksPanel(providers, librariesContainer, myFrameworkSupportModel);
    builder.addModuleConfigurationUpdater(new ModuleBuilder.ModuleConfigurationUpdater() {
      public void update(@NotNull final Module module, @NotNull final ModifiableRootModel rootModel) {
        mySupportForFrameworksPanel.addSupport(module, rootModel);
      }
    });
  }

  private static String getBaseDirectory(final ModuleBuilder builder) {
    final String path = builder.getContentEntryPath();
    return path != null ? FileUtil.toSystemIndependentName(path) : "";
  }

  public Icon getIcon() {
    return ICON;
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(mySupportForFrameworksPanel);
    super.disposeUIResources();
  }

  @NonNls
  public String getHelpId() {
    return "reference.dialogs.new.project.technologies";
  }

  public void _commit(final boolean finishChosen) throws CommitStepException {
    if (finishChosen && !myCommitted) {
      boolean ok = mySupportForFrameworksPanel.downloadLibraries();
      if (!ok) {
        int answer = Messages.showYesNoDialog(getComponent(),
                                              ProjectBundle.message("warning.message.some.required.libraries.wasn.t.downloaded"),
                                              CommonBundle.getWarningTitle(), Messages.getWarningIcon());
        if (answer != 0) {
          throw new CommitStepException(null);
        }
      }
      myCommitted = true;
    }
  }

  public JComponent getComponent() {
    return mySupportForFrameworksPanel.getMainPanel();
  }

  @Override
  public void updateStep() {
    myFrameworkSupportModel.fireWizardStepUpdated();
  }

  public void updateDataModel() {
  }

  private static class FrameworkSupportModelInWizard extends FrameworkSupportModelBase {
    private final ModuleBuilder myBuilder;

    public FrameworkSupportModelInWizard(LibrariesContainer librariesContainer, ModuleBuilder builder) {
      super(librariesContainer.getProject(), builder);
      myBuilder = builder;
    }

    @NotNull
    @Override
    public String getBaseDirectoryForLibrariesPath() {
      return getBaseDirectory(myBuilder);
    }
  }
}
