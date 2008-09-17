package com.intellij.application.options.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;
import java.awt.*;

public class EditorOptionsPanel {
  private JPanel myBehaviourPanel;
  private JCheckBox myCbHighlightBraces;
  private static final String STRIP_CHANGED = ApplicationBundle.message("combobox.strip.modified.lines");

  private static final String STRIP_ALL = ApplicationBundle.message("combobox.strip.all");
  private static final String STRIP_NONE = ApplicationBundle.message("combobox.strip.none");
  private JComboBox myStripTrailingSpacesCombo;

  private JCheckBox myCbVirtualSpace;
  private JCheckBox myCbCaretInsideTabs;

  private JTextField myRecentFilesLimitField;

  private JCheckBox myCbHighlightScope;

  private JTextField myClipboardContentLimitTextField;
  private JCheckBox myCbSmoothScrolling;
  private JCheckBox myCbVirtualPageAtBottom;
  private JCheckBox myCbEnableDnD;
  private JCheckBox myCbEnableWheelFontChange;
  private JCheckBox myCbHonorCamelHumpsWhenSelectingByClicking;

  private JPanel myHighlightSettingsPanel;
  private JRadioButton myRbPreferScrolling;
  private JRadioButton myRbPreferMovingCaret;
  private ErrorHighlightingPanel myErrorHighlightingPanel = new ErrorHighlightingPanel();

  private TabbedPaneWrapper myTabbedPaneWrapper;

  public JComponent getTabbedPanel() {
    return myTabbedPaneWrapper.getComponent();
  }

  public EditorOptionsPanel(){
    if (SystemInfo.isMac) {
      myCbEnableWheelFontChange.setText(ApplicationBundle.message("checkbox.enable.ctrl.mousewheel.changes.font.size.macos"));
    }


    myStripTrailingSpacesCombo.addItem(STRIP_CHANGED);
    myStripTrailingSpacesCombo.addItem(STRIP_ALL);
    myStripTrailingSpacesCombo.addItem(STRIP_NONE);


    myHighlightSettingsPanel.setLayout(new BorderLayout());
    myHighlightSettingsPanel.add(myErrorHighlightingPanel.getPanel(), BorderLayout.CENTER);
    myTabbedPaneWrapper = new TabbedPaneWrapper();
    myTabbedPaneWrapper.addTab(ApplicationBundle.message("tab.editor.settings.behavior"), myBehaviourPanel);
    for (EditorOptionsProvider provider : Extensions.getExtensions(EditorOptionsProvider.EP_NAME)) {
      myTabbedPaneWrapper.addTab(provider.getDisplayName(), provider.createComponent());
    }
  }



  public void reset() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    UISettings uiSettings=UISettings.getInstance();

    // Display


    myCbSmoothScrolling.setSelected(editorSettings.isSmoothScrolling());

    // Brace highlighting

    myCbHighlightBraces.setSelected(codeInsightSettings.HIGHLIGHT_BRACES);
    myCbHighlightScope.setSelected(codeInsightSettings.HIGHLIGHT_SCOPE);

    // Virtual space

    myCbVirtualSpace.setSelected(editorSettings.isVirtualSpace());
    myCbCaretInsideTabs.setSelected(editorSettings.isCaretInsideTabs());
    myCbVirtualPageAtBottom.setSelected(editorSettings.isAdditionalPageAtBottom());

    // Limits
    myClipboardContentLimitTextField.setText(Integer.toString(uiSettings.MAX_CLIPBOARD_CONTENTS));

    // Strip trailing spaces on save

    String stripTrailingSpaces = editorSettings.getStripTrailingSpaces();
    if(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE.equals(stripTrailingSpaces)) {
      myStripTrailingSpacesCombo.setSelectedItem(STRIP_NONE);
    }
    else if (EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED.equals(stripTrailingSpaces)) {
      myStripTrailingSpacesCombo.setSelectedItem(STRIP_CHANGED);
    }
    else if (EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE.equals(stripTrailingSpaces)) {
      myStripTrailingSpacesCombo.setSelectedItem(STRIP_ALL);
    }

    // Advanced mouse
    myCbEnableDnD.setSelected(editorSettings.isDndEnabled());
    myCbEnableWheelFontChange.setSelected(editorSettings.isWheelFontChangeEnabled());
    myCbHonorCamelHumpsWhenSelectingByClicking.setSelected(editorSettings.isMouseClickSelectionHonorsCamelWords());

    myRbPreferMovingCaret.setSelected(editorSettings.isRefrainFromScrolling());
    myRbPreferScrolling.setSelected(!editorSettings.isRefrainFromScrolling());


