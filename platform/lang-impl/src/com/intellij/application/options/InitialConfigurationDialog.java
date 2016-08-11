/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

/**
 * @author yole
 */
public class InitialConfigurationDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JComboBox myKeymapComboBox;
  private JComboBox myColorSchemeComboBox;
  private JCheckBox myCreateScriptCheckbox;
  private JTextField myScriptPathTextField;
  private JPanel myCreateScriptPanel;
  private JPanel myColorPreviewPanel;
  private JPanel myHeaderPanel;
  private JPanel myFooterPanel;
  private JPanel myExtraOptionsPanel;
  private JCheckBox myCreateEntryCheckBox;
  private JCheckBox myGlobalEntryCheckBox;
  private JPanel myCreateEntryPanel;
  private ComboBox myAppearanceComboBox;
  private JLabel myPreferencesLabel;
  private final String myColorSettingsPage;
  private SimpleEditorPreview myPreviewEditor;
  private ColorAndFontOptions myPreviewOptions;
  private MyColorPreviewPanel myHidingPreviewPanel;
  private NewColorAndFontPanel myColorAndFontPanel;

  public InitialConfigurationDialog(Component parent, String colorSettingsPage) {
    super(parent, true);
    myColorSettingsPage = colorSettingsPage;
    setTitle(ApplicationNamesInfo.getInstance().getFullProductName() + " Initial Configuration");

    final ArrayList<Keymap> keymaps = new ArrayList<>();
    for (Keymap keymap : ((KeymapManagerImpl)KeymapManager.getInstance()).getAllKeymaps()) {
      if (matchesPlatform(keymap)) {
        keymaps.add(keymap);
      }
    }

    myAppearanceComboBox.setModel(new DefaultComboBoxModel(LafManager.getInstance().getInstalledLookAndFeels()));
    myAppearanceComboBox.setRenderer(new LafComboBoxRenderer());
    myAppearanceComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        preselectColorScheme();
      }
    });
    myAppearanceComboBox.setSelectedItem(LafManager.getInstance().getCurrentLookAndFeel());
    myColorSchemeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        EditorColorsScheme scheme = (EditorColorsScheme)myColorSchemeComboBox.getSelectedItem();
        if (scheme.getName().equals("Darcula")) {
          UIManager.LookAndFeelInfo[] lafs = LafManager.getInstance().getInstalledLookAndFeels();
          for (UIManager.LookAndFeelInfo laf : lafs) {
            if (laf.getName().contains("Darcula")) {
              myAppearanceComboBox.setSelectedItem(laf);
              break;
            }
          }
        }
      }
    });

    myKeymapComboBox.setModel(new DefaultComboBoxModel(keymaps.toArray(new Keymap[keymaps.size()])));
    myKeymapComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(final JList list, final Object value, final int index, final boolean selected, final boolean cellHasFocus) {
        Keymap keymap = (Keymap)value;
        if (keymap == null) {
          return;
        }
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
    });
    preselectKeyMap(keymaps);

    final EditorColorsScheme[] colorSchemes = EditorColorsManager.getInstance().getAllSchemes();
    myColorSchemeComboBox.setModel(new DefaultComboBoxModel(colorSchemes));
    myColorSchemeComboBox.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean cellHasFocus) {
        if (value != null) {
          setText(((EditorColorsScheme)value).getName());
        }
      }
    });
    myColorSchemeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        if (myHidingPreviewPanel != null) myHidingPreviewPanel.updateColorSchemePreview(true);
      }
    });
    preselectColorScheme();
    setResizable(false);
    setCancelButtonText("Skip");
    init();

    final boolean canCreateLauncherScript = canCreateLauncherScript();
    myCreateScriptPanel.setVisible(canCreateLauncherScript);
    myCreateScriptCheckbox.setVisible(canCreateLauncherScript);
    myCreateScriptCheckbox.setSelected(false);
    if (canCreateLauncherScript) {
      myScriptPathTextField.setText(CreateLauncherScriptAction.defaultScriptPath());
      myScriptPathTextField.setEnabled(false);
      myCreateScriptCheckbox.addChangeListener(e -> myScriptPathTextField.setEnabled(myCreateScriptCheckbox.isSelected()));
    }

    final boolean canCreateDesktopEntry = canCreateDesktopEntry();
    myCreateEntryPanel.setVisible(canCreateDesktopEntry);
    myCreateEntryCheckBox.setVisible(canCreateDesktopEntry);
    myCreateEntryCheckBox.setSelected(canCreateDesktopEntry);
    myGlobalEntryCheckBox.setSelected(false);
    if (canCreateDesktopEntry) {
      myGlobalEntryCheckBox.setEnabled(true);
      myCreateEntryCheckBox.addChangeListener(e -> myGlobalEntryCheckBox.setEnabled(myCreateEntryCheckBox.isSelected()));
    }

    myPreferencesLabel.setText("You can use " + CommonBundle.settingsActionPath() + " to configure any of these settings later.");

    Disposer.register(myDisposable, new Disposable() {
      @Override
      public void dispose() {
        disposeUIResources();
      }
    });
  }

  private void preselectColorScheme() {
    UIManager.LookAndFeelInfo selectedLaf = (UIManager.LookAndFeelInfo) myAppearanceComboBox.getSelectedItem();
    if (!selectedLaf.getName().contains("Darcula")) {
      myColorSchemeComboBox.setSelectedItem(EditorColorsManager.getInstance().getScheme("Default"));
    }
  }

  private void disposeUIResources() {
    if (myPreviewEditor != null) {
      myPreviewEditor.disposeUIResources();
    }
    if (myPreviewOptions != null) {
      myPreviewOptions.disposeUIResources();
    }
    if (myColorAndFontPanel != null) {
      myColorAndFontPanel.disposeUIResources();
    }
  }

  protected boolean canCreateDesktopEntry() {
    return CustomizeDesktopEntryStep.isAvailable();
  }

  protected boolean canCreateLauncherScript() {
    return CustomizeLauncherScriptStep.isAvailable();
  }

  public JComboBox getKeymapComboBox() {
    return myKeymapComboBox;
  }

  public JComboBox getColorSchemeComboBox() {
    return myColorSchemeComboBox;
  }

  public ComboBox getAppearanceComboBox() {
    return myAppearanceComboBox;
  }

  private void preselectKeyMap(ArrayList<Keymap> keymaps) {
    final Keymap defaultKeymap = KeymapManager.getInstance().getActiveKeymap();
    for (Keymap keymap : keymaps) {
      if (SystemInfo.isMac ? keymap.getName().contains("10.5+") : keymap.equals(defaultKeymap)) {
        myKeymapComboBox.setSelectedItem(keymap);
        break;
      }
    }
  }

  private void createUIComponents() {
    myHeaderPanel = createHeaderPanel();
    myFooterPanel = createFooterPanel();
    myExtraOptionsPanel = createExtraOptionsPanel();
    myColorPreviewPanel = new AbstractTitledSeparatorWithIcon(AllIcons.General.ComboArrowRight,
                                                              AllIcons.General.ComboArrowDown,
                                                              "Click to preview") {

      private int myAddedWidth;

      @Override
      protected RefreshablePanel createPanel() {
        myHidingPreviewPanel = new MyColorPreviewPanel(myWrapper);
        return myHidingPreviewPanel;
      }

      @Override
      protected void initOnImpl() {
        //?
      }

      @Override
      protected void onImpl() {
        myWrapper.setVisible(true);
        setText("Click to hide preview");
        initDetails();
        myLabel.setIcon(myIconOpen);
        myOn = true;
        final InitialConfigurationDialog dialog = InitialConfigurationDialog.this;
        revalidate();
        myAddedWidth = getPreferredSize().width - getSize().width;
        resizeTo(dialog.getSize().width + myAddedWidth, dialog.getSize().height + getPreviewPreferredHeight());
      }

      @Override
      protected void offImpl() {
        myLabel.setIcon(myIcon);
        setText("Click to preview");
        final InitialConfigurationDialog dialog = InitialConfigurationDialog.this;
        resizeTo(dialog.getSize().width - myAddedWidth, dialog.getSize().height - getPreviewPreferredHeight());
        myWrapper.removeAll();
        myWrapper.setVisible(false);
        myOn = false;
      }
    };
  }

  private int getPreviewPreferredHeight() {
    return myPreviewEditor.getPanel().getPreferredSize().height / 2;
  }

  protected JPanel createFooterPanel() {
    final JPanel panel = new JPanel();
    panel.setVisible(false);
    return panel;
  }

  protected JPanel createHeaderPanel() {
    final JPanel panel = new JPanel();
    panel.setVisible(false);
    return panel;
  }

  protected JPanel createExtraOptionsPanel() {
    final JPanel panel = new JPanel();
    panel.setVisible(false);
    return panel;
  }

  private void resizeTo(final int newWidth, final int newHeight) {
    setSize(newWidth, newHeight);
    getRootPane().revalidate();
    getRootPane().repaint();
  }

  private class MyColorPreviewPanel extends JPanel implements RefreshablePanel {
    private final JPanel myWrapper;

    public MyColorPreviewPanel(JPanel wrapper) {
      super(new BorderLayout());
      myWrapper = wrapper;
      updateColorSchemePreview(false);
    }

    @Override
    public boolean refreshDataSynch() {
      return false;
    }

    @Override
    public void dataChanged() {}

    @Override
    public boolean isStillValid(Object o) {
      return false;
    }

    @Override
    public void refresh() {
      updateColorSchemePreview(false);
    }

    @Override
    public JPanel getPanel() {
      return (JPanel)myPreviewEditor.getPanel();
    }

    @Override
    public void away() {}

    @Override
    public void dispose() {
      disposeUIResources();
    }

    public void updateColorSchemePreview(final boolean recalculateDialogSize) {
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
      myPreviewOptions.selectScheme(((EditorColorsScheme)myColorSchemeComboBox.getSelectedItem()).getName());
      myColorAndFontPanel = myPreviewOptions.findPage(myColorSettingsPage);
      assert myColorAndFontPanel != null;
      myPreviewEditor = new SimpleEditorPreview(myPreviewOptions, myColorAndFontPanel.getSettingsPage(), false);
      myPreviewEditor.updateView();
      myWrapper.add(myPreviewEditor.getPanel(), BorderLayout.EAST);
      if (recalculateDialogSize) {
        final InitialConfigurationDialog dialog = InitialConfigurationDialog.this;
        resizeTo(dialog.getSize().width, dialog.getSize().height - wrapperHeight + getPreviewPreferredHeight());
      }
    }
  }

  private static boolean matchesPlatform(Keymap keymap) {
    final String name = keymap.getName();
    if (KeymapManager.DEFAULT_IDEA_KEYMAP.equals(name)) {
      return SystemInfo.isWindows;
    }
    else if (KeymapManager.MAC_OS_X_KEYMAP.equals(name) || KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP.equals(name)) {
      return SystemInfo.isMac;
    }
    else if (KeymapManager.X_WINDOW_KEYMAP.equals(name) || "Default for GNOME".equals(name) || KeymapManager.KDE_KEYMAP.equals(name)) {
      return SystemInfo.isXWindow;
    }
    return true;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  protected void doOKAction() {
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myMainPanel));

    super.doOKAction();

    // set keymap
    ((KeymapManagerImpl)KeymapManager.getInstance()).setActiveKeymap((Keymap)myKeymapComboBox.getSelectedItem());
    // set color scheme
    EditorColorsManager.getInstance().setGlobalScheme((EditorColorsScheme)myColorSchemeComboBox.getSelectedItem());
    // create default todo_pattern for color scheme
    TodoConfiguration.getInstance().resetToDefaultTodoPatterns();

    final boolean createScript = myCreateScriptCheckbox.isSelected();
    final boolean createEntry = myCreateEntryCheckBox.isSelected();
    if (createScript || createEntry) {
      final String pathName = myScriptPathTextField.getText();
      final boolean globalEntry = myGlobalEntryCheckBox.isSelected();
      ProgressManager.getInstance().run(new Task.Backgroundable(project, getTitle()) {
        @Override
        public void run(@NotNull final ProgressIndicator indicator) {
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

    UIManager.LookAndFeelInfo info = (UIManager.LookAndFeelInfo) myAppearanceComboBox.getSelectedItem();
    LafManagerImpl lafManager = (LafManagerImpl)LafManager.getInstance();
    if (info.getName().contains("Darcula") != (LafManager.getInstance().getCurrentLookAndFeel() instanceof DarculaLookAndFeelInfo)) {
      lafManager.setLookAndFeelAfterRestart(info);
      String message = "IDE appearance settings will be applied after restart. Would you like to restart now?";
      int rc = Messages.showYesNoDialog(project, message, "IDE Appearance", Messages.getQuestionIcon());
      if (rc == Messages.YES) {
        ((ApplicationImpl) ApplicationManager.getApplication()).restart(true);
      }
    }
    else if (!info.equals(lafManager.getCurrentLookAndFeel())) {
      lafManager.setCurrentLookAndFeel(info);
      lafManager.updateUI();
    }
  }

  @Override
  public void doCancelAction() {
    super.doCancelAction();
  }
}
