// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.associate.OSAssociateFileTypesUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.templateLanguages.TemplateDataLanguagePatterns;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.util.Pair.pair;

public final class FileTypeConfigurable implements SearchableConfigurable, Configurable.NoScroll, FileTypeSelectable {
  private static final Insets TITLE_INSETS = JBUI.insetsTop(8);

  private RecognizedFileTypesPanel myRecognizedFileType;
  private PatternsPanel myPatterns;
  private HashBangPanel myHashBangs;
  private FileTypePanel myFileTypePanel;
  private Set<FileType> myTempFileTypes;
  private FileTypeAssocTable<FileType> myTempPatternsTable;
  private FileTypeAssocTable<Language> myTempTemplateDataLanguages;
  @SuppressWarnings("rawtypes")
  private final Map<UserFileType, UserFileType> myOriginalToEditedMap = new HashMap<>();
  private FileType myFileTypeToPreselect;
  private IgnoredFilesAndFoldersPanel myIgnoreFilesPanel;

  @Override
  public String getDisplayName() {
    return FileTypesBundle.message("filetype.settings.title");
  }

  @Override
  public JComponent createComponent() {
    JBTabbedPane tabbedPane = new JBTabbedPane();
    myFileTypePanel = new FileTypePanel();
    myRecognizedFileType = new RecognizedFileTypesPanel();
    JBSplitter splitter = new JBSplitter(false, 0.3f);
    splitter.setFirstComponent(myRecognizedFileType);

    JPanel rightPanel = new JPanel(new BorderLayout());
    myPatterns = new PatternsPanel();
    myHashBangs = new HashBangPanel();
    rightPanel.add(myPatterns, BorderLayout.CENTER);
    rightPanel.add(myHashBangs, BorderLayout.SOUTH);
    splitter.setSecondComponent(rightPanel);

    myFileTypePanel.myUpperPanel.add(splitter, BorderLayout.CENTER);

    myRecognizedFileType.myFileTypesList.addListSelectionListener(__ -> updateExtensionList());
    myFileTypePanel.myAssociatePanel.setVisible(OSAssociateFileTypesUtil.isAvailable());
    myFileTypePanel.myAssociatePanel.setBorder(JBUI.Borders.emptyTop(16));
    myFileTypePanel.myAssociateButton.setText(
      FileTypesBundle.message("filetype.associate.button", ApplicationNamesInfo.getInstance().getFullProductName()));
    myFileTypePanel.myAssociateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        OSAssociateFileTypesUtil.chooseAndAssociate(
          new OSAssociateFileTypesUtil.Callback() {
            @Override
            public void beforeStart() {
              ApplicationManager.getApplication().invokeLater(()-> {
                myFileTypePanel.myAssociateButton.setEnabled(false);
                updateAssociateMessageLabel(
                  FileTypesBundle.message("filetype.associate.message.updating"), null);
              }, ModalityState.any());
            }

            @Override
            public void onSuccess(boolean isOsRestartRequired) {
              ApplicationManager.getApplication().invokeLater(()-> {
                myFileTypePanel.myAssociateButton.setEnabled(true);
                if (isOsRestartRequired) {
                  updateAssociateMessageLabel(
                    FileTypesBundle.message("filetype.associate.message.os.restart"), AllIcons.General.Warning);
                }
                else {
                  updateAssociateMessageLabel("", null);
                }
                showAssociationBalloon(
                  FileTypesBundle.message("filetype.associate.success.message", ApplicationInfo.getInstance().getFullApplicationName()),
                  HintUtil.getInformationColor());
              }, ModalityState.any());
            }

            @Override
            public void onFailure(@NotNull @Nls String errorMessage) {
              ApplicationManager.getApplication().invokeLater(()-> {
                myFileTypePanel.myAssociateButton.setEnabled(true);
                updateAssociateMessageLabel("", null);
                showAssociationBalloon(errorMessage, HintUtil.getErrorColor());
              }, ModalityState.any());
            }
          }
        );
      }
    });
    tabbedPane.add(FileTypesBundle.message("filetype.recognized.group"), myFileTypePanel.myWholePanel);

    myIgnoreFilesPanel = new IgnoredFilesAndFoldersPanel();
    tabbedPane.add(FileTypesBundle.message("filetype.ignore.group"), myIgnoreFilesPanel);
    return tabbedPane;
  }

  private void updateAssociateMessageLabel(@NotNull @Nls String message, @Nullable Icon icon) {
    myFileTypePanel.myAssociateMessageLabel.setText(message);
    myFileTypePanel.myAssociateMessageLabel.setIcon(icon);
  }

  private void showAssociationBalloon(@NotNull @Nls String message, @NotNull Color color) {
    Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(new JLabel(message))
      .setFillColor(color)
      .setHideOnKeyOutside(true)
      .createBalloon();
    JComponent component = myFileTypePanel.myAssociateButton;
    RelativePoint relativePoint = new RelativePoint(component, new Point(component.getWidth() / 2, component.getHeight() - JBUI.scale(10)));
    balloon.show(relativePoint, Balloon.Position.below);
  }

  private void updateFileTypeList() {
    List<FileType> types = ContainerUtil.filter(myTempFileTypes, fileType -> !fileType.isReadOnly());
    types.sort((o1, o2) -> o1.getDescription().compareToIgnoreCase(o2.getDescription()));
    myRecognizedFileType.setFileTypes(types);
  }

  @NotNull
  private static Set<FileType> getRegisteredFilesTypes() {
    return ContainerUtil.set(FileTypeManager.getInstance().getRegisteredFileTypes());
  }

  @Override
  public void apply() {
    copyTypeMap();

    FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
    ApplicationManager.getApplication().runWriteAction(() -> {
      if (!fileTypeManager.isIgnoredFilesListEqualToCurrent(myIgnoreFilesPanel.getValues())) {
        fileTypeManager.setIgnoredFilesList(myIgnoreFilesPanel.getValues());
      }
      fileTypeManager.setPatternsTable(myTempFileTypes, myTempPatternsTable);
      TemplateDataLanguagePatterns.getInstance().setAssocTable(myTempTemplateDataLanguages);
    });
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void copyTypeMap() {
    Set<UserFileType> modifiedUserTypes = myOriginalToEditedMap.keySet();
    for (UserFileType oldType : modifiedUserTypes) {
      oldType.copyFrom(myOriginalToEditedMap.get(oldType));
    }
    myOriginalToEditedMap.clear();
  }

  @Override
  public void reset() {
    FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
    myTempPatternsTable = fileTypeManager.getExtensionMap().copy();
    myTempTemplateDataLanguages = TemplateDataLanguagePatterns.getInstance().getAssocTable();

    myTempFileTypes = getRegisteredFilesTypes();
    myOriginalToEditedMap.clear();

    updateFileTypeList();
    updateExtensionList();

    myIgnoreFilesPanel.setValues(fileTypeManager.getIgnoredFilesList());
    if (myFileTypeToPreselect != null) {
      myRecognizedFileType.selectFileType(myFileTypeToPreselect);
    }
  }

  @Override
  public boolean isModified() {
    FileTypeManagerImpl fileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();
    if (!fileTypeManager.isIgnoredFilesListEqualToCurrent(myIgnoreFilesPanel.getValues())) {
      return true;
    }
    return !myTempPatternsTable.equals(fileTypeManager.getExtensionMap()) ||
           !myTempFileTypes.equals(getRegisteredFilesTypes()) ||
           !myOriginalToEditedMap.isEmpty() ||
           !myTempTemplateDataLanguages.equals(TemplateDataLanguagePatterns.getInstance().getAssocTable());
  }

  @Override
  public void disposeUIResources() {
    if (myFileTypePanel != null) {
      myRecognizedFileType.setFileTypes(Collections.emptyList());
      myPatterns.clearList();
      myHashBangs.clearList();
    }
    myFileTypePanel = null;
    myRecognizedFileType = null;
    myPatterns = null;
    myHashBangs = null;
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
      return new JBDimension(0, 20);
    }
  }

  private void updateExtensionList() {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;
    myPatterns.refill(myTempPatternsTable.getAssociations(type));
    myHashBangs.refill(myTempPatternsTable.getHashBangPatterns(type));
  }

  private void editFileType() {
    FileType fileType = myRecognizedFileType.getSelectedFileType();
    if (!canBeModified(fileType)) return;

    UserFileType<?> userFileType = (UserFileType<?>)fileType;
    UserFileType<?> ftToEdit = myOriginalToEditedMap.get(userFileType);
    if (ftToEdit == null) ftToEdit = userFileType.clone();
    @SuppressWarnings({"unchecked", "rawtypes"}) TypeEditor editor = new TypeEditor(myRecognizedFileType.myFileTypesList, ftToEdit, FileTypesBundle.message("filetype.edit.existing.title"));
    if (editor.showAndGet()) {
      FileTypeConfigurableInteractions.fileTypeEdited.log();
      myOriginalToEditedMap.put(userFileType, ftToEdit);
    }
  }

  private void removeFileType() {
    FileType fileType = myRecognizedFileType.getSelectedFileType();
    if (fileType == null) return;
    FileTypeConfigurableInteractions.fileTypeRemoved.log();

    myTempFileTypes.remove(fileType);
    if (fileType instanceof UserFileType) {
      myOriginalToEditedMap.remove(fileType);
    }
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
    TypeEditor<AbstractFileType> editor = new TypeEditor<>(myRecognizedFileType.myFileTypesList, type, FileTypesBundle.message("filetype.edit.new.title"));
    if (editor.showAndGet()) {
      FileTypeConfigurableInteractions.fileTypeAdded.log();
      myTempFileTypes.add(type);
      updateFileTypeList();
      updateExtensionList();
      myRecognizedFileType.selectFileType(type);
    }
  }

  private void editPattern() {
    String item = myPatterns.getSelectedItem();
    if (item != null) {
      editPattern(item);
    }
  }

  private void editPattern(@Nullable String item) {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;

    if (item == null) {
      FileTypeConfigurableInteractions.patternAdded.log(type);
    }
    else {
      FileTypeConfigurableInteractions.patternEdited.log(type);
    }

    String title = item == null ? FileTypesBundle.message("filetype.edit.add.pattern.title") : FileTypesBundle.message("filetype.edit.edit.pattern.title");

    Language oldLanguage = item == null ? null : myTempTemplateDataLanguages.findAssociatedFileType(item);
    FileTypePatternDialog dialog = new FileTypePatternDialog(item, type, oldLanguage);
    DialogBuilder builder = new DialogBuilder(myPatterns.myList);
    builder.setPreferredFocusComponent(dialog.getPatternField());
    builder.setCenterPanel(dialog.getMainPanel());
    builder.setTitle(title);
    builder.showModal(true);
    if (builder.getDialogWrapper().isOK()) {
      String pattern = dialog.getPatternField().getText();
      if (StringUtil.isEmpty(pattern)) return;

      FileNameMatcher matcher = FileTypeManager.parseFromString(pattern);
      FileType registeredFileType = findExistingFileType(matcher);
      if (registeredFileType != null && registeredFileType != type) {
        if (registeredFileType.isReadOnly()) {
          Messages.showMessageDialog(myPatterns.myList,
                                     FileTypesBundle.message("filetype.edit.add.pattern.exists.error", registeredFileType.getDescription()),
                                     title, Messages.getErrorIcon());
          return;
        }
        int ret = Messages.showOkCancelDialog(myPatterns.myList, FileTypesBundle.message("filetype.edit.add.pattern.exists.message",
                                                                                         registeredFileType.getDescription()),
                                              FileTypesBundle.message("filetype.edit.add.pattern.exists.title"),
                                              FileTypesBundle.message("filetype.edit.add.pattern.reassign.button"),
                                              CommonBundle.getCancelButtonText(), Messages.getQuestionIcon());
        if (ret == Messages.OK) {
          myTempPatternsTable.removeAssociation(matcher, registeredFileType);
          if (oldLanguage != null) {
            myTempTemplateDataLanguages.removeAssociation(matcher, oldLanguage);
          }
        }
        else {
          return;
        }
      }

      if (item != null) {
        FileNameMatcher oldMatcher = FileTypeManager.parseFromString(item);
        myTempPatternsTable.removeAssociation(oldMatcher, type);
        if (oldLanguage != null) {
          myTempTemplateDataLanguages.removeAssociation(oldMatcher, oldLanguage);
        }
      }
      myTempPatternsTable.addAssociation(matcher, type);
      Language language = dialog.getTemplateDataLanguage();
      if (language != null) {
        myTempTemplateDataLanguages.addAssociation(matcher, language);
      }

      updateExtensionList();
      myPatterns.select(pattern);
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myPatterns.myList, true));
    }
  }

  private void addPattern() {
    editPattern(null);
  }

  private @Nullable FileType findExistingFileType(@NotNull FileNameMatcher matcher) {
    FileType type = myTempPatternsTable.findAssociatedFileType(matcher);
    if (type != null && type != FileTypes.UNKNOWN) {
      return type;
    }
    FileType registeredFileType = FileTypeManager.getInstance().getFileTypeByExtension(matcher.getPresentableString());
    if (registeredFileType != FileTypes.UNKNOWN && registeredFileType.isReadOnly()) {
      return registeredFileType;
    }
    return null;
  }

  private void removePattern() {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;
    FileTypeConfigurableInteractions.patternRemoved.log(type);
    String extension = myPatterns.removeSelected();
    if (extension == null) return;
    FileNameMatcher matcher = FileTypeManager.parseFromString(extension);

    myTempPatternsTable.removeAssociation(matcher, type);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myPatterns.myList, true));
  }

  private void removeHashBang() {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;
    FileTypeConfigurableInteractions.hashbangRemoved.log(type);
    String extension = myHashBangs.removeSelected();
    if (extension == null) return;

    myTempPatternsTable.removeHashBangPattern(extension, type);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myHashBangs.myList, true));
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "preferences.fileTypes";
  }

  class RecognizedFileTypesPanel extends JPanel {
    private final JList<FileType> myFileTypesList = new JBList<>(new DefaultListModel<>());

    RecognizedFileTypesPanel() {
      setLayout(new BorderLayout());

      myFileTypesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myFileTypesList.setCellRenderer(new FileTypeRenderer(myFileTypesList.getModel()));

      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(@NotNull MouseEvent e) {
          editFileType();
          return true;
        }
      }.installOn(myFileTypesList);

      ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myFileTypesList)
        .setScrollPaneBorder(JBUI.Borders.empty())
        .setPanelBorder(JBUI.Borders.customLine(JBColor.border(),0,1,0,1))
        .setAddAction(__ -> addFileType())
        .setRemoveAction(__ -> removeFileType())
        .setEditAction(__ -> editFileType())
        .setEditActionUpdater(e -> {
          FileType fileType = getSelectedFileType();
          return canBeModified(fileType);
        })
        .setRemoveActionUpdater(e -> canBeModified(getSelectedFileType()))
        .disableUpDownActions();

      add(toolbarDecorator.createPanel(), BorderLayout.NORTH);
      JScrollPane scrollPane = new JBScrollPane(myFileTypesList);
      add(scrollPane, BorderLayout.CENTER);

      new MySpeedSearch(myFileTypesList);
    }

    private final class MySpeedSearch extends SpeedSearchBase<JList<FileType>> {
      private final List<Condition<Pair<Object, String>>> myOrderedConverters;
      private Object myCurrentType;
      private String myExtension;

      private MySpeedSearch(@NotNull JList<FileType> component) {
        super(component);
        myOrderedConverters = Arrays.asList(
          // simple
          p -> {
            String value = p.first.toString();
            if (p.first instanceof FileType) {
              value = ((FileType)p.first).getDescription();
            }
            return getComparator().matchingFragments(p.second, value) != null;
          },
          // by-extension
          p -> p.first instanceof FileType && myCurrentType != null && myCurrentType.equals(p.first)
        );
      }

      @Override
      protected boolean isMatchingElement(Object element, String pattern) {
        for (Condition<Pair<Object, String>> convertor : myOrderedConverters) {
          boolean matched = convertor.value(pair(element, pattern));
          if (matched) return true;
        }
        return false;
      }

      @Nullable
      @Override
      protected final String getElementText(Object element) {
        throw new IllegalStateException();
      }

      @Override
      protected int getSelectedIndex() {
        return myComponent.getSelectedIndex();
      }

      @Override
      protected int getElementCount() {
        return myComponent.getModel().getSize();
      }

      @Override
      protected Object getElementAt(int viewIndex) {
        return myComponent.getModel().getElementAt(viewIndex);
      }

      @Override
      protected void selectElement(Object element, String selectedText) {
        if (element != null) {
          ScrollingUtil.selectItem(myComponent, (FileType)element);
          if (element.equals(myCurrentType)) {
            myPatterns.select(myExtension);
          }
        }
      }

      @Override
      protected void onSearchFieldUpdated(String s) {
        if (myTempPatternsTable == null) return;
        int index = s.lastIndexOf('.');
        if (index < 0) {
          s = "." + s;
        }
        myCurrentType = myTempPatternsTable.findAssociatedFileType(s);
        if (myCurrentType != null) {
          myExtension = s;
        }
        else {
          myExtension = null;
        }
      }
    }

    FileType getSelectedFileType() {
      return myFileTypesList.getSelectedValue();
    }

    void setFileTypes(@NotNull Iterable<? extends FileType> types) {
      DefaultListModel<FileType> listModel = (DefaultListModel<FileType>)myFileTypesList.getModel();
      listModel.clear();
      for (FileType type : types) {
        if (type != FileTypes.UNKNOWN) {
          listModel.addElement(type);
        }
      }
      ScrollingUtil.ensureSelectionExists(myFileTypesList);
    }

    void selectFileType(@NotNull FileType fileType) {
      myFileTypesList.setSelectedValue(fileType, true);
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myFileTypesList, true));
    }
  }

  @Override
  public void selectFileType(@NotNull FileType fileType) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myRecognizedFileType == null) {
      myFileTypeToPreselect = fileType;
    }
    else {
      myRecognizedFileType.selectFileType(fileType);
    }
  }

  class PatternsPanel extends JPanel {
    private final JBList<String> myList = new JBList<>(new DefaultListModel<>());

    PatternsPanel() {
      setLayout(new BorderLayout());
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setCellRenderer(new ExtensionRenderer());
      myList.getEmptyText().setText(FileTypesBundle.message("filetype.settings.no.patterns"));

      ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList)
        .setScrollPaneBorder(JBUI.Borders.empty())
        .setPanelBorder(JBUI.Borders.customLine(JBColor.border(),0,1,0,1))
        .setAddAction(__ -> addPattern())
        .setEditAction(__ -> editPattern())
        .setRemoveAction(__ -> removePattern())
        .disableUpDownActions();
      add(decorator.createPanel(), BorderLayout.NORTH);
      JScrollPane scrollPane = new JBScrollPane(myList);
      add(scrollPane, BorderLayout.CENTER);
      //noinspection DialogTitleCapitalization IDEA-254041
      setBorder(IdeBorderFactory.createTitledBorder(FileTypesBundle.message("filetype.registered.patterns.group"), false, TITLE_INSETS).setShowLine(false));
    }

    void clearList() {
      ((DefaultListModel<String>)myList.getModel()).clear();
      myList.clearSelection();
    }

    void select(@NotNull String pattern) {
      for (int i = 0; i < myList.getItemsCount(); i++) {
        String at = myList.getModel().getElementAt(i);
        FileNameMatcher matcher = FileTypeManager.parseFromString(at);
        if (matcher.acceptsCharSequence(pattern)) {
          ScrollingUtil.selectItem(myList, i);
          return;
        }
      }
    }

    String removeSelected() {
      String selectedValue = getSelectedItem();
      if (selectedValue == null) return null;
      ListUtil.removeSelectedItems(myList);
      return selectedValue;
    }

    String getSelectedItem() {
      return myList.getSelectedValue();
    }

    private void refill(@NotNull List<? extends FileNameMatcher> matchers) {
      clearList();
      List<FileNameMatcher> copy = new ArrayList<>(matchers);
      copy.sort(Comparator.comparing(FileNameMatcher::getPresentableString));
      DefaultListModel<String> model = (DefaultListModel<String>)myList.getModel();
      for (FileNameMatcher matcher : copy) {
        model.addElement(matcher.getPresentableString());
      }
      ScrollingUtil.ensureSelectionExists(myList);
    }
  }

  class HashBangPanel extends JPanel {
    private final JBList<String> myList = new JBList<>(new DefaultListModel<>());

    HashBangPanel() {
      setLayout(new BorderLayout());
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setCellRenderer(new ExtensionRenderer(){
        @Override
        public @NotNull Component getListCellRendererComponent(@NotNull JList list,
                                                               Object value,
                                                               int index,
                                                               boolean isSelected,
                                                               boolean cellHasFocus) {
          Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          setText(" #!*"+value+"*");
          return component;
        }
      });
      myList.setEmptyText(FileTypesBundle.message("filetype.settings.no.patterns"));

      ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList)
        .setScrollPaneBorder(JBUI.Borders.empty())
        .setPanelBorder(JBUI.Borders.customLine(JBColor.border(),0,1,0,1))
        .setAddAction(__ -> editHashBang(null))
        .setAddActionName(LangBundle.message("action.HashBangPanel.add.hashbang.pattern.text"))
        .setEditAction(__ -> editHashBang())
        .setRemoveAction(__ -> removeHashBang())
        .disableUpDownActions();

      add(decorator.createPanel(), BorderLayout.NORTH);
      JScrollPane scrollPane = new JBScrollPane(myList);
      add(scrollPane, BorderLayout.CENTER);
      //noinspection DialogTitleCapitalization IDEA-254041
      setBorder(IdeBorderFactory.createTitledBorder(FileTypesBundle.message("filetype.hashbang.group"), false, TITLE_INSETS).setShowLine(false));
    }

    void clearList() {
      ((DefaultListModel<String>)myList.getModel()).clear();
      myList.clearSelection();
    }

    void select(@NotNull String pattern) {
      ScrollingUtil.selectItem(myList, pattern);
    }

    String removeSelected() {
      String selectedValue = getSelectedItem();
      if (selectedValue == null) return null;
      ListUtil.removeSelectedItems(myList);
      return selectedValue;
    }

    String getSelectedItem() {
      return myList.getSelectedValue();
    }

    private void refill(@NotNull List<String> patterns) {
      clearList();
      Collections.sort(patterns);
      DefaultListModel<String> model = (DefaultListModel<String>)myList.getModel();
      for (@NlsSafe String pattern : patterns) {
        model.addElement(pattern);
      }
      ScrollingUtil.ensureSelectionExists(myList);
    }
  }

  private void editHashBang() {
    String item = myHashBangs.getSelectedItem();
    if (item == null) return;

    editHashBang(item);
  }
  private void editHashBang(@Nullable("null means new") String oldHashBang) {
    FileType type = myRecognizedFileType.getSelectedFileType();
    if (type == null) return;

    if (oldHashBang == null) {
      FileTypeConfigurableInteractions.hashbangAdded.log(type);
    }
    else {
      FileTypeConfigurableInteractions.hashbangEdited.log(type);
    }

    String title = FileTypesBundle.message("filetype.edit.hashbang.title");

    Language oldLanguage = oldHashBang == null ? null : myTempTemplateDataLanguages.findAssociatedFileType(oldHashBang);
    String hashbang = Messages.showInputDialog(myHashBangs.myList, FileTypesBundle.message("filetype.edit.hashbang.prompt"), title, null, oldHashBang, null);
    if (StringUtil.isEmpty(hashbang)) {
      return; //canceled or empty
    }
    HashBangConflict conflict = checkHashBangConflict(hashbang);
    if (conflict != null && conflict.fileType != type) {
      FileType existingFileType = conflict.fileType;
      if (!conflict.writeable) {
        String message = conflict.exact
                         ? FileTypesBundle.message("filetype.edit.hashbang.exists.exact.error", existingFileType.getDescription())
                         : FileTypesBundle.message("filetype.edit.hashbang.exists.similar.error", existingFileType.getDescription(), conflict.existingHashBang);
        Messages.showMessageDialog(myHashBangs.myList, message, title, Messages.getErrorIcon());
        return;
      }
      String message = conflict.exact ? FileTypesBundle.message("filetype.edit.hashbang.exists.exact.message", existingFileType.getDescription())
                                      : FileTypesBundle.message("filetype.edit.hashbang.exists.similar.message", existingFileType.getDescription(), conflict.existingHashBang);
      int ret = Messages.showOkCancelDialog(myHashBangs.myList, message,
                                            FileTypesBundle.message("filetype.edit.hashbang.exists.title"),
                                            FileTypesBundle.message("filetype.edit.hashbang.reassign.button"),
                                            CommonBundle.getCancelButtonText(), Messages.getQuestionIcon());
      if (ret != Messages.OK) {
        return;
      }
      myTempPatternsTable.removeHashBangPattern(hashbang, existingFileType);
      if (oldLanguage != null) {
        myTempTemplateDataLanguages.removeHashBangPattern(hashbang, oldLanguage);
      }
      myTempPatternsTable.removeHashBangPattern(conflict.existingHashBang, conflict.fileType);
    }
    if (oldHashBang != null) {
      myTempPatternsTable.removeHashBangPattern(oldHashBang, type);
      if (oldLanguage != null) {
        myTempTemplateDataLanguages.removeHashBangPattern(oldHashBang, oldLanguage);
      }
    }
    myTempPatternsTable.addHashBangPattern(hashbang, type);

    updateExtensionList();
    myHashBangs.select(hashbang);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myPatterns.myList, true));
  }

  // describes conflict between two hashbang patterns when user tried to create new/edit existing hashbang
  private static class HashBangConflict {
    FileType fileType; // conflicting file type
    boolean exact; // true: conflict with the file type with the exactly the same hashbang/false: similar hashbang (more selective or less selective)
    boolean writeable; //file type can be changed
    String existingHashBang; // the hashbang of the conflicting file type
  }

  private static boolean isStandardFileType(@NotNull FileType fileType) {
    return FileTypeManager.getInstance().getStdFileType(fileType.getName()) == fileType;
  }

  // check if there is a conflict between new hashbang and existing ones
  private HashBangConflict checkHashBangConflict(@NotNull String hashbang) {
    HashBangConflict conflict = new HashBangConflict();
    for (Map.Entry<String, FileType> entry : myTempPatternsTable.getInternalRawHashBangPatterns().entrySet()) {
      String existingHashBang = entry.getKey();
      if (hashbang.contains(existingHashBang) || existingHashBang.contains(hashbang)) {
        conflict.fileType = entry.getValue();
        conflict.exact = existingHashBang.equals(hashbang);
        conflict.writeable = !conflict.fileType.isReadOnly() && !isStandardFileType(conflict.fileType);
        conflict.existingHashBang = existingHashBang;
        return conflict;
      }
    }
    for (FileTypeRegistry.FileTypeDetector detector : FileTypeRegistry.FileTypeDetector.EP_NAME.getIterable()) {
      if (detector instanceof HashBangFileTypeDetector) {
        String existingHashBang = ((HashBangFileTypeDetector)detector).getMarker();
        if (hashbang.contains(existingHashBang) || existingHashBang.contains(hashbang)) {
          conflict.fileType = ((HashBangFileTypeDetector)detector).getFileType();
          conflict.exact = existingHashBang.equals(hashbang);
          conflict.writeable = false;
          conflict.existingHashBang = existingHashBang;
          return conflict;
        }
      }
    }
    return null;
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }
}
