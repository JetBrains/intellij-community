/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.openapi.fileTypes.impl;

import com.intellij.CommonBundle;
import com.intellij.application.options.ExportSchemeAction;
import com.intellij.application.options.SchemesToImportPopup;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.ide.highlighter.custom.impl.ReadFileType;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.options.*;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.templateLanguages.TemplateDataLanguagePatterns;
import com.intellij.ui.ListScrollingUtil;
import com.intellij.ui.ListUtil;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class FileTypeConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private RecognizedFileTypes myRecognizedFileType;
  private PatternsPanel myPatterns;
  private FileTypePanel myFileTypePanel;
  private HashSet<FileType> myTempFileTypes;
  private final FileTypeManagerImpl myManager;
  private FileTypeAssocTable<FileType> myTempPatternsTable;
  private FileTypeAssocTable<Language> myTempTemplateDataLanguages;
  private final Map<UserFileType, UserFileType> myOriginalToEditedMap = new HashMap<UserFileType, UserFileType>();

  public FileTypeConfigurable(FileTypeManager fileTypeManager) {
    myManager = (FileTypeManagerImpl)fileTypeManager;
  }

  public String getDisplayName() {
    return FileTypesBundle.message("filetype.settings.title");
  }

  public JComponent createComponent() {
    myFileTypePanel = new FileTypePanel();
    myRecognizedFileType = myFileTypePanel.myRecognizedFileType;
    myPatterns = myFileTypePanel.myPatterns;
    myRecognizedFileType.attachActions(this);
    myRecognizedFileType.myFileTypesList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateExtensionList();
      }
    });
    myPatterns.attachActions(this);
    myFileTypePanel.myIgnoreFilesField.setColumns(30);
    return myFileTypePanel.getComponent();
  }

  private void updateFileTypeList() {
    FileType[] types = myTempFileTypes.toArray(new FileType[myTempFileTypes.size()]);
    Arrays.sort(types, new Comparator() {
      public int compare(Object o1, Object o2) {
        FileType fileType1 = (FileType)o1;
        FileType fileType2 = (FileType)o2;
        return fileType1.getDescription().compareToIgnoreCase(fileType2.getDescription());
      }
    });
    myRecognizedFileType.setFileTypes(types);
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableFileTypes.png");
  }

  private static FileType[] getModifiableFileTypes() {
    FileType[] registeredFileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    ArrayList<FileType> result = new ArrayList<FileType>();
    for (FileType fileType : registeredFileTypes) {
      if (!fileType.isReadOnly()) result.add(fileType);
    }
    return result.toArray(new FileType[result.size()]);
  }

  public void apply() throws ConfigurationException {
    Set<UserFileType> modifiedUserTypes = myOriginalToEditedMap.keySet();
    for (UserFileType oldType : modifiedUserTypes) {
      UserFileType newType = myOriginalToEditedMap.get(oldType);
      oldType.copyFrom(newType);
    }
    myOriginalToEditedMap.clear();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        if (!myManager.isIgnoredFilesListEqualToCurrent(myFileTypePanel.myIgnoreFilesField.getText())) {
          myManager.setIgnoredFilesList(myFileTypePanel.myIgnoreFilesField.getText());
        }
        myManager.setPatternsTable(myTempFileTypes, myTempPatternsTable);

        TemplateDataLanguagePatterns.getInstance().setAssocTable(myTempTemplateDataLanguages);
      }
    });
  }

  public void reset() {
    myTempPatternsTable = myManager.getExtensionMap().copy();
    myTempTemplateDataLanguages = TemplateDataLanguagePatterns.getInstance().getAssocTable();
    
    myTempFileTypes = new HashSet<FileType>(Arrays.asList(getModifiableFileTypes()));
    myOriginalToEditedMap.clear();

    updateFileTypeList();
    updateExtensionList();
    
    myFileTypePanel.myIgnoreFilesField.setText(myManager.getIgnoredFilesList());
  }

  public boolean isModified() {
    if (!myManager.isIgnoredFilesListEqualToCurrent(myFileTypePanel.myIgnoreFilesField.getText())) return true;
    HashSet types = new HashSet(Arrays.asList(getModifiableFileTypes()));
    return !myTempPatternsTable.equals(myManager.getExtensionMap()) || !myTempFileTypes.equals(types) ||
           !myOriginalToEditedMap.isEmpty() || !myTempTemplateDataLanguages.equals(TemplateDataLanguagePatterns.getInstance().getAssocTable());
  }

  public void disposeUIResources() {
    if (myFileTypePanel != null) myFileTypePanel.dispose();
    myFileTypePanel = null;
    myRecognizedFileType = null;
    myPatterns = null;
  }

  private static class ExtensionRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(" " + getText());
      return this;
    }

    public Dimension getPreferredSize() {
      return new Dimension(0, 20);
    }
  }

  private void updateExtensionList() {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;
    List<String> extensions = new ArrayList<String>();

    final List<FileNameMatcher> assocs = myTempPatternsTable.getAssociations(type);
    for (FileNameMatcher assoc : assocs) {
      extensions.add(assoc.getPresentableString());
    }

    myPatterns.clearList();
    Collections.sort(extensions);
    for (String extension : extensions) {
      myPatterns.addPattern(extension);
    }
    myPatterns.ensureSelectionExists();
  }

  private void editFileType() {
    FileType fileType = myRecognizedFileType.getSelectedFileType();
    if (!canBeModified(fileType)) return;
    UserFileType ftToEdit = myOriginalToEditedMap.get(fileType);
    if (ftToEdit == null) ftToEdit = ((UserFileType)fileType).clone();
    TypeEditor editor =
      new TypeEditor(myRecognizedFileType.myEditButton, ftToEdit, FileTypesBundle.message("filetype.edit.existing.title"));
    editor.show();
    if (editor.isOK()) {
      myOriginalToEditedMap.put((UserFileType)fileType, ftToEdit);
    }
  }

  private void removeFileType() {
    FileType fileType = myRecognizedFileType.getSelectedFileType();
    if (fileType == null) return;
    myTempFileTypes.remove(fileType);
    myOriginalToEditedMap.remove(fileType);

    myTempPatternsTable.removeAllAssociations(fileType);

    updateFileTypeList();
    updateExtensionList();
  }

  private static boolean canBeModified(FileType fileType) {
    return fileType instanceof AbstractFileType && !(fileType instanceof ImportedFileType); //todo: add API for canBeModified
  }

  private void addFileType() {
    //TODO: support adding binary file types...
    AbstractFileType type = new AbstractFileType(new SyntaxTable());
    TypeEditor<AbstractFileType> editor = new TypeEditor<AbstractFileType>(myRecognizedFileType.myAddButton, type, FileTypesBundle.message("filetype.edit.new.title"));
    editor.show();
    if (editor.isOK()) {
      myTempFileTypes.add(type);
      updateFileTypeList();
      updateExtensionList();
      myRecognizedFileType.selectFileType(type);
    }
  }

  private void editPattern() {
    final String item = myPatterns.getSelectedItem();
    if (item == null) return;

    editPattern(item);
  }

  private void editPattern(@Nullable final String item) {
    final FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;

    final String title =
      item == null ? FileTypesBundle.message("filetype.edit.add.pattern.title") : FileTypesBundle.message("filetype.edit.edit.pattern.title");

    final Language oldLanguage = item == null ? null : myTempTemplateDataLanguages.findAssociatedFileType(item);
    final FileTypePatternDialog dialog = new FileTypePatternDialog(item, type, oldLanguage);
    final DialogBuilder builder = new DialogBuilder(myPatterns);
    builder.setPreferedFocusComponent(dialog.getPatternField());
    builder.setCenterPanel(dialog.getMainPanel());
    builder.setTitle(title);
    builder.showModal(true);
    if (builder.getDialogWrapper().isOK()) {
      final String pattern = dialog.getPatternField().getText();
      if (StringUtil.isEmpty(pattern)) return;

      final FileNameMatcher matcher = FileTypeManager.parseFromString(pattern);
      FileType registeredFileType = findExistingFileType(matcher);
      if (registeredFileType != null && registeredFileType != type) {
        if (registeredFileType.isReadOnly()) {
          Messages.showMessageDialog(myPatterns.myAddButton,
                                     FileTypesBundle.message("filetype.edit.add.pattern.exists.error", registeredFileType.getDescription()),
                                     title, Messages.getErrorIcon());
          return;
        }
        else {
          if (0 == Messages.showDialog(myPatterns.myAddButton, FileTypesBundle.message("filetype.edit.add.pattern.exists.message",
                                                                                       registeredFileType.getDescription()),
                                       FileTypesBundle.message("filetype.edit.add.pattern.exists.title"),
                                       new String[]{
                                         FileTypesBundle.message("filetype.edit.add.pattern.reassign.button"),
                                         CommonBundle.getCancelButtonText()}, 0, Messages.getQuestionIcon())) {
            myTempPatternsTable.removeAssociation(matcher, registeredFileType);
            myTempTemplateDataLanguages.removeAssociation(matcher, oldLanguage);
          } else {
            return;
          }
        }
      }

      if (item != null) {
        final FileNameMatcher oldMatcher = FileTypeManager.parseFromString(item);
        myTempPatternsTable.removeAssociation(oldMatcher, type);
        myTempTemplateDataLanguages.removeAssociation(oldMatcher, oldLanguage);
      }
      myTempPatternsTable.addAssociation(matcher, type);
      myTempTemplateDataLanguages.addAssociation(matcher, dialog.getTemplateDataLanguage());

      updateExtensionList();
      final int index = myPatterns.getListModel().indexOf(matcher.getPresentableString());
      if (index >= 0) {
        ListScrollingUtil.selectItem(myPatterns.myPatternsList, index);
      }
      myPatterns.myPatternsList.requestFocus();
    }
  }

  private void addPattern() {
    editPattern(null);
  }

  @Nullable
  public FileType findExistingFileType(FileNameMatcher matcher) {
    FileType fileTypeByExtension = myTempPatternsTable.findAssociatedFileType(matcher);

    if (fileTypeByExtension != null && fileTypeByExtension != FileTypes.UNKNOWN) {
      return fileTypeByExtension;
    }
    FileType registeredFileType = FileTypeManager.getInstance().getFileTypeByExtension(matcher.getPresentableString());
    if (registeredFileType != FileTypes.UNKNOWN && registeredFileType.isReadOnly()) {
      return registeredFileType;
    }
    return null;
  }

  @Nullable
  public FileType addNewPattern(FileType type, String pattern) {
    FileNameMatcher matcher = FileTypeManager.parseFromString(pattern);
    final FileType existing = findExistingFileType(matcher);
    if (existing != null) {
      return existing;
    }

    myTempPatternsTable.addAssociation(matcher, type);
    myPatterns.addPatternAndSelect(pattern);
    myPatterns.myPatternsList.requestFocus();

    return null;
  }

  private void removePattern() {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;
    String extension = myPatterns.removeSelected();
    if (extension == null) return;
    FileNameMatcher matcher = FileTypeManager.parseFromString(extension);

    myTempPatternsTable.removeAssociation(matcher, type);
    myPatterns.myPatternsList.requestFocus();
  }

  public String getHelpTopic() {
    return "preferences.fileTypes";
  }

  public static class RecognizedFileTypes extends JPanel {
    private JList myFileTypesList;
    private JButton myAddButton;
    private JButton myEditButton;
    private JButton myRemoveButton;
    private JPanel myWholePanel;
    private JButton myExportButton;
    private JButton myImportButton;

    public RecognizedFileTypes() {
      super(new BorderLayout());
      add(myWholePanel, BorderLayout.CENTER);
      myFileTypesList.setCellRenderer(new FileTypeRenderer(myFileTypesList.getCellRenderer(), new FileTypeRenderer.FileTypeListProvider() {
        public Iterable<FileType> getCurrentFileTypeList() {
          ArrayList<FileType> result = new ArrayList<FileType>();
          for (int i = 0; i < myFileTypesList.getModel().getSize(); i++) {
            result.add((FileType)myFileTypesList.getModel().getElementAt(i));
          }
          return result;
        }
      }));
      myFileTypesList.setModel(new DefaultListModel());

      if (getSchemesManager().isImportAvailable()) {
        myImportButton.setVisible(true);
      }

      if (getSchemesManager().isExportAvailable()) {
        myExportButton.setVisible(true);
      }

    }

    private SchemesManager<FileType, AbstractFileType> getSchemesManager() {
      return ((FileTypeManagerEx)FileTypeManager.getInstance()).getSchemesManager();
    }

    public void attachActions(final FileTypeConfigurable controller) {
      myAddButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.addFileType();
        }
      });
      myEditButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.editFileType();
        }
      });
      myFileTypesList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          FileType fileType = getSelectedFileType();
          boolean b = canBeModified(fileType);
          myEditButton.setEnabled(b);
          myRemoveButton.setEnabled(b);
          boolean shared = getSchemesManager().isShared(fileType);
          myExportButton.setEnabled(b && !shared);
          if (shared) {
            myRemoveButton.setEnabled(true);
          }
        }
      });
      myRemoveButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.removeFileType();
        }
      });
      myFileTypesList.addMouseListener(new MouseAdapter() {
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) controller.editFileType();
        }
      });

      myImportButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          new SchemesToImportPopup<FileType, AbstractFileType>(myWholePanel){
            protected void onSchemeSelected(final AbstractFileType scheme) {
              controller.importFileType(scheme);
            }
          }.show(getSchemesManager(), collectRegisteredFileTypes());
        }
      });

      myExportButton.addActionListener(new ActionListener(){
        public void actionPerformed(final ActionEvent e) {
          FileType selected = (FileType)myFileTypesList.getSelectedValue();
          if (selected instanceof AbstractFileType) {
            ExportSchemeAction.doExport((AbstractFileType)selected, getSchemesManager());
          }
        }
      });
    }

    private Collection<FileType> collectRegisteredFileTypes() {
      HashSet<FileType> result = new HashSet<FileType>();
      for (int i = 0; i < myFileTypesList.getModel().getSize(); i++) {
        result.add((FileType)myFileTypesList.getModel().getElementAt(i));
      }
      return result;
    }

    public FileType getSelectedFileType() {
      return (FileType)myFileTypesList.getSelectedValue();
    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    public void setFileTypes(FileType[] types) {
      DefaultListModel listModel = (DefaultListModel)myFileTypesList.getModel();
      listModel.clear();
      for (FileType type : types) {
        if (type != FileTypes.UNKNOWN) {
          listModel.addElement(type);
        }
      }
      ListScrollingUtil.ensureSelectionExists(myFileTypesList);
    }

    public int getSelectedIndex() {
      return myFileTypesList.getSelectedIndex();
    }

    public void setSelectionIndex(int selectedIndex) {
      myFileTypesList.setSelectedIndex(selectedIndex);
    }

    public void selectFileType(FileType fileType) {
      myFileTypesList.setSelectedValue(fileType, true);
      myFileTypesList.requestFocus();
    }
  }

  private void importFileType(final FileType type) {
    ReadFileType readFileType = (ReadFileType)type;
    ImportedFileType actualType = new ImportedFileType(readFileType.getSyntaxTable(), readFileType.getExternalInfo());
    actualType.setDescription(readFileType.getDescription());
    actualType.setName(readFileType.getName());
    actualType.readOriginalMatchers(readFileType.getElement());
    for (FileNameMatcher matcher : actualType.getOriginalPatterns()) {
      myTempPatternsTable.addAssociation(matcher, actualType);
    }
    myTempFileTypes.add(actualType);
    updateFileTypeList();
    updateExtensionList();
    myRecognizedFileType.selectFileType(type);

  }

  public static class PatternsPanel extends JPanel {
    private JBList myPatternsList;
    private JButton myAddButton;
    private JButton myRemoveButton;
    private JPanel myWholePanel;
    private JButton myEditButton;

    public PatternsPanel() {
      super(new BorderLayout());
      add(myWholePanel, BorderLayout.CENTER);
      myPatternsList.setCellRenderer(new ExtensionRenderer());
      myPatternsList.setModel(new DefaultListModel());
      myPatternsList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e) {
          myRemoveButton.setEnabled(myPatternsList.getSelectedIndex() != -1 && getListModel().size() > 0);
        }
      });

      myPatternsList.getEmptyText().setText(FileTypesBundle.message("filetype.settings.no.patterns"));
    }

    public void attachActions(final FileTypeConfigurable controller) {
      myAddButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.addPattern();
        }
      });
      myEditButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.editPattern();
        }
      });

      myRemoveButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          controller.removePattern();
        }
      });

    }

    public JComponent getComponent() {
      return myWholePanel;
    }

    public void clearList() {
      getListModel().clear();
      myPatternsList.clearSelection();
    }

    private DefaultListModel getListModel() {
      return (DefaultListModel)myPatternsList.getModel();
    }

    public void addPattern(String pattern) {
      getListModel().addElement(pattern);
    }

    public void ensureSelectionExists() {
      ListScrollingUtil.ensureSelectionExists(myPatternsList);
    }

    public void addPatternAndSelect(String pattern) {
      addPattern(pattern);
      ListScrollingUtil.selectItem(myPatternsList, getListModel().getSize() - 1);
    }

    public boolean isListEmpty() {
      return getListModel().size() == 0;
    }

    public String removeSelected() {
      Object selectedValue = myPatternsList.getSelectedValue();
      if (selectedValue == null) return null;
      ListUtil.removeSelectedItems(myPatternsList);
      return (String)selectedValue;
    }

    public String getDefaultExtension() {
      return (String)getListModel().getElementAt(0);
    }

    public String getSelectedItem() {
      return (String)myPatternsList.getSelectedValue();
    }
  }

  private static class FileTypePanel {
    private JPanel myWholePanel;
    private RecognizedFileTypes myRecognizedFileType;
    private PatternsPanel myPatterns;
    private JTextField myIgnoreFilesField;

    public JComponent getComponent() {
      return myWholePanel;
    }

    public void dispose() {
      myRecognizedFileType.setFileTypes(FileType.EMPTY_ARRAY);
      myPatterns.clearList();
    }
  }

  private static class TypeEditor<T extends UserFileType<T>> extends DialogWrapper {
    private final T myFileType;
    private final SettingsEditor<T> myEditor;

    public TypeEditor(Component parent, T fileType, final String title) {
      super(parent, false);
      myFileType = fileType;
      myEditor = fileType.getEditor();
      setTitle(title);
      init();
      Disposer.register(myDisposable, myEditor);
    }

    protected void init() {
      super.init();
      myEditor.resetFrom(myFileType);
    }

    protected JComponent createCenterPanel() {
      return myEditor.getComponent();
    }

    protected void doOKAction() {
      try {
        myEditor.applyTo(myFileType);
      }
      catch (ConfigurationException e) {
        Messages.showErrorDialog(getContentPane(), e.getMessage(), e.getTitle());
        return;
      }
      super.doOKAction();
    }

    @Override
    protected String getHelpId() {
      return "reference.dialogs.newfiletype";
    }
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
