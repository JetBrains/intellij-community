package com.intellij.ide;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.charset.Charset;

public class GeneralSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private MyComponent myComponent;
  @NonNls private static final String SYSTEM_DEFAULT_ENCODING = "System Default";

  public void apply() {
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setBrowserPath(myComponent.myBrowserPathField.getText());
    settings.setReopenLastProject(myComponent.myChkReopenLastProject.isSelected());
    settings.setSyncOnFrameActivation(myComponent.myChkSyncOnFrameActivation.isSelected());
    settings.setSaveOnFrameDeactivation(myComponent.myChkSaveOnFrameDeactivation.isSelected());
    settings.setUseDefaultBrowser(myComponent.myUseSystemDefaultBrowser.isSelected());
    settings.setConfirmExit(myComponent.myConfirmExit.isSelected());
    settings.setSearchInBackground(myComponent.mySearchInBackground.isSelected());

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



    final Object item = myComponent.myEncodingsCombo.getSelectedItem();
    if (SYSTEM_DEFAULT_ENCODING.equals(item)) {
      EncodingManager.getInstance().setDefaultCharsetName("");
    }
    else if (item != null) {
      EncodingManager.getInstance().setDefaultCharsetName(((Charset)item).name());
    }
  }

  public boolean isModified() {
    boolean isModified = false;
    GeneralSettings settings = GeneralSettings.getInstance();
    isModified |= !Comparing.strEqual(settings.getBrowserPath(), myComponent.myBrowserPathField.getText());
    isModified |= settings.isReopenLastProject() != myComponent.myChkReopenLastProject.isSelected();
    isModified |= settings.isSyncOnFrameActivation() != myComponent.myChkSyncOnFrameActivation.isSelected();
    isModified |= settings.isSaveOnFrameDeactivation() != myComponent.myChkSaveOnFrameDeactivation.isSelected();
    isModified |= settings.isAutoSaveIfInactive() != myComponent.myChkAutoSaveIfInactive.isSelected();
    isModified |= settings.isUseDefaultBrowser() != myComponent.myUseSystemDefaultBrowser.isSelected();
    isModified |= settings.isConfirmExit() != myComponent.myConfirmExit.isSelected();

    int inactiveTimeout = -1;
    try {
      inactiveTimeout = Integer.parseInt(myComponent.myTfInactiveTimeout.getText());
    }
    catch (NumberFormatException e) {
    }
    isModified |= inactiveTimeout > 0 && settings.getInactiveTimeout() != inactiveTimeout;



    isModified |= settings.isSearchInBackground() != myComponent.mySearchInBackground.isSelected();

    isModified |= isEncodingModified();

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

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    myComponent.myBrowserPathField.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null, descriptor);



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
    myComponent.myBrowserPathField.setText(settings.getBrowserPath());
    myComponent.myChkReopenLastProject.setSelected(settings.isReopenLastProject());
    myComponent.myChkSyncOnFrameActivation.setSelected(settings.isSyncOnFrameActivation());
    myComponent.myChkSaveOnFrameDeactivation.setSelected(settings.isSaveOnFrameDeactivation());

    myComponent.myChkAutoSaveIfInactive.setSelected(settings.isAutoSaveIfInactive());
    myComponent.myTfInactiveTimeout.setText(Integer.toString(settings.getInactiveTimeout()));
    myComponent.myTfInactiveTimeout.setEditable(settings.isAutoSaveIfInactive());
    myComponent.myConfirmExit.setSelected(settings.isConfirmExit());

    

    if (settings.isUseDefaultBrowser()) {
      myComponent.myUseSystemDefaultBrowser.setSelected(true);
    }
    else {
      myComponent.myUseUserDefinedBrowser.setSelected(true);
    }
    myComponent.updateBrowserField();

    myComponent.mySearchInBackground.setSelected(settings.isSearchInBackground());

    final DefaultComboBoxModel encodingsModel = new DefaultComboBoxModel(CharsetToolkit.getAvailableCharsets());
    encodingsModel.insertElementAt(SYSTEM_DEFAULT_ENCODING, 0);
    myComponent.myEncodingsCombo.setModel(encodingsModel);

    final String name = EncodingManager.getInstance().getDefaultCharsetName();
    if (StringUtil.isEmpty(name)) {
      myComponent.myEncodingsCombo.setSelectedItem(SYSTEM_DEFAULT_ENCODING);
    }
    else {
      myComponent.myEncodingsCombo.setSelectedItem(EncodingManager.getInstance().getDefaultCharset());
    }
  }

  public void disposeUIResources() {
    myComponent = null;
  }

  @NotNull
  public String getHelpTopic() {
    return "preferences.general";
  }

  public boolean isEncodingModified() {
    final Object item = myComponent.myEncodingsCombo.getSelectedItem();
    if (SYSTEM_DEFAULT_ENCODING.equals(item)) {
      return !StringUtil.isEmpty(EncodingManager.getInstance().getDefaultCharsetName());
    }

    return !Comparing.equal(item, EncodingManager.getInstance().getDefaultCharset());
  }

  private static class MyComponent {
    JPanel myPanel;
    private TextFieldWithBrowseButton myBrowserPathField;

    private JCheckBox myChkReopenLastProject;
    private JCheckBox myChkSyncOnFrameActivation;
    private JCheckBox myChkSaveOnFrameDeactivation;
    private JCheckBox myChkAutoSaveIfInactive;
    private JTextField myTfInactiveTimeout;
    private JRadioButton myUseSystemDefaultBrowser;
    private JRadioButton myUseUserDefinedBrowser;
    public JCheckBox myConfirmExit;
    private JCheckBox mySearchInBackground;
    private JComboBox myEncodingsCombo;

    public MyComponent() {
      if (BrowserUtil.canStartDefaultBrowser()) {
        ActionListener actionListener = new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            updateBrowserField();
          }
        };
        myUseSystemDefaultBrowser.addActionListener(actionListener);
        myUseUserDefinedBrowser.addActionListener(actionListener);
      }
      else {
        myUseSystemDefaultBrowser.setVisible(false);
        myUseUserDefinedBrowser.setVisible(false);
      }
    }

    private void updateBrowserField() {
      if (!BrowserUtil.canStartDefaultBrowser()) {
        return;
      }
      myBrowserPathField.getTextField().setEnabled(myUseUserDefinedBrowser.isSelected());
      myBrowserPathField.getButton().setEnabled(myUseUserDefinedBrowser.isSelected());
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