
/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.find.impl;

import com.intellij.CommonBundle;
import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.StateRestoringCheckBox;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

class FindDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.FindDialog");

  private ComboBox myInputComboBox;
  private ComboBox myReplaceComboBox;
  private StateRestoringCheckBox myCbCaseSensitive;
  private StateRestoringCheckBox myCbPreserveCase;
  private StateRestoringCheckBox myCbWholeWordsOnly;
  private StateRestoringCheckBox myCbInCommentsOnly;
  private StateRestoringCheckBox myCbInStringLiteralsOnly;
  private StateRestoringCheckBox myCbRegularExpressions;
  private JRadioButton myRbGlobal;
  private JRadioButton myRbSelectedText;
  private JRadioButton myRbForward;
  private JRadioButton myRbBackward;
  private JRadioButton myRbFromCursor;
  private JRadioButton myRbEntireScope;
  private JRadioButton myRbProject;
  private JRadioButton myRbDirectory;
  private JRadioButton myRbModule;
  private ComboBox myModuleComboBox;
  private ComboBox myDirectoryComboBox;
  private StateRestoringCheckBox myCbWithSubdirectories;
  private JCheckBox myCbToOpenInNewTab;
  private final FindModel myModel;
  private final Runnable myOkHandler;
  private FixedSizeButton mySelectDirectoryButton;
  private StateRestoringCheckBox useFileFilter;
  private ComboBox myFileFilter;
  private JCheckBox myCbToSkipResultsWhenOneUsage;
  private final Project myProject;
  private final Map<EditorTextField, DocumentAdapter> myComboBoxListeners = new HashMap<EditorTextField, DocumentAdapter>();

  private Action myFindAllAction;
  private JRadioButton myRbCustomScope;
  private ScopeChooserCombo myScopeCombo;

  public FindDialog(Project project, FindModel model, Runnable myOkHandler){
    super(project, true);
    myProject = project;
    myModel = model;
    this.myOkHandler = myOkHandler;

    if (myModel.isReplaceState()){
      if (myModel.isMultipleFiles()){
        setTitle(FindBundle.message("find.replace.in.project.dialog.title"));
      }
      else{
        setTitle(FindBundle.message("find.replace.text.dialog.title"));
      }
    }
    else{
      setButtonsMargin(null);
      if (myModel.isMultipleFiles()){
        setTitle(FindBundle.message("find.in.path.dialog.title"));
      }
      else{
        setTitle(FindBundle.message("find.text.dialog.title"));
      }
    }
    setOKButtonText(FindBundle.message("find.button"));
    setOKButtonIcon(IconLoader.getIcon("/actions/find.png"));
    init();
    initByModel();
  }

  @Override
  protected void dispose() {
    for(Map.Entry<EditorTextField, DocumentAdapter> e: myComboBoxListeners.entrySet()) {
      e.getKey().removeDocumentListener(e.getValue());
    }
    myComboBoxListeners.clear();
    super.dispose();
  }

  public JComponent getPreferredFocusedComponent() {
    return myInputComboBox;
  }

  protected String getDimensionServiceKey() {
    return myModel.isReplaceState() ? "replaceTextDialog" : "findTextDialog";
  }

  @Override
  protected Action[] createActions() {
    if (!myModel.isMultipleFiles() && !myModel.isReplaceState() && myModel.isFindAllEnabled()) {
      return new Action[] { getFindAllAction(), getOKAction(), getCancelAction(), getHelpAction() };
    }
    return new Action[] { getOKAction(), getCancelAction(), getHelpAction() };
  }

  private Action getFindAllAction() {
    return myFindAllAction = new AbstractAction(FindBundle.message("find.all.button")) {
      public void actionPerformed(ActionEvent e) {
        doOKAction(true);
      }
    };
  }

  public JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gbConstraints = new GridBagConstraints();

    gbConstraints.insets = new Insets(4, 4, 4, 4);
    gbConstraints.fill = GridBagConstraints.VERTICAL;
    gbConstraints.weightx = 0;
    gbConstraints.weighty = 1;
    gbConstraints.anchor = GridBagConstraints.EAST;
    JLabel prompt = new JLabel(FindBundle.message("find.text.to.find.label"));
    panel.add(prompt, gbConstraints);

    myInputComboBox = new ComboBox(300);
    revealWhitespaces(myInputComboBox);
    initCombobox(myInputComboBox);

    if (myModel.isReplaceState()){
      myReplaceComboBox = new ComboBox(300);
      revealWhitespaces(myReplaceComboBox);
      
      initCombobox(myReplaceComboBox);
      final Component editorComponent = myReplaceComboBox.getEditor().getEditorComponent();
      editorComponent.addFocusListener(
        new FocusAdapter() {
          public void focusGained(FocusEvent e) {
            myReplaceComboBox.getEditor().selectAll();
            editorComponent.removeFocusListener(this);
          }
        }
      );
    }


    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    gbConstraints.fill = GridBagConstraints.BOTH;
    gbConstraints.weightx = 1;
    panel.add(myInputComboBox, gbConstraints);
    prompt.setLabelFor(myInputComboBox.getEditor().getEditorComponent());

    if (myModel.isReplaceState()){
      gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
      gbConstraints.fill = GridBagConstraints.VERTICAL;
      gbConstraints.weightx = 0;
      final JLabel replacePrompt = new JLabel(FindBundle.message("find.replace.with.label"));
      panel.add(replacePrompt, gbConstraints);

      gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
      gbConstraints.fill = GridBagConstraints.BOTH;
      gbConstraints.weightx = 1;
      panel.add(myReplaceComboBox, gbConstraints);
      replacePrompt.setLabelFor(myReplaceComboBox.getEditor().getEditorComponent());
    }

    return panel;
  }

  private void revealWhitespaces(ComboBox comboBox) {
    ComboBoxEditor comboBoxEditor = new RevealingSpaceComboboxEditor(myProject, comboBox);
    comboBox.setEditor(comboBoxEditor);
    comboBox.setRenderer(new EditorComboBoxRenderer(comboBoxEditor));
  }

  private void initCombobox(final ComboBox comboBox) {
    comboBox.setEditable(true);
    comboBox.setMaximumRowCount(8);

    comboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateFindButton();
      }
    });

    Component editorComponent = comboBox.getEditor().getEditorComponent();

    if (editorComponent instanceof EditorTextField) {
      final EditorTextField etf = (EditorTextField) editorComponent;

      DocumentAdapter listener = new DocumentAdapter() {
        public void documentChanged(final DocumentEvent e) {
          handleComboBoxValueChanged(comboBox);
        }
      };
      etf.addDocumentListener(listener);
      myComboBoxListeners.put(etf, listener);
    }
    else {
      editorComponent.addKeyListener(
        new KeyAdapter() {
          public void keyReleased(KeyEvent e) {
            handleComboBoxValueChanged(comboBox);
          }
        }
      );
    }
  }

  private void handleComboBoxValueChanged(final ComboBox comboBox) {
    Object item = comboBox.getEditor().getItem();
    if (item != null && !item.equals(comboBox.getSelectedItem())){
      int caretPosition = getCaretPosition(comboBox);
      comboBox.setSelectedItem(item);
      setCaretPosition(comboBox, caretPosition);
    }
    validateFindButton();
  }

  private static int getCaretPosition(JComboBox comboBox) {
    Component editorComponent = comboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField){
      JTextField textField = (JTextField)editorComponent;
      return textField.getCaretPosition();
    }
    return 0;
  }

  private static void setCaretPosition(JComboBox comboBox, int position) {
    Component editorComponent = comboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField){
      JTextField textField = (JTextField)editorComponent;
      textField.setCaretPosition(position);
    }
  }

  private void validateFindButton() {
    final String toFind = getStringToFind();

    if (toFind == null || toFind.length() == 0){
      setOKStatus(false);
      return;
    }

    if (myRbDirectory != null && myRbDirectory.isSelected() &&
      (getDirectory() == null || getDirectory().length() == 0)){
      setOKStatus(false);
      return;
    }
    setOKStatus(true);
  }

  private void setOKStatus(boolean value) {
    setOKActionEnabled(value);
    if (myFindAllAction != null) {
      myFindAllAction.setEnabled(value);
    }
  }

  public JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel();
    optionsPanel.setLayout(new GridBagLayout());

    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.weightx = 1;
    gbConstraints.weighty = 1;
    gbConstraints.fill = GridBagConstraints.BOTH;

    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    JPanel topOptionsPanel = new JPanel();

    topOptionsPanel.setLayout(new GridLayout(1, 2, 8, 0));
    optionsPanel.add(topOptionsPanel, gbConstraints);

    topOptionsPanel.add(createFindOptionsPanel());
    if (!myModel.isMultipleFiles()){
      if (FindManagerImpl.ourHasSearchInCommentsAndLiterals) {
        JPanel leftOptionsPanel = new JPanel();
        leftOptionsPanel.setLayout(new GridLayout(3, 1, 0, 4));

        leftOptionsPanel.add(createDirectionPanel());
        leftOptionsPanel.add(createOriginPanel());
        leftOptionsPanel.add(createScopePanel());
        topOptionsPanel.add(leftOptionsPanel);
      } else {
        topOptionsPanel.add(createDirectionPanel());
        gbConstraints.gridwidth = GridBagConstraints.RELATIVE;
        JPanel bottomOptionsPanel = new JPanel();
        bottomOptionsPanel.setLayout(new GridLayout(1, 2, 8, 0));
        optionsPanel.add(bottomOptionsPanel, gbConstraints);
        bottomOptionsPanel.add(createScopePanel());
        bottomOptionsPanel.add(createOriginPanel());
      }
    }
    else{
      optionsPanel.add(createGlobalScopePanel(), gbConstraints);
      gbConstraints.weightx = 1;
      gbConstraints.weighty = 1;
      gbConstraints.fill = GridBagConstraints.HORIZONTAL;

      gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
      optionsPanel.add(createFilterPanel(),gbConstraints);

      if (!myModel.isReplaceState()) {
        myCbToSkipResultsWhenOneUsage = createCheckbox(FindSettings.getInstance().isSkipResultsWithOneUsage(), FindBundle.message("find.options.skip.results.tab.with.one.usage.checkbox"));
        optionsPanel.add(myCbToSkipResultsWhenOneUsage, gbConstraints);
      }
    }

    if (myModel.isOpenInNewTabVisible()){
      JPanel openInNewTabWindowPanel = new JPanel(new BorderLayout());
      myCbToOpenInNewTab = new JCheckBox(FindBundle.message("find.open.in.new.tab.checkbox"));
      myCbToOpenInNewTab.setFocusable(false);
      myCbToOpenInNewTab.setSelected(myModel.isOpenInNewTab());
      myCbToOpenInNewTab.setEnabled(myModel.isOpenInNewTabEnabled());
      openInNewTabWindowPanel.add(myCbToOpenInNewTab, BorderLayout.EAST);
      optionsPanel.add(openInNewTabWindowPanel, gbConstraints);
    }

    return optionsPanel;
  }

  private JComponent createFilterPanel() {
    JPanel filterPanel = new JPanel();
    filterPanel.setLayout(new BorderLayout());
    filterPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.filter.file.name.group")));

    myFileFilter = new ComboBox(100);
    initCombobox(myFileFilter);
    filterPanel.add(useFileFilter = createCheckbox(FindBundle.message("find.filter.file.mask.checkbox")),BorderLayout.WEST);
    filterPanel.add(myFileFilter,BorderLayout.CENTER);
    myFileFilter.setEditable(true);
    String[] fileMasks = FindSettings.getInstance().getRecentFileMasks();
    for(int i=fileMasks.length-1; i >= 0; i--) {
      myFileFilter.addItem(fileMasks [i]);
    }
    myFileFilter.setEnabled(false);

    useFileFilter.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (!useFileFilter.isSelected()) {
            myFileFilter.setEnabled(false);
          } else {
            myFileFilter.setEnabled(true);
            myFileFilter.getEditor().selectAll();
            myFileFilter.getEditor().getEditorComponent().requestFocusInWindow();
          }
        }
      }
    );

    return filterPanel;
  }

  public void doOKAction() {
    doOKAction(false);
  }

  private void doOKAction(boolean findAll) {
    FindModel validateModel = (FindModel)myModel.clone();
    applyTo(validateModel);
    validateModel.setFindAll(findAll);
    if (validateModel.getDirectoryName() != null) {
      PsiDirectory directory = FindInProjectUtil.getPsiDirectory(validateModel, myProject);
      if (directory == null) {
        Messages.showMessageDialog(
          myProject,
          FindBundle.message("find.directory.not.found.error", validateModel.getDirectoryName()),
          CommonBundle.getErrorTitle(),
          Messages.getErrorIcon()
        );
        return;
      }
    }

    if (validateModel.isRegularExpressions()) {
      String toFind = validateModel.getStringToFind();
      try {
        Pattern pattern = Pattern.compile(toFind, validateModel.isCaseSensitive() ? Pattern.MULTILINE : Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        if (pattern.matcher("").matches() && !toFind.endsWith("$") && !toFind.startsWith("^")) {
          throw new PatternSyntaxException(FindBundle.message("find.empty.match.regular.expression.error"),toFind, -1);
        }
      }
      catch(PatternSyntaxException e){
        Messages.showMessageDialog(
            myProject,
            FindBundle.message("find.invalid.regular.expression.error", toFind, e.getDescription()),
            CommonBundle.getErrorTitle(),
            Messages.getErrorIcon()
        );
        return;
      }
    }

    validateModel.setFileFilter( null );
    FindSettings.getInstance().setFileMask(null);

    if (useFileFilter!=null && useFileFilter.isSelected() &&
        myFileFilter.getSelectedItem()!=null
       ) {
      final String mask = (String)myFileFilter.getSelectedItem();

      if (mask.length() > 0) {
        try {
          FindInProjectUtil.createFileMaskRegExp(mask);   // verify that the regexp compiles
          validateModel.setFileFilter(mask);
          FindSettings.getInstance().setFileMask(mask);
        }
        catch (PatternSyntaxException ex) {
          Messages.showMessageDialog(myProject, FindBundle.message("find.filter.invalid.file.mask.error", myFileFilter.getSelectedItem()),
                                     CommonBundle.getErrorTitle(), Messages.getErrorIcon());
          return;
        }
      }
      else {
        Messages.showMessageDialog(myProject, FindBundle.message("find.filter.empty.file.mask.error"), CommonBundle.getErrorTitle(),
                                   Messages.getErrorIcon());
        return;
      }
    }

    if (myCbToSkipResultsWhenOneUsage != null){
      FindSettings.getInstance().setSkipResultsWithOneUsage(
        isSkipResultsWhenOneUsage()
      );
    }

    myModel.copyFrom(validateModel);
    super.doOKAction();
    myOkHandler.run();
  }

  public void doHelpAction() {
    String id = myModel.isReplaceState()
                ? myModel.isMultipleFiles() ? HelpID.REPLACE_IN_PATH : HelpID.REPLACE_OPTIONS
                : myModel.isMultipleFiles() ? HelpID.FIND_IN_PATH : HelpID.FIND_OPTIONS;
    HelpManager.getInstance().invokeHelp(id);
  }

  private boolean isSkipResultsWhenOneUsage() {
    return myCbToSkipResultsWhenOneUsage!=null &&
    myCbToSkipResultsWhenOneUsage.isSelected();
  }

  private JPanel createFindOptionsPanel() {
    JPanel findOptionsPanel = new JPanel();
    findOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.options.group")));
    findOptionsPanel.setLayout(new BoxLayout(findOptionsPanel, BoxLayout.Y_AXIS));

    myCbCaseSensitive = createCheckbox(FindBundle.message("find.options.case.sensitive"));
    findOptionsPanel.add(myCbCaseSensitive);
    if (myModel.isReplaceState()) {
      myCbPreserveCase = createCheckbox(FindBundle.message("find.options.replace.preserve.case"));
      findOptionsPanel.add(myCbPreserveCase);
    }
    myCbWholeWordsOnly = createCheckbox(FindBundle.message("find.options.whole.words.only"));

    findOptionsPanel.add(myCbWholeWordsOnly);

    myCbRegularExpressions = createCheckbox(FindBundle.message("find.options.regular.expressions"));

    final JPanel regExPanel = new JPanel();
    regExPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
    regExPanel.setLayout(new BoxLayout(regExPanel, BoxLayout.X_AXIS));
    regExPanel.add(myCbRegularExpressions);

    regExPanel.add(new LinkLabel("[Help]", null, new LinkListener() {
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        try {
          final JBPopup helpPopup = RegExHelpPopup.createRegExHelpPopup();
          helpPopup.showInCenterOf(regExPanel);
        }
        catch (BadLocationException e) {
          LOG.info(e);
        }
      }
    }));

    findOptionsPanel.add(regExPanel);

    myCbInCommentsOnly = createCheckbox(FindBundle.message("find.options.comments.only"));
    myCbInStringLiteralsOnly = createCheckbox(FindBundle.message("find.options.string.literals.only"));

    if (FindManagerImpl.ourHasSearchInCommentsAndLiterals) {
      findOptionsPanel.add(myCbInCommentsOnly);
      findOptionsPanel.add(myCbInStringLiteralsOnly);
    }

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myCbRegularExpressions.addActionListener(actionListener);
    myCbRegularExpressions.addItemListener(new ItemListener() {
      public void itemStateChanged(final ItemEvent e) {
        setupRegExpSetting();
      }
    });

    if (myModel.isReplaceState()) {
      myCbCaseSensitive.addActionListener(actionListener);
      myCbPreserveCase.addActionListener(actionListener);
    }

//    if(isReplaceState) {
//      myCbPromptOnReplace = new JCheckBox("Prompt on replace", true);
//      myCbPromptOnReplace.setMnemonic('P');
//      findOptionsPanel.add(myCbPromptOnReplace);
//    }
    return findOptionsPanel;
  }

  private void setupRegExpSetting() {
    updateFileTypeForEditorComponent(myInputComboBox);
    if (myReplaceComboBox != null) updateFileTypeForEditorComponent(myReplaceComboBox);
  }

  private void updateFileTypeForEditorComponent(final ComboBox inputComboBox) {
    final Component editorComponent = inputComboBox.getEditor().getEditorComponent();

    if (editorComponent instanceof EditorTextField) {
      boolean selected = myCbRegularExpressions.isSelectedWhenSelectable();
      @NonNls final String s = selected ? "*.regexp" : "*.txt";
      FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(s);

      if (selected && fileType == FileTypes.UNKNOWN) {
        fileType = FileTypeManager.getInstance().getFileTypeByFileName("*.txt"); // RegExp plugin is not installed
      }

      final PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText(s, fileType, ((EditorTextField)editorComponent).getText(), -1, true);

      ((EditorTextField)editorComponent).setNewDocumentAndFileType(fileType, PsiDocumentManager.getInstance(myProject).getDocument(file));
    }
  }

  private void updateControls() {
    if (myCbRegularExpressions.isSelected()) {
      myCbWholeWordsOnly.makeUnselectable(false);
    } else {
      myCbWholeWordsOnly.makeSelectable();
    }
    if (myModel.isReplaceState()) {
      if (myCbRegularExpressions.isSelected() || myCbCaseSensitive.isSelected()) {
        myCbPreserveCase.makeUnselectable(false);
      } else {
        myCbPreserveCase.makeSelectable();
      }

      if (myCbPreserveCase.isSelected()) {
        myCbRegularExpressions.makeUnselectable(false);
        myCbCaseSensitive.makeUnselectable(false);
      } else {
        myCbRegularExpressions.makeSelectable();
        myCbCaseSensitive.makeSelectable();
      }
    }

    if (!myModel.isMultipleFiles()) {
      myRbFromCursor.setEnabled(myRbGlobal.isSelected());
      myRbEntireScope.setEnabled(myRbGlobal.isSelected());
    }
  }

  private JPanel createDirectionPanel() {
    JPanel directionPanel = new JPanel();
    directionPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.direction.group")));
    directionPanel.setLayout(new BoxLayout(directionPanel, BoxLayout.Y_AXIS));

    myRbForward = new JRadioButton(FindBundle.message("find.direction.forward.radio"), true);
    directionPanel.add(myRbForward);
    myRbBackward = new JRadioButton(FindBundle.message("find.direction.backward.radio"));
    directionPanel.add(myRbBackward);
    ButtonGroup bgDirection = new ButtonGroup();
    bgDirection.add(myRbForward);
    bgDirection.add(myRbBackward);

    return directionPanel;
  }

  private JComponent createGlobalScopePanel() {
    JPanel scopePanel = new JPanel();
    scopePanel.setLayout(new GridBagLayout());
    scopePanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.scope.group")));
    GridBagConstraints gbConstraints = new GridBagConstraints();
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = 2;
    gbConstraints.weightx = 1;
    myRbProject = new JRadioButton(FindBundle.message("find.scope.whole.project.radio"), true);
    scopePanel.add(myRbProject, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    myRbModule = new JRadioButton(FindBundle.message("find.scope.module.radio"), false);
    scopePanel.add(myRbModule, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    String[] names = new String[modules.length];
    for (int i = 0; i < modules.length; i++) {
      names[i] = modules[i].getName();
    }

    Arrays.sort(names,String.CASE_INSENSITIVE_ORDER);
    myModuleComboBox = new ComboBox(names, -1);
    scopePanel.add(myModuleComboBox, gbConstraints);

    if (modules.length == 1) {
      myModuleComboBox.setVisible(false);
      myRbModule.setVisible(false);
    }

    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.weightx = 0;
    gbConstraints.gridwidth = 1;
    myRbDirectory = new JRadioButton(FindBundle.message("find.scope.directory.radio"), false);
    scopePanel.add(myRbDirectory, gbConstraints);

    gbConstraints.gridx = 1;
    gbConstraints.weightx = 1;

    myDirectoryComboBox = new ComboBox(-1);
    Component editorComponent = myDirectoryComboBox.getEditor().getEditorComponent();
    if (editorComponent instanceof JTextField) {
      JTextField field = (JTextField)editorComponent;
      field.setColumns(40);
    }
    initCombobox(myDirectoryComboBox);
    scopePanel.add(myDirectoryComboBox, gbConstraints);

    gbConstraints.weightx = 0;
    gbConstraints.gridx = 2;
    gbConstraints.insets = new Insets(0, 1, 0, 0);
    mySelectDirectoryButton = new FixedSizeButton(myDirectoryComboBox);
    TextFieldWithBrowseButton.MyDoClickAction.addTo(mySelectDirectoryButton, myDirectoryComboBox);
    mySelectDirectoryButton.setMargin(new Insets(0, 0, 0, 0));
    scopePanel.add(mySelectDirectoryButton, gbConstraints);

    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 2;
    gbConstraints.insets = new Insets(0, 16, 0, 0);
    myCbWithSubdirectories = createCheckbox(true, FindBundle.message("find.scope.directory.recursive.checkbox"));
    myCbWithSubdirectories.setSelected(true);
    scopePanel.add(myCbWithSubdirectories, gbConstraints);


    gbConstraints.gridx = 0;
    gbConstraints.gridy++;
    gbConstraints.weightx = 1;
    gbConstraints.gridwidth = 1;
    gbConstraints.insets = new Insets(0, 1, 0, 0);
    myRbCustomScope = new JRadioButton(FindBundle.message("find.scope.custom.radio"), false);
    scopePanel.add(myRbCustomScope, gbConstraints);

    gbConstraints.gridx++;
    gbConstraints.gridwidth = GridBagConstraints.REMAINDER;
    myScopeCombo = new ScopeChooserCombo(myProject, true, true, FindSettings.getInstance().getDefaultScopeName());
    Disposer.register(myDisposable, myScopeCombo);
    scopePanel.add(myScopeCombo, gbConstraints);


    ButtonGroup bgScope = new ButtonGroup();
    bgScope.add(myRbDirectory);
    bgScope.add(myRbProject);
    bgScope.add(myRbModule);
    bgScope.add(myRbCustomScope);

    myRbProject.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
      }
    });
    myRbCustomScope.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
      }
    });

    myRbDirectory.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
        myDirectoryComboBox.getEditor().getEditorComponent().requestFocusInWindow();
      }
    });

    myRbModule.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        validateScopeControls();
        validateFindButton();
        myModuleComboBox.requestFocusInWindow();
      }
    });

    mySelectDirectoryButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        VirtualFile[] files = FileChooser.chooseFiles(myProject, descriptor);
        if (files.length != 0) {
          myDirectoryComboBox.setSelectedItem(files[0].getPresentableUrl());
          validateFindButton();
        }
      }
    });

    return scopePanel;
  }

  private static StateRestoringCheckBox createCheckbox(String message) {
    final StateRestoringCheckBox cb = new StateRestoringCheckBox(message);
    cb.setFocusable(false);
    return cb;
  }

  private static StateRestoringCheckBox createCheckbox(boolean selected, String message) {
    final StateRestoringCheckBox cb = new StateRestoringCheckBox(message, selected);
    cb.setFocusable(false);
    return cb;
  }

  private void validateScopeControls() {
    if (myRbDirectory.isSelected()) {
      myCbWithSubdirectories.makeSelectable();
    }
    else {
      myCbWithSubdirectories.makeUnselectable(myCbWithSubdirectories.isSelected());
    }
    myDirectoryComboBox.setEnabled(myRbDirectory.isSelected());
    mySelectDirectoryButton.setEnabled(myRbDirectory.isSelected());

    myModuleComboBox.setEnabled(myRbModule.isSelected());
    myScopeCombo.setEnabled(myRbCustomScope.isSelected());
  }

  private JPanel createScopePanel() {
    JPanel scopePanel = new JPanel();
    scopePanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.scope.group")));
    scopePanel.setLayout(new BoxLayout(scopePanel, BoxLayout.Y_AXIS));

    myRbGlobal = new JRadioButton(FindBundle.message("find.scope.global.radio"), true);
    scopePanel.add(myRbGlobal);
    myRbSelectedText = new JRadioButton(FindBundle.message("find.scope.selected.text.radio"));
    scopePanel.add(myRbSelectedText);
    ButtonGroup bgScope = new ButtonGroup();
    bgScope.add(myRbGlobal);
    bgScope.add(myRbSelectedText);

    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myRbGlobal.addActionListener(actionListener);
    myRbSelectedText.addActionListener(actionListener);

    return scopePanel;
  }

  private JPanel createOriginPanel() {
    JPanel originPanel = new JPanel();
    originPanel.setBorder(IdeBorderFactory.createTitledBorder(FindBundle.message("find.origin.group")));
    originPanel.setLayout(new BoxLayout(originPanel, BoxLayout.Y_AXIS));

    myRbFromCursor = new JRadioButton(FindBundle.message("find.origin.from.cursor.radio"), true);
    originPanel.add(myRbFromCursor);
    myRbEntireScope = new JRadioButton(FindBundle.message("find.origin.entire.scope.radio"));
    originPanel.add(myRbEntireScope);
    ButtonGroup bgOrigin = new ButtonGroup();
    bgOrigin.add(myRbFromCursor);
    bgOrigin.add(myRbEntireScope);

    return originPanel;
  }

  private String getStringToFind() {
    return (String)myInputComboBox.getEditor().getItem();
  }
  private String getStringToReplace() {
    return (String)myReplaceComboBox.getEditor().getItem();
  }

  private String getDirectory() {
    return (String)myDirectoryComboBox.getSelectedItem();
  }

  private static void setStringsToComboBox(String[] strings, ComboBox combo, String selected) {
    if (combo.getItemCount() > 0){
      combo.removeAllItems();
    }
    if (selected != null && selected.indexOf('\n') < 0) {
      strings = ArrayUtil.remove(strings, selected);
      strings = ArrayUtil.append(strings, selected);
    }
    for(int i = strings.length - 1; i >= 0; i--){
      combo.addItem(strings[i]);
    }
  }

  private void setDirectories(ArrayList strings, String directoryName) {
    if (myDirectoryComboBox.getItemCount() > 0){
      myReplaceComboBox.removeAllItems();
    }
    if (directoryName != null && directoryName.length() > 0){
      if (strings.contains(directoryName)){
        strings.remove(directoryName);
      }
      myDirectoryComboBox.addItem(directoryName);
    }
    for(int i = strings.size() - 1; i >= 0; i--){
      myDirectoryComboBox.addItem(strings.get(i));
    }
    if (myDirectoryComboBox.getItemCount() == 0){
      myDirectoryComboBox.addItem("");
    }
  }

  private void applyTo(FindModel model) {
    FindSettings findSettings = FindSettings.getInstance();
    model.setCaseSensitive(myCbCaseSensitive.isSelected());
    findSettings.setCaseSensitive(myCbCaseSensitive.isSelected());

    if (model.isReplaceState()) {
      model.setPreserveCase(myCbPreserveCase.isSelected());
      findSettings.setPreserveCase(myCbPreserveCase.isSelected());
    }

    model.setWholeWordsOnly(myCbWholeWordsOnly.isSelected());
    findSettings.setWholeWordsOnly(myCbWholeWordsOnly.isSelected());
    model.setInStringLiteralsOnly(myCbInStringLiteralsOnly.isSelected());
    findSettings.setInStringLiteralsOnly(myCbInStringLiteralsOnly.isSelected());

    model.setInCommentsOnly(myCbInCommentsOnly.isSelected());
    findSettings.setInCommentsOnly(myCbInCommentsOnly.isSelected());

    model.setRegularExpressions(myCbRegularExpressions.isSelected());
    findSettings.setRegularExpressions(myCbRegularExpressions.isSelected());
    model.setStringToFind(getStringToFind());

    if (model.isReplaceState()){
      model.setPromptOnReplace(true);
      model.setReplaceAll(false);
      String stringToReplace = getStringToReplace();
      if (stringToReplace == null){
        stringToReplace = "";
      }
      model.setStringToReplace(StringUtil.convertLineSeparators(stringToReplace));
    }

    if (!model.isMultipleFiles()){
      model.setForward(myRbForward.isSelected());
      findSettings.setForward(myRbForward.isSelected());
      model.setFromCursor(myRbFromCursor.isSelected());
      findSettings.setFromCursor(myRbFromCursor.isSelected());
      model.setGlobal(myRbGlobal.isSelected());
      findSettings.setGlobal(myRbGlobal.isSelected());
    }
    else{
      if (myCbToOpenInNewTab != null){
        model.setOpenInNewTab(myCbToOpenInNewTab.isSelected());
      }

      model.setProjectScope(myRbProject.isSelected());
      model.setDirectoryName(null);
      model.setModuleName(null);
      model.setCustomScopeName(null);
      model.setCustomScope(null);
      model.setCustomScope(false);

      if (myRbDirectory.isSelected()) {
        String directory = getDirectory();
        model.setDirectoryName(directory == null ? "" : directory);
        model.setWithSubdirectories(myCbWithSubdirectories.isSelected());
        findSettings.setWithSubdirectories(myCbWithSubdirectories.isSelected());
      }
      else if (myRbModule.isSelected()) {
        model.setModuleName((String)myModuleComboBox.getSelectedItem());
      }
      else if (myRbCustomScope.isSelected()) {
        SearchScope selectedScope = myScopeCombo.getSelectedScope();
        String customScopeName = selectedScope == null ? null : selectedScope.getDisplayName();
        model.setCustomScopeName(customScopeName);
        model.setCustomScope(selectedScope == null ? null : selectedScope);
        model.setCustomScope(true);
        findSettings.setCustomScope(customScopeName);
      }
    }
  }


  private void initByModel() {
    myCbCaseSensitive.setSelected(myModel.isCaseSensitive());
    myCbWholeWordsOnly.setSelected(myModel.isWholeWordsOnly());
    myCbInStringLiteralsOnly.setSelected(myModel.isInStringLiteralsOnly());
    myCbInCommentsOnly.setSelected(myModel.isInCommentsOnly());
    myCbRegularExpressions.setSelected(myModel.isRegularExpressions());

    if (myModel.isMultipleFiles()) {
      final String dirName = myModel.getDirectoryName();
      setDirectories(FindSettings.getInstance().getRecentDirectories(), dirName);

      if (!StringUtil.isEmptyOrSpaces(dirName)) {
        VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(dirName);
        if (dir != null) {
          Module module = ModuleUtil.findModuleForFile(dir, myProject);
          if (module != null) {
            myModuleComboBox.setSelectedItem(module.getName());
          }
        }
      }
      if (myModel.isCustomScope()) {
        myRbCustomScope.setSelected(true);

        myScopeCombo.setEnabled(true);
        myScopeCombo.init(myProject, true, true, myModel.getCustomScopeName());

        myCbWithSubdirectories.setEnabled(false);
        myDirectoryComboBox.setEnabled(false);
        mySelectDirectoryButton.setEnabled(false);
        myModuleComboBox.setEnabled(false);
      } else if (myModel.isProjectScope()) {
        myRbProject.setSelected(true);

        myCbWithSubdirectories.setEnabled(false);
        myDirectoryComboBox.setEnabled(false);
        mySelectDirectoryButton.setEnabled(false);
        myModuleComboBox.setEnabled(false);
        myScopeCombo.setEnabled(false);
      }
      else if (dirName != null) {
        myRbDirectory.setSelected(true);
        myCbWithSubdirectories.setEnabled(true);
        myDirectoryComboBox.setEnabled(true);
        mySelectDirectoryButton.setEnabled(true);
        myModuleComboBox.setEnabled(false);
        myScopeCombo.setEnabled(false);
      }
      else if (myModel.getModuleName() != null) {
        myRbModule.setSelected(true);

        myCbWithSubdirectories.setEnabled(false);
        myDirectoryComboBox.setEnabled(false);
        mySelectDirectoryButton.setEnabled(false);
        myModuleComboBox.setEnabled(true);
        myModuleComboBox.setSelectedItem(myModel.getModuleName());
        myScopeCombo.setEnabled(false);

        // force showing even if we have only one module
        myRbModule.setVisible(true);
        myModuleComboBox.setVisible(true);
      }
      else {
        assert false : myModel;
      }

      myCbWithSubdirectories.setSelected(myModel.isWithSubdirectories());

      if (myModel.getFileFilter()!=null && myModel.getFileFilter().length() > 0) {
        myFileFilter.setSelectedItem(myModel.getFileFilter());
        myFileFilter.setEnabled(true);
        useFileFilter.setSelected(true);
      }
    }
    else {
      if (myModel.isForward()){
        myRbForward.setSelected(true);
      }
      else{
        myRbBackward.setSelected(true);
      }

      if (myModel.isFromCursor()){
        myRbFromCursor.setSelected(true);
      }
      else{
        myRbEntireScope.setSelected(true);
      }

      if (myModel.isGlobal()){
        myRbGlobal.setSelected(true);
      }
      else{
        myRbSelectedText.setSelected(true);
      }
    }

    setStringsToComboBox(FindSettings.getInstance().getRecentFindStrings(), myInputComboBox, myModel.getStringToFind());
    if (myModel.isReplaceState()){
      myCbPreserveCase.setSelected(myModel.isPreserveCase());
      setStringsToComboBox(FindSettings.getInstance().getRecentReplaceStrings(), myReplaceComboBox, myModel.getStringToReplace());
    }
    updateControls();
    validateFindButton();
  }
}

