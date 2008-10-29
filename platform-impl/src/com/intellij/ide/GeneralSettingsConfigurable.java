package com.intellij.ide;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class GeneralSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private MyComponent myComponent;


  public void apply() {
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setReopenLastProject(myComponent.myChkReopenLastProject.isSelected());
    settings.setSyncOnFrameActivation(myComponent.myChkSyncOnFrameActivation.isSelected());
    settings.setSaveOnFrameDeactivation(myComponent.myChkSaveOnFrameDeactivation.isSelected());
    settings.setConfirmExit(myComponent.myConfirmExit.isSelected());
    // AutoSave in inactive
    settings.setAutoSaveIfInactive(myComponent.myChkAutoSaveIfInactive.isSelected());
    try {
      int newInactiveTimeout = Integer.parseInt(myComponent.myTfInactiveTimeout.getText());
      if (newInactiveTimeout > 0) {
        settings.setInactiveTimeout(newInactiveTimeout);
      }
    }
    catch (NumberFormatException e) {
    }


  }

  public boolean isModified() {
    boolean isModified = false;
    GeneralSettings settings = GeneralSettings.getInstance();
    isModified |= settings.isReopenLastProject() != myComponent.myChkReopenLastProject.isSelected();
    isModified |= settings.isSyncOnFrameActivation() != myComponent.myChkSyncOnFrameActivation.isSelected();
    isModified |= settings.isSaveOnFrameDeactivation() != myComponent.myChkSaveOnFrameDeactivation.isSelected();
    isModified |= settings.isAutoSaveIfInactive() != myComponent.myChkAutoSaveIfInactive.isSelected();
    isModified |= settings.isConfirmExit() != myComponent.myConfirmExit.isSelected();

    int inactiveTimeout = -1;
    try {
      inactiveTimeout = Integer.parseInt(myComponent.myTfInactiveTimeout.getText());
    }
    catch (NumberFormatException e) {
    }

    isModified |= inactiveTimeout > 0 && settings.getInactiveTimeout() != inactiveTimeout;

    return isModified;
  }

  public JComponent createComponent() {
//    optionGroup.add(getDiffOptions().getPanel());
    myComponent = new MyComponent();

    myComponent.myChkAutoSaveIfInactive.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myComponent.myTfInactiveTimeout.setEditable(myComponent.myChkAutoSaveIfInactive.isSelected());
      }
    });

    return myComponent.myPanel;
  }

  public String getDisplayName() {
    return IdeBundle.message("title.general");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableGeneral.png");
  }

  public void reset() {
    GeneralSettings settings = GeneralSettings.getInstance();
    myComponent.myChkReopenLastProject.setSelected(settings.isReopenLastProject());
    myComponent.myChkSyncOnFrameActivation.setSelected(settings.isSyncOnFrameActivation());
    myComponent.myChkSaveOnFrameDeactivation.setSelected(settings.isSaveOnFrameDeactivation());

    myComponent.myChkAutoSaveIfInactive.setSelected(settings.isAutoSaveIfInactive());
    myComponent.myTfInactiveTimeout.setText(Integer.toString(settings.getInactiveTimeout()));
    myComponent.myTfInactiveTimeout.setEditable(settings.isAutoSaveIfInactive());
    myComponent.myConfirmExit.setSelected(settings.isConfirmExit());
  }

  public void disposeUIResources() {
    myComponent = null;
  }

  @NotNull
  public String getHelpTopic() {
    return "preferences.general";
  }



  private static class MyComponent {
    JPanel myPanel;

    private JCheckBox myChkReopenLastProject;
    private JCheckBox myChkSyncOnFrameActivation;
    private JCheckBox myChkSaveOnFrameDeactivation;
    private JCheckBox myChkAutoSaveIfInactive;
    private JTextField myTfInactiveTimeout;
    public JCheckBox myConfirmExit;


    public MyComponent() {
    }
  }

  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}