    myRecentFilesLimitField.setText(Integer.toString(uiSettings.RECENT_FILES_LIMIT));
    myErrorHighlightingPanel.reset();
    for (EditorOptionsProvider provider : Extensions.getExtensions(EditorOptionsProvider.EP_NAME)) {
      provider.reset();
    }
  }

  public void apply() throws ConfigurationException {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    UISettings uiSettings=UISettings.getInstance();

    // Display

    editorSettings.setSmoothScrolling(myCbSmoothScrolling.isSelected());


    // Brace Highlighting

    codeInsightSettings.HIGHLIGHT_BRACES = myCbHighlightBraces.isSelected();
    codeInsightSettings.HIGHLIGHT_SCOPE = myCbHighlightScope.isSelected();

    // Virtual space

    editorSettings.setVirtualSpace(myCbVirtualSpace.isSelected());
    editorSettings.setCaretInsideTabs(myCbCaretInsideTabs.isSelected());
    editorSettings.setAdditionalPageAtBottom(myCbVirtualPageAtBottom.isSelected());

    // Limits



    boolean uiSettingsChanged = false;
    int maxClipboardContents = getMaxClipboardContents();
    if (uiSettings.MAX_CLIPBOARD_CONTENTS != maxClipboardContents) {
      uiSettings.MAX_CLIPBOARD_CONTENTS = maxClipboardContents;
      uiSettingsChanged = true;
    }

    if(uiSettingsChanged){
      uiSettings.fireUISettingsChanged();
    }

    // Strip trailing spaces on save

    if(STRIP_NONE.equals(myStripTrailingSpacesCombo.getSelectedItem())) {
      editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
    }
    else if(STRIP_CHANGED.equals(myStripTrailingSpacesCombo.getSelectedItem())){
      editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED);
    }
    else {
      editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
    }

    editorSettings.setDndEnabled(myCbEnableDnD.isSelected());

    editorSettings.setWheelFontChangeEnabled(myCbEnableWheelFontChange.isSelected());
    editorSettings.setMouseClickSelectionHonorsCamelWords(myCbHonorCamelHumpsWhenSelectingByClicking.isSelected());
    editorSettings.setRefrainFromScrolling(myRbPreferMovingCaret.isSelected());

    Editor[] editors = EditorFactory.getInstance().getAllEditors();
    for (Editor editor : editors) {
      ((EditorEx)editor).reinitSettings();
    }

    String temp=myRecentFilesLimitField.getText();
    if(temp.trim().length() > 0){
      try {
        int newRecentFilesLimit= Integer.parseInt(temp);
        if(newRecentFilesLimit>0&&uiSettings.RECENT_FILES_LIMIT!=newRecentFilesLimit){
          uiSettings.RECENT_FILES_LIMIT=newRecentFilesLimit;
          uiSettingsChanged = true;
        }
      }catch (NumberFormatException ignored){}
    }
    if(uiSettingsChanged){
      uiSettings.fireUISettingsChanged();
    }
    myErrorHighlightingPanel.apply();
    for (EditorOptionsProvider provider : Extensions.getExtensions(EditorOptionsProvider.EP_NAME)) {
      provider.apply();
    }
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      DaemonCodeAnalyzer.getInstance(project).settingsChanged();
    }
  }

  private int getMaxClipboardContents(){
    int maxClipboardContents = -1;
    try {
      maxClipboardContents = Integer.parseInt(myClipboardContentLimitTextField.getText());
    } catch (NumberFormatException ignored) {}
    if (maxClipboardContents <= 0) {
      maxClipboardContents = 1;
    }
    return maxClipboardContents;
  }

  public boolean isModified() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    UISettings uiSettings=UISettings.getInstance();

    // Display
    boolean isModified = isModified(myCbSmoothScrolling, editorSettings.isSmoothScrolling());

    // Brace highlighting
    isModified |= isModified(myCbHighlightBraces, codeInsightSettings.HIGHLIGHT_BRACES);
    isModified |= isModified(myCbHighlightScope, codeInsightSettings.HIGHLIGHT_SCOPE);

    // Virtual space
    isModified |= isModified(myCbVirtualSpace, editorSettings.isVirtualSpace());
    isModified |= isModified(myCbCaretInsideTabs, editorSettings.isCaretInsideTabs());
    isModified |= isModified(myCbVirtualPageAtBottom, editorSettings.isAdditionalPageAtBottom());

    // Limits


    isModified |= getMaxClipboardContents() != uiSettings.MAX_CLIPBOARD_CONTENTS;

    // Paste

    // Strip trailing spaces on save
    isModified |= !getStripTrailingSpacesValue().equals(editorSettings.getStripTrailingSpaces());

    // advanced mouse
    isModified |= isModified(myCbEnableDnD, editorSettings.isDndEnabled());
    isModified |= isModified(myCbEnableWheelFontChange, editorSettings.isWheelFontChangeEnabled());
    isModified |= isModified(myCbHonorCamelHumpsWhenSelectingByClicking, editorSettings.isMouseClickSelectionHonorsCamelWords());

    isModified |= myRbPreferMovingCaret.isSelected() != editorSettings.isRefrainFromScrolling();


    isModified |= isModified(myRecentFilesLimitField, UISettings.getInstance().RECENT_FILES_LIMIT);

    isModified |= myErrorHighlightingPanel.isModified();
    for (EditorOptionsProvider provider : Extensions.getExtensions(EditorOptionsProvider.EP_NAME)) {
      isModified |= provider.isModified();
    }
    return isModified;
  }

  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isModified(JTextField textField, int value) {
    try {
      int fieldValue = Integer.parseInt(textField.getText().trim());
      return fieldValue != value;
    }
    catch(NumberFormatException e) {
      return false;
    }
  }

  private String getStripTrailingSpacesValue() {
    Object selectedItem = myStripTrailingSpacesCombo.getSelectedItem();
    if(STRIP_NONE.equals(selectedItem)) {
      return EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE;
    }
    else if(STRIP_CHANGED.equals(selectedItem)){
      return EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED;
    }
    else {
      return EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE;
    }
  }
}
