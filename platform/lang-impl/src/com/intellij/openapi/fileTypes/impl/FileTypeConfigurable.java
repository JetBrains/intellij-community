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

package com.intellij.openapi.fileTypes.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.options.*;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.templateLanguages.TemplateDataLanguagePatterns;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.PairConvertor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class FileTypeConfigurable extends BaseConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private RecognizedFileTypes myRecognizedFileType;
  private PatternsPanel myPatterns;
  private FileTypePanel myFileTypePanel;
  private HashSet<FileType> myTempFileTypes;
  private final FileTypeManagerImpl myManager;
  private FileTypeAssocTable<FileType> myTempPatternsTable;
  private final Map<FileNameMatcher, FileType> myReassigned = new THashMap<>();
  private FileTypeAssocTable<Language> myTempTemplateDataLanguages;
  private final Map<UserFileType, UserFileType> myOriginalToEditedMap = new HashMap<>();

  public FileTypeConfigurable(FileTypeManager fileTypeManager) {
    myManager = (FileTypeManagerImpl)fileTypeManager;
  }

  @Override
  public String getDisplayName() {
    return FileTypesBundle.message("filetype.settings.title");
  }

  @Override
  public JComponent createComponent() {
    myFileTypePanel = new FileTypePanel();
    myRecognizedFileType = myFileTypePanel.myRecognizedFileType;
    myPatterns = myFileTypePanel.myPatterns;
    myRecognizedFileType.attachActions(this);
    myRecognizedFileType.myFileTypesList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(@Nullable ListSelectionEvent e) {
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
      @Override
      public int compare(@NotNull Object o1, @NotNull Object o2) {
        FileType fileType1 = (FileType)o1;
        FileType fileType2 = (FileType)o2;
        return fileType1.getDescription().compareToIgnoreCase(fileType2.getDescription());
      }
    });
    myRecognizedFileType.setFileTypes(types);
  }

  private static FileType[] getModifiableFileTypes() {
    FileType[] registeredFileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    ArrayList<FileType> result = new ArrayList<>();
    for (FileType fileType : registeredFileTypes) {
      if (!fileType.isReadOnly()) result.add(fileType);
    }
    return result.toArray(new FileType[result.size()]);
  }

  @Override
  public void apply() {
    Set<UserFileType> modifiedUserTypes = myOriginalToEditedMap.keySet();
    for (UserFileType oldType : modifiedUserTypes) {
      oldType.copyFrom(myOriginalToEditedMap.get(oldType));
    }
    myOriginalToEditedMap.clear();

    ApplicationManager.getApplication().runWriteAction(() -> {
      if (!myManager.isIgnoredFilesListEqualToCurrent(myFileTypePanel.myIgnoreFilesField.getText())) {
        myManager.setIgnoredFilesList(myFileTypePanel.myIgnoreFilesField.getText());
      }
      myManager.setPatternsTable(myTempFileTypes, myTempPatternsTable);
      for (FileNameMatcher matcher : myReassigned.keySet()) {
        myManager.getRemovedMappings().put(matcher, Pair.create(myReassigned.get(matcher), true));
      }

      TemplateDataLanguagePatterns.getInstance().setAssocTable(myTempTemplateDataLanguages);
    });
  }

  @Override
  public void reset() {
    myTempPatternsTable = myManager.getExtensionMap().copy();
    myTempTemplateDataLanguages = TemplateDataLanguagePatterns.getInstance().getAssocTable();

    myTempFileTypes = new HashSet<>(Arrays.asList(getModifiableFileTypes()));
    myOriginalToEditedMap.clear();

    updateFileTypeList();
    updateExtensionList();

    myFileTypePanel.myIgnoreFilesField.setText(myManager.getIgnoredFilesList());
  }

  @Override
  public boolean isModified() {
    if (!myManager.isIgnoredFilesListEqualToCurrent(myFileTypePanel.myIgnoreFilesField.getText())) return true;
    HashSet<FileType> types = new HashSet<>(Arrays.asList(getModifiableFileTypes()));
    return !myTempPatternsTable.equals(myManager.getExtensionMap()) || !myTempFileTypes.equals(types) ||
           !myOriginalToEditedMap.isEmpty() ||
           !myTempTemplateDataLanguages.equals(TemplateDataLanguagePatterns.getInstance().getAssocTable());
  }

  @Override
  public void disposeUIResources() {
    if (myFileTypePanel != null) myFileTypePanel.dispose();
    myFileTypePanel = null;
    myRecognizedFileType = null;
    myPatterns = null;
  }

  private static class ExtensionRenderer extends DefaultListCellRenderer {
    @NotNull
    @Override
    public Component getListCellRendererComponent(@NotNull JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(" " + getText());
      return this;
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(0, 20);
    }
  }

  private void updateExtensionList() {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;
    List<String> extensions = new ArrayList<>();

    for (FileNameMatcher assoc : myTempPatternsTable.getAssociations(type)) {
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
    TypeEditor editor = new TypeEditor(myRecognizedFileType.myFileTypesList, ftToEdit, FileTypesBundle.message("filetype.edit.existing.title"));
    if (editor.showAndGet()) {
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
    return fileType instanceof AbstractFileType; //todo: add API for canBeModified
  }

  private void addFileType() {
    //TODO: support adding binary file types...
    AbstractFileType type = new AbstractFileType(new SyntaxTable());
    TypeEditor<AbstractFileType> editor =
      new TypeEditor<>(myRecognizedFileType.myFileTypesList, type, FileTypesBundle.message("filetype.edit.new.title"));
    if (editor.showAndGet()) {
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
      item == null
      ? FileTypesBundle.message("filetype.edit.add.pattern.title")
      : FileTypesBundle.message("filetype.edit.edit.pattern.title");

    final Language oldLanguage = item == null ? null : myTempTemplateDataLanguages.findAssociatedFileType(item);
    final FileTypePatternDialog dialog = new FileTypePatternDialog(item, type, oldLanguage);
    final DialogBuilder builder = new DialogBuilder(myPatterns);
    builder.setPreferredFocusComponent(dialog.getPatternField());
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
          Messages.showMessageDialog(myPatterns.myPatternsList,
                                     FileTypesBundle.message("filetype.edit.add.pattern.exists.error", registeredFileType.getDescription()),
                                     title, Messages.getErrorIcon());
          return;
        }
        else {
          if (Messages.OK == Messages.showOkCancelDialog(myPatterns.myPatternsList, FileTypesBundle.message("filetype.edit.add.pattern.exists.message",
                                                                                               registeredFileType.getDescription()),
                                               FileTypesBundle.message("filetype.edit.add.pattern.exists.title"),
                                               FileTypesBundle.message("filetype.edit.add.pattern.reassign.button"),
                                               CommonBundle.getCancelButtonText(), Messages.getQuestionIcon())) {
            myTempPatternsTable.removeAssociation(matcher, registeredFileType);
            if (oldLanguage != null) {
              myTempTemplateDataLanguages.removeAssociation(matcher, oldLanguage);
            }
            myReassigned.put(matcher, registeredFileType);
          }
          else {
            return;
          }
        }
      }

      if (item != null) {
        final FileNameMatcher oldMatcher = FileTypeManager.parseFromString(item);
        myTempPatternsTable.removeAssociation(oldMatcher, type);
        if (oldLanguage != null) {
          myTempTemplateDataLanguages.removeAssociation(oldMatcher, oldLanguage);
        }
      }
      myTempPatternsTable.addAssociation(matcher, type);
      myTempTemplateDataLanguages.addAssociation(matcher, dialog.getTemplateDataLanguage());

      updateExtensionList();
      final int index = myPatterns.getListModel().indexOf(matcher.getPresentableString());
      if (index >= 0) {
        ScrollingUtil.selectItem(myPatterns.myPatternsList, index);
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

  @Override
  public String getHelpTopic() {
    return "preferences.fileTypes";
  }

  public static class RecognizedFileTypes extends JPanel {
    private final JList myFileTypesList;
    private final MySpeedSearch mySpeedSearch;
    private FileTypeConfigurable myController;

    public RecognizedFileTypes() {
      super(new BorderLayout());

      myFileTypesList = new JBList(new DefaultListModel());
      myFileTypesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myFileTypesList.setCellRenderer(new FileTypeRenderer(new FileTypeRenderer.FileTypeListProvider() {
        @Override
        public Iterable<FileType> getCurrentFileTypeList() {
          ArrayList<FileType> result = new ArrayList<>();
          for (int i = 0; i < myFileTypesList.getModel().getSize(); i++) {
            result.add((FileType)myFileTypesList.getModel().getElementAt(i));
          }
          return result;
        }
      }));

      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(MouseEvent e) {
          myController.editFileType();
          return true;
        }
      }.installOn(myFileTypesList);

      ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myFileTypesList)
        .setAddAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            myController.addFileType();
          }
        })
        .setRemoveAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            myController.removeFileType();
          }
        })
        .setEditAction(new AnActionButtonRunnable() {
          @Override
          public void run(AnActionButton button) {
            myController.editFileType();
          }
        })
        .setEditActionUpdater(new AnActionButtonUpdater() {
          @Override
          public boolean isEnabled(AnActionEvent e) {
            final FileType fileType = getSelectedFileType();
            return canBeModified(fileType);
          }
        })
        .setRemoveActionUpdater(new AnActionButtonUpdater() {
          @Override
          public boolean isEnabled(AnActionEvent e) {
            return canBeModified(getSelectedFileType());
          }
        })
        .disableUpDownActions();

      add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
      setBorder(IdeBorderFactory.createTitledBorder(FileTypesBundle.message("filetype.recognized.group"), false));

      mySpeedSearch = new MySpeedSearch(myFileTypesList);
    }

    private static class MySpeedSearch extends MultipleTraitsListSpeedSearch {
      private FileTypeConfigurable myController;
      private Object myCurrentType;
      private String myExtension;

      private MySpeedSearch(JList component) {
        super(component, new ArrayList<>());
        initConvertors();
      }

      @Override
      protected void selectElement(Object element, String selectedText) {
        super.selectElement(element, selectedText);
        if (myCurrentType != null && myCurrentType.equals(element) && myController != null) {
          myController.myPatterns.select(myExtension);
        }
      }

      private void initConvertors() {
        final PairConvertor<Object, String, Boolean> simpleConvertor = (element, s) -> {
          String value = element.toString();
          if (element instanceof FileType) {
             value = ((FileType)element).getDescription();
          }
          return getComparator().matchingFragments(s, value) != null;
        };
        final PairConvertor<Object, String, Boolean> byExtensionsConvertor = (element, s) -> {
          if (element instanceof FileType && myCurrentType != null) {
            return myCurrentType.equals(element);
          }
          return false;
        };
        myOrderedConvertors.add(simpleConvertor);
        myOrderedConvertors.add(byExtensionsConvertor);
      }

      @Override
      protected void onSearchFieldUpdated(String s) {
        if (myController == null || myController.myTempPatternsTable == null) return;
        int index = s.lastIndexOf('.');
        if (index < 0) {
          s = "." + s;
        }
        myCurrentType = myController.myTempPatternsTable.findAssociatedFileType(s);
        if (myCurrentType != null) {
          myExtension = s;
        } else {
          myExtension = null;
        }
      }
    }

    public void attachActions(final FileTypeConfigurable controller) {
      myController = controller;
      mySpeedSearch.myController = controller;
    }

    public FileType getSelectedFileType() {
      return (FileType)myFileTypesList.getSelectedValue();
    }

    public JComponent getComponent() {
      return this;
    }

    public void setFileTypes(FileType[] types) {
      DefaultListModel listModel = (DefaultListModel)myFileTypesList.getModel();
      listModel.clear();
      for (FileType type : types) {
        if (type != FileTypes.UNKNOWN) {
          listModel.addElement(type);
        }
      }
      ScrollingUtil.ensureSelectionExists(myFileTypesList);
    }

    public int getSelectedIndex() {
      return myFileTypesList.getSelectedIndex();
    }

    public void selectFileType(FileType fileType) {
      myFileTypesList.setSelectedValue(fileType, true);
      myFileTypesList.requestFocus();
    }
  }

  public static class PatternsPanel extends JPanel {
    private final JBList myPatternsList;
    private FileTypeConfigurable myController;

    public PatternsPanel() {
      super(new BorderLayout());
      myPatternsList = new JBList(new DefaultListModel());
      myPatternsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myPatternsList.setCellRenderer(new ExtensionRenderer());
      myPatternsList.getEmptyText().setText(FileTypesBundle.message("filetype.settings.no.patterns"));

      add(ToolbarDecorator.createDecorator(myPatternsList)
            .setAddAction(new AnActionButtonRunnable() {
              @Override
              public void run(AnActionButton button) {
                myController.addPattern();
              }
            }).setEditAction(new AnActionButtonRunnable() {
              @Override
              public void run(AnActionButton button) {
                myController.editPattern();
              }
            }).setRemoveAction(new AnActionButtonRunnable() {
              @Override
              public void run(AnActionButton button) {
                myController.removePattern();
              }
            }).disableUpDownActions().createPanel(), BorderLayout.CENTER);

      setBorder(IdeBorderFactory.createTitledBorder(FileTypesBundle.message("filetype.registered.patterns.group"), false));
    }

    public void attachActions(final FileTypeConfigurable controller) {
      myController = controller;
    }

    public JComponent getComponent() {
      return this;
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
      ScrollingUtil.ensureSelectionExists(myPatternsList);
    }

    public void addPatternAndSelect(String pattern) {
      addPattern(pattern);
      ScrollingUtil.selectItem(myPatternsList, getListModel().getSize() - 1);
    }

    public void select(final String pattern) {
      for (int i = 0; i < myPatternsList.getItemsCount(); i++) {
        final Object at = myPatternsList.getModel().getElementAt(i);
        if (at instanceof String) {
          final FileNameMatcher matcher = FileTypeManager.parseFromString((String)at);
          if (FileNameMatcherEx.acceptsCharSequence(matcher, pattern)) {
            ScrollingUtil.selectItem(myPatternsList, i);
            return;
          }
        }
      }
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

    @Override
    protected void init() {
      super.init();
      myEditor.resetFrom(myFileType);
    }

    @Override
    protected JComponent createCenterPanel() {
      return myEditor.getComponent();
    }

    @Override
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

  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
