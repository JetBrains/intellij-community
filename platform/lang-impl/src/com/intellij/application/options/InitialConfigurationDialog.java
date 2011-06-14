package com.intellij.application.options;

import com.intellij.application.options.colors.ColorAndFontOptions;
import com.intellij.application.options.colors.NewColorAndFontPanel;
import com.intellij.application.options.colors.SimpleEditorPreview;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.CreateLauncherScriptAction;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.ide.ui.ListCellRendererWrapper;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.SystemInfo;

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
  private String myColorSettingsPage;
  private SimpleEditorPreview myPreviewEditor;
  private ColorAndFontOptions myPreviewOptions;

  public InitialConfigurationDialog(Component parent, String colorSettingsPage) {
    super(parent, true);
    myColorSettingsPage = colorSettingsPage;
    setTitle(ApplicationNamesInfo.getInstance().getFullProductName() + " Initial Configuration");

    final ArrayList<Keymap> keymaps = new ArrayList<Keymap>();
    for (Keymap keymap : ((KeymapManagerImpl)KeymapManager.getInstance()).getAllKeymaps()) {
      if (matchesPlatform(keymap)) {
        keymaps.add(keymap);
      }
    }

    myKeymapComboBox.setModel(new DefaultComboBoxModel(keymaps.toArray(new Keymap[keymaps.size()])));
    myKeymapComboBox.setRenderer(new ListCellRendererWrapper(myKeymapComboBox.getRenderer()) {
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
    myColorSchemeComboBox.setRenderer(new ListCellRendererWrapper(myColorSchemeComboBox.getRenderer()) {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean cellHasFocus) {
        setText(((EditorColorsScheme)value).getName());
      }
    });
    myColorSchemeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        updateColorSchemePreview();
      }
    });
    updateColorSchemePreview();
    init();

    final boolean canCreateLauncherScript = SystemInfo.isMac || SystemInfo.isLinux;
    myCreateScriptCheckbox.setVisible(canCreateLauncherScript);
    myCreateScriptCheckbox.setSelected(canCreateLauncherScript);
    myCreateScriptPanel.setVisible(canCreateLauncherScript);
    if (canCreateLauncherScript) {
      myScriptPathTextField.setText("/usr/local/bin/" + CreateLauncherScriptAction.defaultScriptName());
    }
  }

  private void preselectKeyMap(ArrayList<Keymap> keymaps) {
    if (SystemInfo.isMac) {
      for (Keymap keymap : keymaps) {
        if (keymap.getName().equals("Default for Mac OS X")) {
          myKeymapComboBox.setSelectedItem(keymap);
          break;
        }
      }
    }
  }

  private static boolean matchesPlatform(Keymap keymap) {
    if (keymap.getName().equals(KeymapManager.DEFAULT_IDEA_KEYMAP)) {
      return !SystemInfo.isMac;
    }
    else if (keymap.getName().equals(KeymapManager.MAC_OS_X_KEYMAP)) {
      return SystemInfo.isMac;
    }
    else if (keymap.getName().equals("Default for GNOME") || keymap.getName().equals("Default for KDE") ||
             keymap.getName().equals("Default for XWin")) {
      return SystemInfo.isLinux;
    }
    return true;
  }

  private void updateColorSchemePreview() {
    if (myPreviewEditor != null) {
      myColorPreviewPanel.remove(myPreviewEditor.getPanel());
      myPreviewEditor.disposeUIResources();
    }
    myPreviewOptions = ColorAndFontOptions.getColorAndFontsInstance();
    myPreviewOptions.reset();
    myPreviewOptions.selectScheme(((EditorColorsScheme)myColorSchemeComboBox.getSelectedItem()).getName());
    final NewColorAndFontPanel page = myPreviewOptions.findPage(myColorSettingsPage);
    assert page != null;
    myPreviewEditor = new SimpleEditorPreview(myPreviewOptions, page.getSettingsPage());
    myPreviewEditor.updateView();
    myColorPreviewPanel.add(myPreviewEditor.getPanel());
    myColorPreviewPanel.revalidate();
    myColorPreviewPanel.repaint();
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  protected void doOKAction() {
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myMainPanel));

    super.doOKAction();

    myPreviewEditor.disposeUIResources();
    myPreviewOptions.disposeUIResources();
    // set keymap
    ((KeymapManagerImpl)KeymapManager.getInstance()).setActiveKeymap((Keymap)myKeymapComboBox.getSelectedItem());
    // set color scheme
    EditorColorsManager.getInstance().setGlobalScheme((EditorColorsScheme)myColorSchemeComboBox.getSelectedItem());
    // create default todo_pattern for color scheme
    TodoConfiguration.getInstance().resetToDefaultTodoPatterns();

    if (myCreateScriptCheckbox.isSelected()) {
      final String pathname = myScriptPathTextField.getText();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          CreateLauncherScriptAction.createLauncherScript(project, pathname);
        }
      });
    }
  }

  @Override
  public void doCancelAction() {
    myPreviewEditor.disposeUIResources();
    myPreviewOptions.disposeUIResources();
    super.doCancelAction();
  }
}
