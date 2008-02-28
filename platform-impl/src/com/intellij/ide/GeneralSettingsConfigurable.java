package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.impl.external.DiffOptionsForm;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.SortableConfigurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.net.HTTPProxySettingsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GeneralSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable, SortableConfigurable {
  private DiffOptionsForm myDiffOptions;
  private MyComponent myComponent;

  public void apply() {
    GeneralSettings settings = GeneralSettings.getInstance();

    settings.setBrowserPath(myComponent.myBrowserPathField.getText());
    settings.setReopenLastProject(myComponent.myChkReopenLastProject.isSelected());
    settings.setSyncOnFrameActivation(myComponent.myChkSyncOnFrameActivation.isSelected());
    settings.setSaveOnFrameDeactivation(myComponent.myChkSaveOnFrameDeactivation.isSelected());
    settings.setUseDefaultBrowser(myComponent.myUseSystemDefaultBrowser.isSelected());
    settings.setUseCyclicBuffer(myComponent.myUseCyclicBuffer.isSelected());
    settings.setConfirmExit(myComponent.myConfirmExit.isSelected());
    settings.setSearchInBackground(myComponent.mySearchInBackground.isSelected());

    try {
      settings.setCyclicBufferSize(Integer.parseInt(myComponent.myCyclicBufferSize.getText()) * 1024);
    }
    catch (NumberFormatException e) {
      settings.setCyclicBufferSize(0);
    }

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

    //

    if (!FileTypeManagerEx.getInstanceEx().isIgnoredFilesListEqualToCurrent(myComponent.myIgnoreFilesField.getText())) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          FileTypeManagerEx.getInstanceEx().setIgnoredFilesList(myComponent.myIgnoreFilesField.getText());
        }
      });
    }
    getDiffOptions().apply();

    myComponent.myHTTPProxySettingsEditor.apply();
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
    isModified |= settings.isUseCyclicBuffer() != myComponent.myUseCyclicBuffer.isSelected();
    isModified |= settings.isConfirmExit() != myComponent.myConfirmExit.isSelected();
    isModified |= !Comparing.strEqual(settings.getCyclicBufferSize() / 1024 + "", myComponent.myCyclicBufferSize.getText());

    int inactiveTimeout = -1;
    try {
      inactiveTimeout = Integer.parseInt(myComponent.myTfInactiveTimeout.getText());
    }
    catch (NumberFormatException e) {
    }
    isModified |= inactiveTimeout > 0 && settings.getInactiveTimeout() != inactiveTimeout;

    isModified |= !FileTypeManagerEx.getInstanceEx().isIgnoredFilesListEqualToCurrent(myComponent.myIgnoreFilesField.getText());

    isModified |= myComponent.myHTTPProxySettingsEditor.isModified();

    isModified |= settings.isSearchInBackground() != myComponent.mySearchInBackground.isSelected();

    return isModified || getDiffOptions().isModified();
  }

  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  public JComponent createComponent() {

//    optionGroup.add(getDiffOptions().getPanel());
    myComponent = new MyComponent();
    myComponent.myDiffOptionsPanel.setLayout(new BorderLayout());
    myComponent.myDiffOptionsPanel.add(getDiffOptions().createComponent(), BorderLayout.CENTER);

    // AutoSave if inactive

    myComponent.myChkAutoSaveIfInactive.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myComponent.myTfInactiveTimeout.setEditable(myComponent.myChkAutoSaveIfInactive.isSelected());
      }
    });
    myComponent.myUseCyclicBuffer.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myComponent.myCyclicBufferSize.setEditable(myComponent.myUseCyclicBuffer.isSelected());
      }
    });

    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
    myComponent.myBrowserPathField.addBrowseFolderListener(IdeBundle.message("title.select.path.to.browser"), null, null, descriptor);

    myComponent.myIgnoreFilesField.setText("##### ##############");

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
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    myComponent.myBrowserPathField.setText(settings.getBrowserPath());
    myComponent.myChkReopenLastProject.setSelected(settings.isReopenLastProject());
    myComponent.myChkSyncOnFrameActivation.setSelected(settings.isSyncOnFrameActivation());
    myComponent.myChkSaveOnFrameDeactivation.setSelected(settings.isSaveOnFrameDeactivation());

    myComponent.myChkAutoSaveIfInactive.setSelected(settings.isAutoSaveIfInactive());
    myComponent.myTfInactiveTimeout.setText(Integer.toString(settings.getInactiveTimeout()));
    myComponent.myTfInactiveTimeout.setEditable(settings.isAutoSaveIfInactive());
    myComponent.myUseCyclicBuffer.setSelected(settings.isUseCyclicBuffer());
    myComponent.myCyclicBufferSize.setEditable(settings.isUseCyclicBuffer());
    myComponent.myConfirmExit.setSelected(settings.isConfirmExit());
    myComponent.myCyclicBufferSize.setText(settings.getCyclicBufferSize() / 1024 + "");

    myComponent.myIgnoreFilesField.setText(FileTypeManagerEx.getInstanceEx().getIgnoredFilesList());
    getDiffOptions().reset();

    if (settings.isUseDefaultBrowser()) {
      myComponent.myUseSystemDefaultBrowser.setSelected(true);
    }
    else {
      myComponent.myUseUserDefinedBrowser.setSelected(true);
    }
    myComponent.updateBrowserField();
    myComponent.myHTTPProxySettingsEditor.reset();

    myComponent.mySearchInBackground.setSelected(settings.isSearchInBackground());
  }

  public void disposeUIResources() {
    myComponent = null;
    myDiffOptions = null;
  }

  @NotNull
  public String getHelpTopic() {
    return "preferences.general";
  }

  public int getSortWeight() {
    return 0;
  }

  private static class MyComponent {
    JPanel myPanel;
    private TextFieldWithBrowseButton myBrowserPathField;
    private JTextField myIgnoreFilesField;
    private JCheckBox myChkReopenLastProject;
    private JCheckBox myChkSyncOnFrameActivation;
    private JCheckBox myChkSaveOnFrameDeactivation;
    private JCheckBox myChkAutoSaveIfInactive;
    private JTextField myTfInactiveTimeout;
    private JPanel myDiffOptionsPanel;
    private JRadioButton myUseSystemDefaultBrowser;
    private JRadioButton myUseUserDefinedBrowser;
    private JCheckBox myUseCyclicBuffer;
    private JTextField myCyclicBufferSize;
    public JCheckBox myConfirmExit;
    private JPanel myHTTPProxyPanel;

    private final HTTPProxySettingsPanel myHTTPProxySettingsEditor;
    private JCheckBox mySearchInBackground;

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

      myHTTPProxySettingsEditor = new HTTPProxySettingsPanel();
      myHTTPProxyPanel.setLayout(new BorderLayout());
      myHTTPProxyPanel.add(myHTTPProxySettingsEditor, BorderLayout.WEST);
    }

    private void updateBrowserField() {
      if (!BrowserUtil.canStartDefaultBrowser()) {
        return;
      }
      myBrowserPathField.getTextField().setEnabled(myUseUserDefinedBrowser.isSelected());
      myBrowserPathField.getButton().setEnabled(myUseUserDefinedBrowser.isSelected());
    }
  }

  private DiffOptionsForm getDiffOptions() {
    if (myDiffOptions == null) myDiffOptions = new DiffOptionsForm();
    return myDiffOptions;
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}