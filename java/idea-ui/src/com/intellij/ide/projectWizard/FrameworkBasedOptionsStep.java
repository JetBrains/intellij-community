package com.intellij.ide.projectWizard;

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurableBase;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProviderBase;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.SdkSettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Dmitry Avdeev
 */
public abstract class FrameworkBasedOptionsStep<T extends FrameworkSupportProviderBase, B extends ModuleBuilder> extends ModuleWizardStep {

  private final FrameworkSupportConfigurableBase myConfigurable;
  private final SdkSettingsStep mySdkSettingsStep;
  private final JPanel myPanel;
  protected final FrameworkSupportModelBase myFrameworkSupportModel;
  protected final B myBuilder;
  protected final WizardContext myContext;

  public FrameworkBasedOptionsStep(T provider, final B builder, WizardContext context, String sdkLabel) {
    myContext = context;
    LibrariesContainer container = LibrariesContainerFactory.createContainer(myContext.getProject());
    myBuilder = builder;
    myFrameworkSupportModel = new FrameworkSupportModelBase(context.getProject(), myBuilder, container) {
      @NotNull
      @Override
      public String getBaseDirectoryForLibrariesPath() {
        return StringUtil.notNullize(builder.getContentEntryPath());
      }
    };
    //noinspection AbstractMethodCallInConstructor
    myConfigurable = createConfigurable(provider, myFrameworkSupportModel);
    myFrameworkSupportModel.selectFramework(provider, true);

    builder.addModuleConfigurationUpdater(new ModuleBuilder.ModuleConfigurationUpdater() {
      @Override
      public void update(@NotNull Module module, @NotNull ModifiableRootModel rootModel) {
        myConfigurable.addSupport(module, rootModel, null);
      }
    });

    mySdkSettingsStep = new SdkSettingsStep(context, builder, new Condition<SdkTypeId>() {
      @Override
      public boolean value(SdkTypeId id) {
        return acceptSdk(id);
      }
    });

    mySdkSettingsStep.getJdkComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDataModel();
      }
    });

    myPanel = new JPanel(new BorderLayout(0, 4));
    if (!mySdkSettingsStep.isEmpty()) {
      JComponent component = mySdkSettingsStep.getComponent();
      component.add(new JBLabel(sdkLabel), BorderLayout.WEST);
      myPanel.add(component, BorderLayout.NORTH);
    }
    myPanel.add(myConfigurable.getComponent(), BorderLayout.CENTER);
    updateDataModel();
  }

  protected abstract boolean acceptSdk(SdkTypeId id);

  protected abstract FrameworkSupportConfigurableBase createConfigurable(T provider, FrameworkSupportModelBase model);

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
    mySdkSettingsStep.updateDataModel();
    myFrameworkSupportModel.fireWizardStepUpdated();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    return mySdkSettingsStep.validate();
  }

}
