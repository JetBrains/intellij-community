/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.CommonBundle;
import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.NewColorAndFontPanel;
import com.intellij.application.options.colors.SimpleEditorPreview;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CreateDesktopEntryAction;
import com.intellij.ide.actions.CreateLauncherScriptAction;
import com.intellij.ide.customize.CustomizeDesktopEntryStep;
import com.intellij.ide.customize.CustomizeLauncherScriptStep;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.ui.LafComboBoxRenderer;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.laf.LafManagerImpl;
import com.intellij.ide.ui.laf.darcula.DarculaLookAndFeelInfo;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.DefaultKeymap;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.changes.RefreshablePanel;
import com.intellij.ui.AbstractTitledSeparatorWithIcon;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * @author yole
 */
public class InitialConfigurationDialog extends DialogWrapper {
  private final String myColorSettingsPage;
  private JPanel myMainPanel;
  private JComboBox<Keymap> myKeymapComboBox;
  private JComboBox<UIManager.LookAndFeelInfo> myAppearanceComboBox;
  private JComboBox<EditorColorsScheme> myColorSchemeComboBox;
  private JPanel myColorPreviewPanel;
  private JLabel myPreferencesLabel;
  private JCheckBox myCreateScriptCheckbox;
  private JPanel myCreateScriptPanel;
  private JTextField myScriptPathTextField;
  private JCheckBox myCreateEntryCheckBox;
  private JPanel myCreateEntryPanel;
  private JCheckBox myGlobalEntryCheckBox;

  public InitialConfigurationDialog(Component parent, String colorSettingsPage) {
    super(parent, true);
    myColorSettingsPage = colorSettingsPage;

    setTitle(ApplicationNamesInfo.getInstance().getFullProductName() + " Initial Configuration");
    setResizable(false);
    setCancelButtonText("Skip");
    init();

    Keymap[] allKeymaps = ((KeymapManagerImpl)KeymapManager.getInstance()).getAllKeymaps();
    Keymap[] availableKeymaps = Arrays.stream(allKeymaps).filter(DefaultKeymap::matchesPlatform).toArray(Keymap[]::new);
    UIManager.LookAndFeelInfo[] lookAndFeels = LafManager.getInstance().getInstalledLookAndFeels();
    EditorColorsScheme[] colorSchemes = EditorColorsManager.getInstance().getAllSchemes();

    myKeymapComboBox.setModel(new DefaultComboBoxModel<>(availableKeymaps));
    myKeymapComboBox.setRenderer(new ListCellRendererWrapper<Keymap>() {
      @Override
      public void customize(JList list, Keymap keymap, int index, boolean selected, boolean hasFocus) {
        if (keymap != null) {
          if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(keymap.getName())) {
            setText("IntelliJ IDEA Classic");
          }
          else if ("Mac OS X".equals(keymap.getName())) {
            setText("IntelliJ IDEA Classic - Mac OS X");
          }
          else {
            setText(keymap.getPresentableName());
          }
        }
      }
    });

    myAppearanceComboBox.setModel(new DefaultComboBoxModel<>(lookAndFeels));
    myAppearanceComboBox.setRenderer(new LafComboBoxRenderer());
    myAppearanceComboBox.setSelectedItem(LafManager.getInstance().getCurrentLookAndFeel());
    myAppearanceComboBox.addActionListener(event -> updateColorScheme(colorSchemes));

    myColorSchemeComboBox.setModel(new DefaultComboBoxModel<>(colorSchemes));
    myColorSchemeComboBox.setRenderer(new ListCellRendererWrapper<EditorColorsScheme>() {
      @Override
      public void customize(JList list, EditorColorsScheme value, int index, boolean selected, boolean hasFocus) {
        if (value != null) {
          setText(SchemeManager.getDisplayName(value));
        }
      }
    });
    myColorSchemeComboBox.addActionListener(event -> ((MySeparatorPanel)myColorPreviewPanel).updatePreview());

    myPreferencesLabel.setText("You can use " + CommonBundle.settingsActionPath() + " to configure any of these settings later.");

    boolean canCreateLauncherScript = CustomizeLauncherScriptStep.isAvailable();
    myCreateScriptPanel.setVisible(canCreateLauncherScript);
    myCreateScriptCheckbox.setVisible(canCreateLauncherScript);
    myCreateScriptCheckbox.setSelected(false);
    if (canCreateLauncherScript) {
      myScriptPathTextField.setText(CreateLauncherScriptAction.defaultScriptPath());
      myScriptPathTextField.setEnabled(false);
      myCreateScriptCheckbox.addChangeListener(e -> myScriptPathTextField.setEnabled(myCreateScriptCheckbox.isSelected()));
    }

