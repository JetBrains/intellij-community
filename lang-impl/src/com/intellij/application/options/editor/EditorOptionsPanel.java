package com.intellij.application.options.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class EditorOptionsPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.editor.EditorOptionsPanel");

  private JPanel myBehaviourPanel;
  private JPanel myAppearancePanel;
  private JCheckBox myCbModifiedTabsMarkedWithAsterisk;
  private JCheckBox myCbBlinkCaret;
  private JTextField myBlinkIntervalField;
  private JCheckBox myCbRightMargin;
  private JCheckBox myCbHighlightBraces;
  private JCheckBox myCbShowLineNumbers;
  private static final String STRIP_CHANGED = ApplicationBundle.message("combobox.strip.modified.lines");

  private static final String STRIP_ALL = ApplicationBundle.message("combobox.strip.all");
  private static final String STRIP_NONE = ApplicationBundle.message("combobox.strip.none");
  private JComboBox myStripTrailingSpacesCombo;

  private static final String NO_REFORMAT = ApplicationBundle.message("combobox.paste.reformat.none");
  private static final String INDENT_BLOCK = ApplicationBundle.message("combobox.paste.reformat.indent.block");
  private static final String INDENT_EACH_LINE = ApplicationBundle.message("combobox.paste.reformat.indent.each.line");
  private static final String REFORMAT_BLOCK = ApplicationBundle.message("combobox.paste.reformat.reformat.block");
  private JComboBox myReformatOnPasteCombo;

  private JCheckBox myCbSmartHome;
  private JCheckBox myCbSmartEnd;
  private JCheckBox myCbSmartIndentOnEnter;

  private JCheckBox myCbVirtualSpace;
  private JCheckBox myCbCaretInsideTabs;

  private JRadioButton myCloseNonModifiedFilesFirstRadio;
  private JRadioButton myCloseLRUFilesRadio;
  private JRadioButton myActivateMRUEditorOnCloseRadio;
  private JRadioButton myActivateLeftEditorOnCloseRadio;

  private JTextField myEditorTabLimitField;
  private JTextField myRecentFilesLimitField;

  private JCheckBox myScrollTabLayoutInEditorCheckBox;
  private JComboBox myEditorTabPlacement;
  private JCheckBox myHideKnownExtensions;

  private JCheckBox myCbHighlightScope;
  private JCheckBox myCbFolding;
  private JCheckBox myCbBlockCursor;

  private JCheckBox myCbInsertPairBracket;
  private JCheckBox myCbInsertPairQuote;
  private JTextField myClipboardContentLimitTextField;
  private JCheckBox myCbShowWhitespaces;
  private JCheckBox myCbSmoothScrolling;
  private JCheckBox myCbCamelWords;
  private JCheckBox myCbVirtualPageAtBottom;
  private JCheckBox myCbEnableDnD;
  private JCheckBox myCbEnableWheelFontChange;
  private JCheckBox myCbHonorCamelHumpsWhenSelectingByClicking;

  private JPanel myHighlightSettingsPanel;
  private JRadioButton myRbPreferScrolling;
  private JRadioButton myRbPreferMovingCaret;
  private ErrorHighlightingPanel myErrorHighlightingPanel = new ErrorHighlightingPanel();
  private JPanel myFoldingPanel;

  private TabbedPaneWrapper myTabbedPaneWrapper;

  public JComponent getTabbedPanel() {
    return myTabbedPaneWrapper.getComponent();
  }

  public EditorOptionsPanel(){
      myCbBlinkCaret.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent event) {
          myBlinkIntervalField.setEnabled(myCbBlinkCaret.isSelected());
        }
      }
    );

    if (SystemInfo.isMac) {
      myCbEnableWheelFontChange.setText(ApplicationBundle.message("checkbox.enable.ctrl.mousewheel.changes.font.size.macos"));
    }

    myReformatOnPasteCombo.addItem(NO_REFORMAT);
    myReformatOnPasteCombo.addItem(INDENT_BLOCK);
    myReformatOnPasteCombo.addItem(INDENT_EACH_LINE);
    myReformatOnPasteCombo.addItem(REFORMAT_BLOCK);

    myStripTrailingSpacesCombo.addItem(STRIP_CHANGED);
    myStripTrailingSpacesCombo.addItem(STRIP_ALL);
    myStripTrailingSpacesCombo.addItem(STRIP_NONE);


    final ButtonGroup editortabs = new ButtonGroup();
    editortabs.add(myActivateLeftEditorOnCloseRadio);
    editortabs.add(myActivateMRUEditorOnCloseRadio);

    final ButtonGroup closePolicy = new ButtonGroup();
    closePolicy.add(myCloseNonModifiedFilesFirstRadio);
    closePolicy.add(myCloseLRUFilesRadio);

    myEditorTabPlacement.setModel(new DefaultComboBoxModel(new Object[]{
      SwingConstants.TOP,
      SwingConstants.LEFT,
      SwingConstants.BOTTOM,
      SwingConstants.RIGHT,
      UISettings.TABS_NONE,
    }));
    myEditorTabPlacement.setRenderer(new MyTabsPlacementComboBoxRenderer());
    myHighlightSettingsPanel.setLayout(new BorderLayout());
    myHighlightSettingsPanel.add(myErrorHighlightingPanel.getPanel(), BorderLayout.CENTER);
    myTabbedPaneWrapper = new TabbedPaneWrapper();
    myTabbedPaneWrapper.addTab(ApplicationBundle.message("tab.editor.settings.behavior"), myBehaviourPanel);
    myTabbedPaneWrapper.addTab(ApplicationBundle.message("tab.editor.settings.appearance"), myAppearancePanel);
    for (EditorOptionsProvider provider : Extensions.getExtensions(EditorOptionsProvider.EP_NAME)) {
      myTabbedPaneWrapper.addTab(provider.getDisplayName(), provider.createComponent());
    }

    for (CodeFoldingOptionsProvider provider : Extensions.getExtensions(CodeFoldingOptionsProvider.EP_NAME)) {
      myFoldingPanel.add(provider.createComponent(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.NORTHWEST,
                                                                            GridBagConstraints.NONE, new Insets(0,0,0,0), 0,0));
    }
  }



  public void reset() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    UISettings uiSettings=UISettings.getInstance();

    // Display

    myCbModifiedTabsMarkedWithAsterisk.setSelected(uiSettings.MARK_MODIFIED_TABS_WITH_ASTERISK);
    myCbBlinkCaret.setSelected(editorSettings.isBlinkCaret());
    myBlinkIntervalField.setText(Integer.toString(editorSettings.getBlinkPeriod()));
    myBlinkIntervalField.setEnabled(editorSettings.isBlinkCaret());

    myCbBlockCursor.setSelected(editorSettings.isBlockCursor());

    myCbRightMargin.setSelected(editorSettings.isRightMarginShown());

    myCbShowLineNumbers.setSelected(editorSettings.isLineNumbersShown());


    myCbShowWhitespaces.setSelected(editorSettings.isWhitespacesShown());
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

    // Paste
    switch(codeInsightSettings.REFORMAT_ON_PASTE){
      case CodeInsightSettings.NO_REFORMAT:
        myReformatOnPasteCombo.setSelectedItem(NO_REFORMAT);
      break;

      case CodeInsightSettings.INDENT_BLOCK:
        myReformatOnPasteCombo.setSelectedItem(INDENT_BLOCK);
      break;

      case CodeInsightSettings.INDENT_EACH_LINE:
        myReformatOnPasteCombo.setSelectedItem(INDENT_EACH_LINE);
      break;

      case CodeInsightSettings.REFORMAT_BLOCK:
        myReformatOnPasteCombo.setSelectedItem(REFORMAT_BLOCK);
      break;
    }



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

    // Smart Keys

    myCbSmartHome.setSelected(editorSettings.isSmartHome());
    myCbSmartEnd.setSelected(codeInsightSettings.SMART_END_ACTION);

    myCbSmartIndentOnEnter.setSelected(codeInsightSettings.SMART_INDENT_ON_ENTER);

    myCbInsertPairBracket.setSelected(codeInsightSettings.AUTOINSERT_PAIR_BRACKET);
    myCbInsertPairQuote.setSelected(codeInsightSettings.AUTOINSERT_PAIR_QUOTE);
    myCbCamelWords.setSelected(editorSettings.isCamelWords());

    // Code Folding

    myCbFolding.setSelected(editorSettings.isFoldingOutlineShown());


    // Advanced mouse
    myCbEnableDnD.setSelected(editorSettings.isDndEnabled());
    myCbEnableWheelFontChange.setSelected(editorSettings.isWheelFontChangeEnabled());
    myCbHonorCamelHumpsWhenSelectingByClicking.setSelected(editorSettings.isMouseClickSelectionHonorsCamelWords());

    myRbPreferMovingCaret.setSelected(editorSettings.isRefrainFromScrolling());
    myRbPreferScrolling.setSelected(!editorSettings.isRefrainFromScrolling());


    // Editor Tabs
    myScrollTabLayoutInEditorCheckBox.setSelected(uiSettings.SCROLL_TAB_LAYOUT_IN_EDITOR);
    myEditorTabPlacement.setSelectedItem(uiSettings.EDITOR_TAB_PLACEMENT);
    myHideKnownExtensions.setSelected(uiSettings.HIDE_KNOWN_EXTENSION_IN_TABS);
    if (uiSettings.CLOSE_NON_MODIFIED_FILES_FIRST) {
      myCloseNonModifiedFilesFirstRadio.setSelected(true);
    }
    else {
      myCloseLRUFilesRadio.setSelected(true);
    }
    if (uiSettings.ACTIVATE_MRU_EDITOR_ON_CLOSE) {
      myActivateMRUEditorOnCloseRadio.setSelected(true);
    }
    else {
      myActivateLeftEditorOnCloseRadio.setSelected(true);
    }

    myEditorTabLimitField.setText(Integer.toString(uiSettings.EDITOR_TAB_LIMIT));
    myRecentFilesLimitField.setText(Integer.toString(uiSettings.RECENT_FILES_LIMIT));
    myErrorHighlightingPanel.reset();
    for (EditorOptionsProvider provider : Extensions.getExtensions(EditorOptionsProvider.EP_NAME)) {
      provider.reset();
    }
    for (CodeFoldingOptionsProvider provider : Extensions.getExtensions(CodeFoldingOptionsProvider.EP_NAME)) {
      provider.reset();
    }
  }

  public void apply() throws ConfigurationException {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    UISettings uiSettings=UISettings.getInstance();

    // Display

    boolean uiSettingsChanged = uiSettings.MARK_MODIFIED_TABS_WITH_ASTERISK != myCbModifiedTabsMarkedWithAsterisk.isSelected();
    uiSettings.MARK_MODIFIED_TABS_WITH_ASTERISK = myCbModifiedTabsMarkedWithAsterisk.isSelected();

    editorSettings.setBlinkCaret(myCbBlinkCaret.isSelected());
    try {
      editorSettings.setBlinkPeriod(Integer.parseInt(myBlinkIntervalField.getText()));
    }
    catch (NumberFormatException e) {
    }

    editorSettings.setBlockCursor(myCbBlockCursor.isSelected());

    editorSettings.setRightMarginShown(myCbRightMargin.isSelected());

    editorSettings.setLineNumbersShown(myCbShowLineNumbers.isSelected());

    editorSettings.setWhitespacesShown(myCbShowWhitespaces.isSelected());
    editorSettings.setSmoothScrolling(myCbSmoothScrolling.isSelected());


    // Brace Highlighting

    codeInsightSettings.HIGHLIGHT_BRACES = myCbHighlightBraces.isSelected();
    codeInsightSettings.HIGHLIGHT_SCOPE = myCbHighlightScope.isSelected();

    // Virtual space

    editorSettings.setVirtualSpace(myCbVirtualSpace.isSelected());
    editorSettings.setCaretInsideTabs(myCbCaretInsideTabs.isSelected());
    editorSettings.setAdditionalPageAtBottom(myCbVirtualPageAtBottom.isSelected());

    // Limits



    int maxClipboardContents = getMaxClipboardContents();
    if (uiSettings.MAX_CLIPBOARD_CONTENTS != maxClipboardContents) {
      uiSettings.MAX_CLIPBOARD_CONTENTS = maxClipboardContents;
      uiSettingsChanged = true;
    }

    if(uiSettingsChanged){
      uiSettings.fireUISettingsChanged();
    }

    // Paste

    codeInsightSettings.REFORMAT_ON_PASTE = getReformatPastedBlockValue();


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

    // smart keys

    editorSettings.setSmartHome(myCbSmartHome.isSelected());
    codeInsightSettings.SMART_END_ACTION = myCbSmartEnd.isSelected();

    codeInsightSettings.SMART_INDENT_ON_ENTER = myCbSmartIndentOnEnter.isSelected();

    codeInsightSettings.AUTOINSERT_PAIR_BRACKET = myCbInsertPairBracket.isSelected();
    codeInsightSettings.AUTOINSERT_PAIR_QUOTE = myCbInsertPairQuote.isSelected();
    editorSettings.setCamelWords(myCbCamelWords.isSelected());

    // Code folding

    editorSettings.setFoldingOutlineShown(myCbFolding.isSelected());



    editorSettings.setDndEnabled(myCbEnableDnD.isSelected());

    editorSettings.setWheelFontChangeEnabled(myCbEnableWheelFontChange.isSelected());
    editorSettings.setMouseClickSelectionHonorsCamelWords(myCbHonorCamelHumpsWhenSelectingByClicking.isSelected());
    editorSettings.setRefrainFromScrolling(myRbPreferMovingCaret.isSelected());

    Editor[] editors = EditorFactory.getInstance().getAllEditors();
    for (Editor editor : editors) {
      ((EditorEx)editor).reinitSettings();
    }

    if (isModified(myScrollTabLayoutInEditorCheckBox, uiSettings.SCROLL_TAB_LAYOUT_IN_EDITOR)) uiSettingsChanged = true;
    uiSettings.SCROLL_TAB_LAYOUT_IN_EDITOR = myScrollTabLayoutInEditorCheckBox.isSelected();

    final int tabPlacement = ((Integer)myEditorTabPlacement.getSelectedItem()).intValue();
    if (uiSettings.EDITOR_TAB_PLACEMENT != tabPlacement) uiSettingsChanged = true;
    uiSettings.EDITOR_TAB_PLACEMENT = tabPlacement;

    boolean hide = myHideKnownExtensions.isSelected();
    if (uiSettings.HIDE_KNOWN_EXTENSION_IN_TABS != hide) uiSettingsChanged = true;
    uiSettings.HIDE_KNOWN_EXTENSION_IN_TABS = hide;

    uiSettings.CLOSE_NON_MODIFIED_FILES_FIRST = myCloseNonModifiedFilesFirstRadio.isSelected();
    uiSettings.ACTIVATE_MRU_EDITOR_ON_CLOSE = myActivateMRUEditorOnCloseRadio.isSelected();

    String temp = myEditorTabLimitField.getText();
    if(temp.trim().length() > 0){
      try {
        int newEditorTabLimit = Integer.parseInt(temp);
        if(newEditorTabLimit>0&&newEditorTabLimit!=uiSettings.EDITOR_TAB_LIMIT){
          uiSettings.EDITOR_TAB_LIMIT=newEditorTabLimit;
          uiSettingsChanged = true;
        }
      }catch (NumberFormatException ignored){}
    }
    temp=myRecentFilesLimitField.getText();
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
    for (CodeFoldingOptionsProvider provider : Extensions.getExtensions(CodeFoldingOptionsProvider.EP_NAME)) {
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
    boolean isModified = isModified(myCbModifiedTabsMarkedWithAsterisk, uiSettings.MARK_MODIFIED_TABS_WITH_ASTERISK);
    isModified |= isModified(myCbBlinkCaret, editorSettings.isBlinkCaret());
    isModified |= isModified(myBlinkIntervalField, editorSettings.getBlinkPeriod());

    isModified |= isModified(myCbBlockCursor, editorSettings.isBlockCursor());

    isModified |= isModified(myCbRightMargin, editorSettings.isRightMarginShown());

    isModified |= isModified(myCbShowLineNumbers, editorSettings.isLineNumbersShown());
    isModified |= isModified(myCbShowWhitespaces, editorSettings.isWhitespacesShown());
    isModified |= isModified(myCbSmoothScrolling, editorSettings.isSmoothScrolling());

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

    isModified |= getReformatPastedBlockValue() != codeInsightSettings.REFORMAT_ON_PASTE;

    // Strip trailing spaces on save
    isModified |= !getStripTrailingSpacesValue().equals(editorSettings.getStripTrailingSpaces());

    // Smart keys

    isModified |= isModified(myCbSmartHome, editorSettings.isSmartHome());
    isModified |= isModified(myCbSmartEnd, codeInsightSettings.SMART_END_ACTION);

    isModified |= isModified(myCbSmartIndentOnEnter, codeInsightSettings.SMART_INDENT_ON_ENTER);

    isModified |= isModified(myCbInsertPairBracket, codeInsightSettings.AUTOINSERT_PAIR_BRACKET);
    isModified |= isModified(myCbInsertPairQuote, codeInsightSettings.AUTOINSERT_PAIR_QUOTE);
    isModified |= isModified(myCbCamelWords, editorSettings.isCamelWords());

    // Code folding

    isModified |= isModified(myCbFolding, editorSettings.isFoldingOutlineShown());


    // advanced mouse
    isModified |= isModified(myCbEnableDnD, editorSettings.isDndEnabled());
    isModified |= isModified(myCbEnableWheelFontChange, editorSettings.isWheelFontChangeEnabled());
    isModified |= isModified(myCbHonorCamelHumpsWhenSelectingByClicking, editorSettings.isMouseClickSelectionHonorsCamelWords());

    isModified |= myRbPreferMovingCaret.isSelected() != editorSettings.isRefrainFromScrolling();


    isModified |= isModified(myCloseNonModifiedFilesFirstRadio, uiSettings.CLOSE_NON_MODIFIED_FILES_FIRST);
    isModified |= isModified(myActivateMRUEditorOnCloseRadio, uiSettings.ACTIVATE_MRU_EDITOR_ON_CLOSE);

    isModified |= isModified(myEditorTabLimitField, UISettings.getInstance().EDITOR_TAB_LIMIT);
    isModified |= isModified(myRecentFilesLimitField, UISettings.getInstance().RECENT_FILES_LIMIT);

    int tabPlacement = ((Integer)myEditorTabPlacement.getSelectedItem()).intValue();
    isModified |= tabPlacement != uiSettings.EDITOR_TAB_PLACEMENT;
    isModified |= myHideKnownExtensions.isSelected() != uiSettings.HIDE_KNOWN_EXTENSION_IN_TABS;

    isModified |= myScrollTabLayoutInEditorCheckBox.isSelected() != uiSettings.SCROLL_TAB_LAYOUT_IN_EDITOR;

    isModified |= myErrorHighlightingPanel.isModified();
    for (EditorOptionsProvider provider : Extensions.getExtensions(EditorOptionsProvider.EP_NAME)) {
      isModified |= provider.isModified();
    }
    for (CodeFoldingOptionsProvider provider : Extensions.getExtensions(CodeFoldingOptionsProvider.EP_NAME)) {
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

  private int getReformatPastedBlockValue(){
    Object selectedItem = myReformatOnPasteCombo.getSelectedItem();
    if (NO_REFORMAT.equals(selectedItem)){
      return CodeInsightSettings.NO_REFORMAT;
    }
    else if (INDENT_BLOCK.equals(selectedItem)){
      return CodeInsightSettings.INDENT_BLOCK;
    }
    else if (INDENT_EACH_LINE.equals(selectedItem)){
      return CodeInsightSettings.INDENT_EACH_LINE;
    }
    else if (REFORMAT_BLOCK.equals(selectedItem)){
      return CodeInsightSettings.REFORMAT_BLOCK;
    }
    else{
      LOG.assertTrue(false);
      return -1;
    }
  }

  private static final class MyTabsPlacementComboBoxRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      int tabPlacement = ((Integer)value).intValue();
      String text;
      if (UISettings.TABS_NONE == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.none");
      }
      else if (SwingConstants.TOP == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.top");
      }
      else if (SwingConstants.LEFT == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.left");
      }
      else if (SwingConstants.BOTTOM == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.bottom");
      }
      else if (SwingConstants.RIGHT == tabPlacement) {
        text = ApplicationBundle.message("combobox.tab.placement.right");
      }
      else {
        throw new IllegalArgumentException("unknown tabPlacement: " + tabPlacement);
      }
      return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
    }
  }

}
