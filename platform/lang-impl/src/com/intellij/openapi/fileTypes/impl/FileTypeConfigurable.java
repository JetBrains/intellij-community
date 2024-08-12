// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
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
  private Set<FileTypeManagerImpl.FileTypeWithDescriptor> myTempFileTypes;
  private FileTypeAssocTable<FileTypeManagerImpl.FileTypeWithDescriptor> myTempPatternsTable;
  private FileTypeAssocTable<Language> myTempTemplateDataLanguages;
  private final Map<UserFileType<?>, UserFileType<?>> myOriginalToEditedMap = new HashMap<>();
  private FileType myFileTypeToPreselect;
  private IgnoredFilesAndFoldersPanel myIgnoreFilesPanel;
  private final FileTypeManagerImpl myFileTypeManager = (FileTypeManagerImpl)FileTypeManager.getInstance();

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
    myFileTypePanel.myAssociateButton.addActionListener(__ -> OSAssociateFileTypesUtil.chooseAndAssociate(
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
    ));
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
    List<FileTypeManagerImpl.FileTypeWithDescriptor> types = ContainerUtil.sorted(ContainerUtil.filter(myTempFileTypes, ftd -> !ftd.fileType()
                                                                                    .isReadOnly()),
    (o1, o2) -> o1.fileType().getDescription().compareToIgnoreCase(o2.fileType().getDescription()));
    myRecognizedFileType.setFileTypes(types);
  }

  private @NotNull Set<FileTypeManagerImpl.FileTypeWithDescriptor> getRegisteredFilesTypes() {
    return new HashSet<>(myFileTypeManager.getRegisteredFileTypeWithDescriptors());
  }

  @Override
  public void apply() {
    copyTypeMap();

    ApplicationManager.getApplication().runWriteAction(() -> {
      if (!myFileTypeManager.isIgnoredFilesListEqualToCurrent(myIgnoreFilesPanel.getValues())) {
        myFileTypeManager.setIgnoredFilesList(myIgnoreFilesPanel.getValues());
      }
      myFileTypeManager.setPatternsTable(myTempFileTypes, myTempPatternsTable);
      TemplateDataLanguagePatterns.getInstance().setAssocTable(myTempTemplateDataLanguages);
    });
  }

  private void copyTypeMap() {
    for (Map.Entry<UserFileType<?>, UserFileType<?>> entry : myOriginalToEditedMap.entrySet()) {
      //noinspection unchecked,rawtypes
      entry.getKey().copyFrom((UserFileType)entry.getValue());
    }
    myOriginalToEditedMap.clear();
  }

  @Override
  public void reset() {
    myTempPatternsTable = myFileTypeManager.getExtensionMap().copy();
    myTempTemplateDataLanguages = TemplateDataLanguagePatterns.getInstance().getAssocTable();

    myTempFileTypes = getRegisteredFilesTypes();
    myOriginalToEditedMap.clear();

    FileTypeManagerImpl.FileTypeWithDescriptor lastSelectedFileType = myRecognizedFileType.getSelectedFileType();

    updateFileTypeList();
    updateExtensionList();

    myIgnoreFilesPanel.setValues(myFileTypeManager.getIgnoredFilesList());
    if (myFileTypeToPreselect != null) {
      myRecognizedFileType.selectFileType(myFileTypeToPreselect);
    }
    else if (lastSelectedFileType != null) {
      myRecognizedFileType.selectFileType(lastSelectedFileType.fileType());
    }
  }

  @Override
  public boolean isModified() {
    if (!myFileTypeManager.isIgnoredFilesListEqualToCurrent(myIgnoreFilesPanel.getValues())) {
      return true;
    }
    return !myTempPatternsTable.equals(myFileTypeManager.getExtensionMap()) ||
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

  private static final class ExtensionRenderer extends ColoredListCellRenderer<Pair<FileNameMatcher, Language>> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends Pair<FileNameMatcher, Language>> list,
                                         Pair<FileNameMatcher, Language> value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      append(value.first.getPresentableString());
      if (value.second != null) {
        append(" (" + value.second.getDisplayName() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES, false);
      }
    }
  }

  private static final class HashBangRenderer extends ColoredListCellRenderer<String> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends String> list,
                                         String value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      append("#!*", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      //noinspection HardCodedStringLiteral
      append(value);
      append("*", SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  private void updateExtensionList() {
    FileTypeManagerImpl.FileTypeWithDescriptor ftd = myRecognizedFileType.getSelectedFileType();
    if (ftd == null) return;
    myPatterns.refill(myTempPatternsTable.getAssociations(ftd));
    myHashBangs.refill(myTempPatternsTable.getHashBangPatterns(ftd));
  }

  private void editFileType() {
    FileTypeManagerImpl.FileTypeWithDescriptor ftd = myRecognizedFileType.getSelectedFileType();
    if (ftd==null||!canBeModified(ftd.fileType())) return;

    UserFileType<?> userFileType = (UserFileType<?>)ftd.fileType();
    UserFileType<?> ftToEdit = myOriginalToEditedMap.get(userFileType);
    if (ftToEdit == null) ftToEdit = userFileType.clone();
    TypeEditor editor = new TypeEditor(myRecognizedFileType.myFileTypesList, ftToEdit, FileTypesBundle.message("filetype.edit.existing.title"));
    if (editor.showAndGet()) {
      FileTypeConfigurableInteractions.fileTypeEdited.log();
      if (userFileType.equals(ftToEdit)) {
        myOriginalToEditedMap.remove(userFileType);
      } else {
        myOriginalToEditedMap.put(userFileType, ftToEdit);
      }
      myRecognizedFileType.myCellRenderer.resetDuplicates();
    }
  }

  private void removeFileType() {
    FileTypeManagerImpl.FileTypeWithDescriptor ftd = myRecognizedFileType.getSelectedFileType();
    if (ftd == null) return;
    FileType fileType = ftd.fileType();
    FileTypeConfigurableInteractions.fileTypeRemoved.log();

    int index = myRecognizedFileType.myFileTypesList.getSelectedIndex();
    myTempFileTypes.remove(ftd);
    if (fileType instanceof UserFileType) {
      myOriginalToEditedMap.remove(fileType);
    }
    List<FileNameMatcher> matchers = myTempPatternsTable.getAssociations(ftd);
    myTempPatternsTable.removeAllAssociations(ftd);
    for (FileNameMatcher matcher : matchers) {
      myTempTemplateDataLanguages.removeAssociation(matcher, null);
    }

    updateFileTypeList();
    updateExtensionList();
    index = Math.min(index, myRecognizedFileType.myFileTypesList.getModel().getSize() - 1);
    ScrollingUtil.selectItem(myRecognizedFileType.myFileTypesList, index);
  }

  private static boolean canBeModified(@Nullable FileType fileType) {
    return fileType instanceof AbstractFileType; //todo: add API for canBeModified
  }

  private void addFileType() {
    //TODO: support adding binary file types...
    AbstractFileType type = new AbstractFileType(new SyntaxTable());
    TypeEditor editor = new TypeEditor(myRecognizedFileType.myFileTypesList, type, FileTypesBundle.message("filetype.edit.new.title"));
    if (editor.showAndGet()) {
      FileTypeConfigurableInteractions.fileTypeAdded.log();
      myTempFileTypes.add(FileTypeManagerImpl.coreDescriptorFor(type));
      updateFileTypeList();
      updateExtensionList();
      myRecognizedFileType.selectFileType(type);
    }
  }

  private void editPattern() {
    Pair<FileNameMatcher, Language> item = myPatterns.getSelectedItem();
    if (item != null) {
      editPattern(item);
    }
  }

  private void editPattern(@Nullable("null means new") Pair<FileNameMatcher, Language> item) {
    FileTypeManagerImpl.FileTypeWithDescriptor ftd = myRecognizedFileType.getSelectedFileType();
    if (ftd == null) return;
    FileType type = ftd.fileType();

    if (item == null) {
      FileTypeConfigurableInteractions.patternAdded.log(type);
    }
    else {
      FileTypeConfigurableInteractions.patternEdited.log(type);
    }

    String title = FileTypesBundle.message(item == null ? "filetype.edit.add.pattern.title" : "filetype.edit.edit.pattern.title");

    Language oldLanguage = item == null ? null : item.second;
    String oldPattern = item == null ? null : item.first.getPresentableString();
    FileTypePatternDialog dialog = new FileTypePatternDialog(oldPattern, type, oldLanguage);
    DialogBuilder builder = new DialogBuilder(myPatterns.myList);
    builder.setPreferredFocusComponent(dialog.getPatternField());
    builder.setCenterPanel(dialog.getMainPanel());
    builder.setTitle(title);
    builder.showModal(true);
    if (builder.getDialogWrapper().isOK()) {
      String pattern = dialog.getPatternField().getText();
      if (StringUtil.isEmpty(pattern)) return;

      FileNameMatcher matcher = FileTypeManager.parseFromString(pattern);
      FileTypeManagerImpl.FileTypeWithDescriptor registeredFtd = findExistingFileType(matcher);
      if (registeredFtd != null && registeredFtd.fileType() != type) {
        FileType registeredFileType = registeredFtd.fileType();
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
          myTempPatternsTable.removeAssociation(matcher, registeredFtd);
          myTempTemplateDataLanguages.removeAssociation(matcher, null);
        }
        else {
          return;
        }
      }

      if (item != null) {
        myTempPatternsTable.removeAssociation(item.first, ftd);
        myTempTemplateDataLanguages.removeAssociation(item.first, item.second);
      }
      myTempPatternsTable.addAssociation(matcher, ftd);
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

  private @Nullable FileTypeManagerImpl.FileTypeWithDescriptor findExistingFileType(@NotNull FileNameMatcher matcher) {
    FileTypeManagerImpl.@Nullable FileTypeWithDescriptor ftd = myTempPatternsTable.findAssociatedFileType(matcher);
    if (ftd != null && ftd.fileType() != FileTypes.UNKNOWN) {
      return ftd;
    }
    FileTypeManagerImpl.@NotNull FileTypeWithDescriptor registeredFtd = myFileTypeManager.getFileTypeWithDescriptorByExtension(matcher.getPresentableString());
    if (registeredFtd.fileType() != FileTypes.UNKNOWN && registeredFtd.fileType().isReadOnly()) {
      return registeredFtd;
    }
    return null;
  }

  private void removePattern() {
    FileTypeManagerImpl.FileTypeWithDescriptor ftd = myRecognizedFileType.getSelectedFileType();
    if (ftd == null) return;
    FileTypeConfigurableInteractions.patternRemoved.log(ftd.fileType());
    Pair<FileNameMatcher, Language> removed = myPatterns.removeSelected();
    if (removed == null) return;

    myTempPatternsTable.removeAssociation(removed.first, ftd);
    myTempTemplateDataLanguages.removeAssociation(removed.first, removed.second);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myPatterns.myList, true));
  }

  private void removeHashBang() {
    FileTypeManagerImpl.FileTypeWithDescriptor ftd = myRecognizedFileType.getSelectedFileType();
    if (ftd == null) return;
    FileTypeConfigurableInteractions.hashbangRemoved.log(ftd.fileType());
    String extension = myHashBangs.removeSelected();
    if (extension == null) return;

    myTempPatternsTable.removeHashBangPattern(extension, ftd);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myHashBangs.myList, true));
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "preferences.fileTypes";
  }

  final class RecognizedFileTypesPanel extends JPanel {
    private final JList<FileTypeManagerImpl.FileTypeWithDescriptor> myFileTypesList = new JBList<>(new DefaultListModel<>());
    private final FileTypeWithDescriptorRenderer<FileTypeManagerImpl.FileTypeWithDescriptor> myCellRenderer;

    RecognizedFileTypesPanel() {
      setLayout(new BorderLayout());

      myFileTypesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myCellRenderer = new FileTypeWithDescriptorRenderer<>(myFileTypesList.getModel(), ftd -> {
        FileType fileType = ftd.fileType();
        UserFileType<?> modified = myOriginalToEditedMap.get(fileType);
        return modified != null ? modified : fileType;
      });
      myFileTypesList.setCellRenderer(myCellRenderer);

      new DoubleClickListener() {
        @Override
        protected boolean onDoubleClick(@NotNull MouseEvent e) {
          editFileType();
          return true;
        }
      }.installOn(myFileTypesList);

      ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myFileTypesList)
        .setScrollPaneBorder(JBUI.Borders.empty())
        .setPanelBorder(JBUI.Borders.customLine(JBColor.border(),1,1,0,1))
        .setAddAction(__ -> addFileType())
        .setRemoveAction(__ -> removeFileType())
        .setEditAction(__ -> editFileType())
        .setEditActionUpdater(e -> selectedTypeCanBeModified())
        .setRemoveActionUpdater(e -> selectedTypeCanBeModified())
        .disableUpDownActions();

      add(toolbarDecorator.createPanel(), BorderLayout.NORTH);
      JScrollPane scrollPane = new JBScrollPane(myFileTypesList);
      add(scrollPane, BorderLayout.CENTER);
      scrollPane.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 1, 1, 1));

      MySpeedSearch search = new MySpeedSearch(myFileTypesList);
      search.setupListeners();
    }

    private boolean selectedTypeCanBeModified() {
      FileTypeManagerImpl.FileTypeWithDescriptor ftd = getSelectedFileType();
      if (ftd == null) return false;
      return canBeModified(ftd.fileType());
    }

    private final class MySpeedSearch extends SpeedSearchBase<JList<FileTypeManagerImpl.FileTypeWithDescriptor>> {
      private final List<Condition<Pair<Object, String>>> myOrderedConverters;
      private Object myCurrentType;
      private String myExtension;

      private MySpeedSearch(@NotNull JList<FileTypeManagerImpl.FileTypeWithDescriptor> component) {
        super(component, null);
        myOrderedConverters = Arrays.asList(
          // simple
          p -> {
            String value = p.first.toString();
            if (p.first instanceof FileTypeManagerImpl.FileTypeWithDescriptor) {
              value = ((FileTypeManagerImpl.FileTypeWithDescriptor)p.first).fileType().getDescription();
            }
            return getComparator().matchingFragments(p.second, value) != null;
          },
          // by-extension
          p -> p.first instanceof FileTypeManagerImpl.FileTypeWithDescriptor && myCurrentType != null && myCurrentType.equals(p.first)
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

      @Override
      protected @Nullable String getElementText(Object element) {
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
          ScrollingUtil.selectItem(myComponent, (FileTypeManagerImpl.FileTypeWithDescriptor)element);
          if (element.equals(myCurrentType)) {
            myPatterns.select(myExtension);
          }
        }
      }

      @Override
      protected void onSearchFieldUpdated(String s) {
        super.onSearchFieldUpdated(s);
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

    FileTypeManagerImpl.FileTypeWithDescriptor getSelectedFileType() {
      return myFileTypesList.getSelectedValue();
    }

    void setFileTypes(@NotNull Collection<FileTypeManagerImpl.FileTypeWithDescriptor> types) {
      DefaultListModel<FileTypeManagerImpl.FileTypeWithDescriptor> listModel = (DefaultListModel<FileTypeManagerImpl.FileTypeWithDescriptor>)myFileTypesList.getModel();
      listModel.clear();
      for (FileTypeManagerImpl.FileTypeWithDescriptor type : types) {
        if (type.fileType() != FileTypes.UNKNOWN) {
          listModel.addElement(type);
        }
      }
      ScrollingUtil.ensureSelectionExists(myFileTypesList);
    }

    void selectFileType(@NotNull FileType fileType) {
      myFileTypesList.setSelectedValue(FileTypeManagerImpl.FileTypeWithDescriptor.allFor(fileType), true);
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myFileTypesList, true));
    }
  }

  @Override
  public void selectFileType(@NotNull FileType fileType) {
    ThreadingAssertions.assertEventDispatchThread();
    if (myRecognizedFileType == null) {
      myFileTypeToPreselect = fileType;
    }
    else {
      myRecognizedFileType.selectFileType(fileType);
    }
  }

  final class PatternsPanel extends JPanel {
    private final JBList<Pair<FileNameMatcher, Language>> myList = new JBList<>(new DefaultListModel<>());

    PatternsPanel() {
      setLayout(new BorderLayout());
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setCellRenderer(new ExtensionRenderer());
      myList.getEmptyText().setText(FileTypesBundle.message("filetype.settings.no.patterns"));
      myList.setBorder(JBUI.Borders.empty());

      ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList)
        .setScrollPaneBorder(JBUI.Borders.empty())
        .setPanelBorder(JBUI.Borders.customLine(JBColor.border(),1,1,0,1))
        .setAddAction(__ -> addPattern())
        .setEditAction(__ -> editPattern())
        .setRemoveAction(__ -> removePattern())
        .disableUpDownActions();
      add(decorator.createPanel(), BorderLayout.NORTH);
      JScrollPane scrollPane = new JBScrollPane(myList);
      scrollPane.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 1, 1, 1));
      add(scrollPane, BorderLayout.CENTER);
      //noinspection DialogTitleCapitalization IDEA-254041
      setBorder(IdeBorderFactory.createTitledBorder(FileTypesBundle.message("filetype.registered.patterns.group"), false, TITLE_INSETS).setShowLine(false));
    }

    void clearList() {
      ((DefaultListModel<Pair<FileNameMatcher, Language>>)myList.getModel()).clear();
      myList.clearSelection();
    }

    void select(@NotNull String pattern) {
      for (int i = 0; i < myList.getItemsCount(); i++) {
        Pair<FileNameMatcher, Language> at = myList.getModel().getElementAt(i);
        if (at.first.acceptsCharSequence(pattern)) {
          ScrollingUtil.selectItem(myList, i);
          return;
        }
      }
    }

    Pair<FileNameMatcher, Language> removeSelected() {
      Pair<FileNameMatcher, Language> selectedValue = getSelectedItem();
      if (selectedValue == null) return null;
      ListUtil.removeSelectedItems(myList);
      return selectedValue;
    }

    Pair<FileNameMatcher, Language> getSelectedItem() {
      return myList.getSelectedValue();
    }

    private void refill(@NotNull List<? extends FileNameMatcher> matchers) {
      clearList();
      List<FileNameMatcher> copy = new ArrayList<>(matchers);
      copy.sort(Comparator.comparing(FileNameMatcher::getPresentableString));
      DefaultListModel<Pair<FileNameMatcher, Language>> model = (DefaultListModel<Pair<FileNameMatcher, Language>>)myList.getModel();
      for (FileNameMatcher matcher : copy) {
        Language language = myTempTemplateDataLanguages.findAssociatedFileType(matcher);
        model.addElement(Pair.create(matcher, language));
      }
      ScrollingUtil.ensureSelectionExists(myList);
    }
  }

  final class HashBangPanel extends JPanel {
    private final JBList<String> myList = new JBList<>(new DefaultListModel<>());

    HashBangPanel() {
      setLayout(new BorderLayout());
      myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      myList.setCellRenderer(new HashBangRenderer());
      myList.setEmptyText(FileTypesBundle.message("filetype.settings.no.patterns"));
      myList.setBorder(JBUI.Borders.empty());

      ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myList)
        .setScrollPaneBorder(JBUI.Borders.empty())
        .setPanelBorder(JBUI.Borders.customLine(JBColor.border(),1,1,0,1))
        .setAddAction(__ -> editHashBang(null))
        .setAddActionName(LangBundle.message("action.HashBangPanel.add.hashbang.pattern.text"))
        .setEditAction(__ -> editHashBang())
        .setRemoveAction(__ -> removeHashBang())
        .disableUpDownActions();

      add(decorator.createPanel(), BorderLayout.NORTH);
      JScrollPane scrollPane = new JBScrollPane(myList);
      add(scrollPane, BorderLayout.CENTER);
      scrollPane.setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 1, 1, 1));
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
      DefaultListModel<String> model = (DefaultListModel<String>)myList.getModel();
      for (@NlsSafe String pattern : ContainerUtil.sorted(patterns)) {
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
    FileTypeManagerImpl.FileTypeWithDescriptor ftd = myRecognizedFileType.getSelectedFileType();
    if (ftd == null) return;
    FileType type = ftd.fileType();

    if (oldHashBang == null) {
      FileTypeConfigurableInteractions.hashbangAdded.log(type);
    }
    else {
      FileTypeConfigurableInteractions.hashbangEdited.log(type);
    }

    String title = FileTypesBundle.message(oldHashBang == null ? "filetype.add.hashbang.title" : "filetype.edit.hashbang.title");

    String hashbang = Messages.showInputDialog(myHashBangs.myList, FileTypesBundle.message("filetype.edit.hashbang.prompt"), title, null, oldHashBang, null);
    if (StringUtil.isEmpty(hashbang)) {
      return; //canceled or empty
    }
    HashBangConflict conflict = checkHashBangConflict(hashbang);
    if (conflict != null && conflict.fileType.fileType() != type) {
      FileTypeManagerImpl.FileTypeWithDescriptor existingFtd = conflict.fileType;
      FileType existingFileType = existingFtd.fileType();
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
      myTempPatternsTable.removeHashBangPattern(hashbang, existingFtd);
      myTempPatternsTable.removeHashBangPattern(conflict.existingHashBang, conflict.fileType);
    }
    if (oldHashBang != null) {
      myTempPatternsTable.removeHashBangPattern(oldHashBang, ftd);
    }
    myTempPatternsTable.addHashBangPattern(hashbang, ftd);

    updateExtensionList();
    myHashBangs.select(hashbang);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myPatterns.myList, true));
  }

  /**
   * describes conflict between two hashbang patterns when user tried to create new/edit existing hashbang
   *
   * @param fileType         conflicting file type
   * @param exact            true: conflict with the file type with exactly the same hashbang/false: similar hashbang (more selective or less selective)
   * @param writeable        file type can be changed
   * @param existingHashBang the hashbang of the conflicting file type
   */
  private record HashBangConflict(@NotNull FileTypeManagerImpl.FileTypeWithDescriptor fileType,
                                  boolean exact, boolean writeable,
                                  @NotNull String existingHashBang) {
  }

  private boolean isStandardFileType(@NotNull FileType fileType) {
    return myFileTypeManager.getStdFileType(fileType.getName()) == fileType;
  }

  // check if there is a conflict between new hashbang and existing ones
  private HashBangConflict checkHashBangConflict(@NotNull String hashbang) {
    for (Map.Entry<String, FileTypeManagerImpl.FileTypeWithDescriptor> entry : myTempPatternsTable.getInternalRawHashBangPatterns().entrySet()) {
      String existingHashBang = entry.getKey();
      if (hashbang.contains(existingHashBang) || existingHashBang.contains(hashbang)) {
        FileTypeManagerImpl.FileTypeWithDescriptor ftd = entry.getValue();
        boolean exact = existingHashBang.equals(hashbang);
        boolean writeable = !ftd.fileType().isReadOnly() && !isStandardFileType(ftd.fileType());
        return new HashBangConflict(ftd, exact, writeable, existingHashBang);
      }
    }
    for (FileTypeRegistry.FileTypeDetector detector : FileTypeRegistry.FileTypeDetector.EP_NAME.getIterable()) {
      if (detector instanceof HashBangFileTypeDetector) {
        String existingHashBang = ((HashBangFileTypeDetector)detector).getMarker();
        if (hashbang.contains(existingHashBang) || existingHashBang.contains(hashbang)) {
          FileType fileType = ((HashBangFileTypeDetector)detector).getFileType();
          boolean exact = existingHashBang.equals(hashbang);
          boolean writeable = false;
          FileTypeManagerImpl.FileTypeWithDescriptor ftd = FileTypeManagerImpl.detectPluginDescriptor(fileType);
          return new HashBangConflict(ftd, exact, writeable, existingHashBang);
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