    boolean canCreateDesktopEntry = CustomizeDesktopEntryStep.isAvailable();
    myCreateEntryPanel.setVisible(canCreateDesktopEntry);
    myCreateEntryCheckBox.setVisible(canCreateDesktopEntry);
    myCreateEntryCheckBox.setSelected(canCreateDesktopEntry);
    myGlobalEntryCheckBox.setSelected(false);
    if (canCreateDesktopEntry) {
      myGlobalEntryCheckBox.setEnabled(true);
      myCreateEntryCheckBox.addChangeListener(e -> myGlobalEntryCheckBox.setEnabled(myCreateEntryCheckBox.isSelected()));
    }

    preselectKeyMap(availableKeymaps);
    updateColorScheme(colorSchemes);

    Disposer.register(myDisposable, () -> ((MySeparatorPanel)myColorPreviewPanel).disposeUIResources());
  }

  private void createUIComponents() {
    myColorPreviewPanel = new MySeparatorPanel(this);
  }

  private void preselectKeyMap(Keymap[] keymaps) {
    Keymap defaultKeymap = KeymapManager.getInstance().getActiveKeymap();
    for (Keymap keymap : keymaps) {
      if (SystemInfo.isMac ? keymap.getName().contains("10.5+") : keymap.equals(defaultKeymap)) {
        myKeymapComboBox.setSelectedItem(keymap);
        break;
      }
    }
  }

  private void updateColorScheme(EditorColorsScheme[] colorsSchemes) {
    UIManager.LookAndFeelInfo selectedLaf = (UIManager.LookAndFeelInfo) myAppearanceComboBox.getSelectedItem();
    if (selectedLaf != null) {
      String matchingScheme = selectedLaf.getName().contains("Darcula") ? "Darcula" : "Default";
      for (EditorColorsScheme colorsScheme : colorsSchemes) {
        if (colorsScheme.getName().contains(matchingScheme)) {
          myColorSchemeComboBox.setSelectedItem(colorsScheme);
          break;
        }
      }
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  protected void doOKAction() {
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myMainPanel));

    super.doOKAction();

    setAppearance(project,
                  (Keymap)myKeymapComboBox.getSelectedItem(),
                  (EditorColorsScheme)myColorSchemeComboBox.getSelectedItem(),
                  (UIManager.LookAndFeelInfo)myAppearanceComboBox.getSelectedItem());

    makeLaunchers(project, getTitle(),
                  myCreateScriptCheckbox.isSelected(), myCreateEntryCheckBox.isSelected(),
                  myScriptPathTextField.getText(), myGlobalEntryCheckBox.isSelected());
  }

  private static void setAppearance(Project project, Keymap keymap, EditorColorsScheme colorScheme, UIManager.LookAndFeelInfo laf) {
    ((KeymapManagerImpl)KeymapManager.getInstance()).setActiveKeymap(keymap);

    EditorColorsManager.getInstance().setGlobalScheme(colorScheme);
    TodoConfiguration.getInstance().resetToDefaultTodoPatterns();

    LafManagerImpl lafManager = (LafManagerImpl)LafManager.getInstance();
    if (laf.getName().contains("Darcula") != (LafManager.getInstance().getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo)) {
      lafManager.setLookAndFeelAfterRestart(laf);
      String message = "IDE appearance settings will be applied after restart. Would you like to restart now?";
      int rc = Messages.showYesNoDialog(project, message, "IDE Appearance", Messages.getQuestionIcon());
      if (rc == Messages.YES) {
        ((ApplicationImpl)ApplicationManager.getApplication()).restart(true);
      }
    }
    else if (!laf.equals(lafManager.getCurrentLookAndFeel())) {
      lafManager.setCurrentLookAndFeel(laf);
      lafManager.updateUI();
    }
  }

  private static void makeLaunchers(Project project,
                                    String title,
                                    boolean createScript,
                                    boolean createEntry,
                                    String pathName,
                                    boolean globalEntry) {
    if (createScript || createEntry) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, title) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setFraction(0.0);

          if (createScript) {
            indicator.setText("Creating launcher script...");
            try {
              CreateLauncherScriptAction.createLauncherScript(pathName);
            }
            catch (Exception e) {
              CreateLauncherScriptAction.reportFailure(e, getProject());
            }
          }

          indicator.setFraction(0.5);

          if (createEntry) {
            indicator.setText("Creating desktop entry...");
            try {
              CreateDesktopEntryAction.createDesktopEntry(globalEntry);
            }
            catch (Exception e) {
              CreateDesktopEntryAction.reportFailure(e, getProject());
            }
          }

          indicator.setFraction(1.0);
        }
      });
    }
  }

  private static class MySeparatorPanel extends AbstractTitledSeparatorWithIcon {
    private static final String SHOW_TEXT = "Click to preview";
    private static final String HIDE_TEXT = "Click to hide preview";

    private final InitialConfigurationDialog myDialog;
    private int myAddedWidth;

    public MySeparatorPanel(InitialConfigurationDialog dialog) {
      super(AllIcons.General.ComboArrowRight, AllIcons.General.ComboArrowDown, SHOW_TEXT);
      myDialog = dialog;
    }

    @Override
    protected RefreshablePanel createPanel() {
      return new MyColorPreviewPanel(myDialog, myWrapper);
    }

    @Override
    protected void initOnImpl() { }

    @Override
    protected void onImpl() {
      myWrapper.setVisible(true);
      setText(HIDE_TEXT);
      initDetails();
      myLabel.setIcon(myIconOpen);
      myOn = true;
      revalidate();
      myAddedWidth = getPreferredSize().width - getSize().width;
      ((MyColorPreviewPanel)myDetailsComponent).inflate(myAddedWidth);
    }

    @Override
    protected void offImpl() {
      myLabel.setIcon(myIcon);
      setText(SHOW_TEXT);
      ((MyColorPreviewPanel)myDetailsComponent).deflate(myAddedWidth);
      myWrapper.removeAll();
      myWrapper.setVisible(false);
      myOn = false;
    }

    private void updatePreview() {
      RefreshablePanel previewPanel = myDetailsComponent;
      if (previewPanel != null) {
        ((MyColorPreviewPanel)previewPanel).updateColorSchemePreview(true);
      }
    }

    private void disposeUIResources() {
      RefreshablePanel previewPanel = myDetailsComponent;
      if (previewPanel != null) {
        ((MyColorPreviewPanel)previewPanel).disposeUIResources();
      }
    }
  }

  private static class MyColorPreviewPanel extends JPanel implements RefreshablePanel {
    private final InitialConfigurationDialog myDialog;
    private final JPanel myWrapper;
    private SimpleEditorPreview myPreviewEditor;
    private ColorAndFontOptions myPreviewOptions;
    private NewColorAndFontPanel myColorAndFontPanel;

    public MyColorPreviewPanel(InitialConfigurationDialog dialog, JPanel wrapper) {
      super(new BorderLayout());
      myDialog = dialog;
      myWrapper = wrapper;
      updateColorSchemePreview(false);
    }

    @Override
    public void refresh() {
      updateColorSchemePreview(false);
    }

    @Override
    public JPanel getPanel() {
      return (JPanel)myPreviewEditor.getPanel();
    }

    private void updateColorSchemePreview(boolean recalculateDialogSize) {
      if (!myWrapper.isVisible()) return;

      int wrapperHeight = 0;
      if (myPreviewEditor != null) {
        wrapperHeight = getPreviewPreferredHeight();
        myPreviewEditor.disposeUIResources();
        myWrapper.removeAll();
      }
      if (myPreviewOptions == null) {
        myPreviewOptions = new ColorAndFontOptions();
      }
      myPreviewOptions.reset();
      EditorColorsScheme scheme = (EditorColorsScheme)myDialog.myColorSchemeComboBox.getSelectedItem();
      assert scheme != null;
      myPreviewOptions.selectScheme(scheme.getName());
      myColorAndFontPanel = myPreviewOptions.findPage(myDialog.myColorSettingsPage);
      assert myColorAndFontPanel != null : myDialog.myColorSettingsPage;
      myPreviewEditor = new SimpleEditorPreview(myPreviewOptions, myColorAndFontPanel.getSettingsPage(), false);
      myPreviewEditor.updateView();
      myWrapper.add(myPreviewEditor.getPanel());
      if (recalculateDialogSize) {
        myDialog.resize(0, getPreviewPreferredHeight() - wrapperHeight);
      }
    }

    private void disposeUIResources() {
      if (myPreviewEditor != null) myPreviewEditor.disposeUIResources();
      if (myPreviewOptions != null) myPreviewOptions.disposeUIResources();
      if (myColorAndFontPanel != null) myColorAndFontPanel.disposeUIResources();
    }

    private int getPreviewPreferredHeight() {
      return myPreviewEditor.getPanel().getPreferredSize().height / 2;
    }

    private void inflate(int dW) {
      myDialog.resize(dW, getPreviewPreferredHeight());
    }

    private void deflate(int dW) {
      myDialog.resize(-dW, -getPreviewPreferredHeight());
    }
  }

  private void resize(int dW, int dH) {
    Dimension size = getSize();
    setSize(size.width + dW, size.height + dH);
    getRootPane().revalidate();
    getRootPane().repaint();
  }
}