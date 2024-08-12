// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.tree;

import com.intellij.CommonBundle;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.LangBundle;
import com.intellij.lang.LanguagePerFileMappings;
import com.intellij.lang.PerFileMappingsBase;
import com.intellij.lang.PerFileMappingsEx;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.IconUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.ui.IdeBorderFactory.*;

public abstract class PerFileConfigurableBase<T> implements SearchableConfigurable, Configurable.NoScroll {
  public record Mapping<T>(@Nls String name, @NotNull Supplier<? extends T> getter, @NotNull Consumer<? super T> setter) {
  }
  protected static final Key<@NlsContexts.Label String> DESCRIPTION = KeyWithDefaultValue.create("DESCRIPTION", "");
  protected static final Key<@NlsContexts.ColumnName String> TARGET_TITLE = KeyWithDefaultValue.create("TARGET_TITLE", () -> LangBundle.message("PerFileConfigurableBase.target.title"));
  protected static final Key<@NlsContexts.ColumnName String> MAPPING_TITLE = KeyWithDefaultValue.create("MAPPING_TITLE", () -> LangBundle.message("PerFileConfigurableBase.mapping.title"));
  protected static final Key<@NlsContexts.Label String> EMPTY_TEXT = KeyWithDefaultValue.create("EMPTY_TEXT", () -> LangBundle.message("PerFileConfigurableBase.empty.text"));
  protected static final Key<@Nls String> OVERRIDE_QUESTION = Key.create("OVERRIDE_QUESTION");
  protected static final Key<@NlsContexts.DialogTitle String> OVERRIDE_TITLE = Key.create("OVERRIDE_TITLE");
  protected static final Key<@NlsActions.ActionText String> NULL_TEXT = KeyWithDefaultValue.create("NULL_TEXT", () -> LangBundle.message("PerFileConfigurableBase.null.text"));
  protected static final Key<Boolean> ADD_PROJECT_MAPPING = KeyWithDefaultValue.create("ADD_PROJECT_MAPPING", Boolean.TRUE);
  protected static final Key<Boolean> ONLY_DIRECTORIES = KeyWithDefaultValue.create("ONLY_DIRECTORIES", Boolean.FALSE);
  protected static final Key<Boolean> SORT_VALUES = KeyWithDefaultValue.create("SORT_VALUES", Boolean.TRUE);

  protected final @NotNull Project myProject;
  protected final PerFileMappingsEx<T> myMappings;

  /** @noinspection FieldCanBeLocal */
  private JPanel myPanel;
  private JBTable myTable;
  private MyModel<T> myModel;

  private final List<Runnable> myResetRunnables = new ArrayList<>();
  private final Map<String, T> myDefaultVals = new HashMap<>();
  private final List<Mapping<T>> myDefaultProps = new ArrayList<>();
  private VirtualFile myFileToSelect;
  private final Mapping<T> myProjectMapping;

  protected interface Value<T> extends Setter<T>, Supplier<T> {
    void commit();
  }

  protected PerFileConfigurableBase(@NotNull Project project, @NotNull PerFileMappingsEx<T> mappings) {
    myProject = project;
    myMappings = mappings;
    myProjectMapping = new Mapping<>(
      LangBundle.message("PerFileConfigurableBase.project.mapping", StringUtil.capitalize(param(MAPPING_TITLE))),
      () -> ((LanguagePerFileMappings<T>)myMappings).getConfiguredMapping(null),
      o -> myMappings.setMapping(null, o));
  }

  @Override
  public @NotNull String getId() {
    return getDisplayName();
  }

  protected abstract @Nullable <S> Object getParameter(@NotNull Key<S> key);

  protected @NotNull List<Mapping<T>> getDefaultMappings() {
    return ContainerUtil.emptyList();
  }

  protected boolean canRemoveTarget(@Nullable Object target) {
    return true;
  }

  protected boolean canEditTarget(@Nullable Object target, T value) {
    return true;
  }

  protected T adjustChosenValue(@Nullable Object target, T chosen) {
    return chosen;
  }

  protected abstract void renderValue(@Nullable Object target, @NotNull T t, @NotNull ColoredTextContainer renderer);

  protected void renderDefaultValue(@Nullable Object target, @NotNull ColoredTextContainer renderer) { }

  private <S> S param(@NotNull Key<S> key) {
    Object o = getParameter(key);
    if (o == null && key instanceof KeyWithDefaultValue) return ((KeyWithDefaultValue<S>)key).getDefaultValue();
    @SuppressWarnings("unchecked") S s = (S)o;
    return s;
  }

  @Override
  public @NotNull JComponent createComponent() {
    ThreadingAssertions.assertEventDispatchThread();
    //todo multi-editing, separate project/ide combos _if_ needed by specific configurable (SQL, no Web)
    myPanel = new JPanel(new BorderLayout());
    myModel = new MyModel<>(param(TARGET_TITLE), param(MAPPING_TITLE));
    myTable = new JBTable(myModel) {
      @SuppressWarnings("unchecked")
      @Override
      public String getToolTipText(@NotNull MouseEvent event) {
        Point point = event.getPoint();
        int row = rowAtPoint(point);
        int col = columnAtPoint(point);
        if (row != -1 && col == 1) {
          return getToolTipFor((T)getValueAt(convertRowIndexToModel(row), col));
        }
        return super.getToolTipText(event);
      }
    };
    setupPerFileTable();
    JPanel tablePanel = ToolbarDecorator.createDecorator(myTable)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(button -> doAddAction())
      .setRemoveAction(button -> doRemoveAction())
      .setEditAction(button -> doEditAction())
      .setEditActionUpdater(e -> myTable.getSelectedRows().length > 0)
      .createPanel();
    myTable.getEmptyText().setText(param(EMPTY_TEXT).replace(
      "$addShortcut", KeymapUtil.getFirstKeyboardShortcutText(CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.ADD))));
    JBLabel label = new JBLabel(param(DESCRIPTION));
    label.setBorder(BorderFactory.createEmptyBorder(TITLED_BORDER_TOP_INSET, TITLED_BORDER_INDENT, TITLED_BORDER_BOTTOM_INSET, 0));
    label.setComponentStyle(UIUtil.ComponentStyle.SMALL);

    JComponent north = createDefaultMappingComponent();
    if (north != null) {
      myPanel.add(north, BorderLayout.NORTH);
    }
    myPanel.add(label, BorderLayout.SOUTH);
    myPanel.add(tablePanel, BorderLayout.CENTER);

    return myPanel;
  }

  protected @NlsContexts.Tooltip @Nullable String getToolTipFor(@Nullable T value) {
    return null;
  }

  protected @Nullable JComponent createDefaultMappingComponent() {
    myDefaultProps.addAll(getDefaultMappings());
    if (myMappings instanceof LanguagePerFileMappings && param(ADD_PROJECT_MAPPING)) {
     myDefaultProps.add(myProjectMapping);
    }
    if (myDefaultProps.size() == 0) return null;
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints cons1 = new GridBagConstraints();
    cons1.fill = GridBagConstraints.HORIZONTAL;
    cons1.weightx = 0;
    cons1.gridx = 0;
    cons1.insets = JBUI.insets(0, 0, 5, UIUtil.DEFAULT_HGAP);
    GridBagConstraints cons2 = new GridBagConstraints();
    cons2.fill = GridBagConstraints.NONE;
    cons2.anchor = GridBagConstraints.WEST;
    cons2.weightx = 0;
    cons2.gridx = 1;
    cons2.insets = cons1.insets;
    panel.add(Box.createGlue(), new GridBagConstraints(2, 0, 1, 1, 1., 1., GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                       JBInsets.emptyInsets(), 0, 0));

    for (Mapping<T> prop : myDefaultProps) {
      myDefaultVals.put(prop.name, prop.getter.get());
      JPanel p = createActionPanel(null, new Value<>() {
        @Override
        public void commit() {
          myModel.fireTableDataChanged();
        }

        @Override
        public T get() {
          return myDefaultVals.get(prop.name);
        }

        @Override
        public void set(T value) {
          myDefaultVals.put(prop.name, adjustChosenValue(null, value));
        }
      });
      panel.add(new JBLabel(prop.name + ":"), cons1);
      panel.add(p, cons2);
    }
    return panel;
  }

  private void doAddAction() {
    TableCellEditor editor = myTable.getCellEditor();
    if (editor != null) editor.cancelCellEditing();

    int row = myTable.getSelectedRow();
    Object selectedTarget = row >= 0 ? myModel.data.get(myTable.convertRowIndexToModel(row)).first : null;
    VirtualFile toSelect = myFileToSelect != null ? myFileToSelect :
                           ObjectUtils.tryCast(selectedTarget, VirtualFile.class);
    FileChooserDescriptor descriptor = new FileChooserDescriptor(!param(ONLY_DIRECTORIES), true, true, true, true, true);
    FileChooser.chooseFiles(descriptor, myProject, myTable, toSelect, this::doAddFiles);
  }

  private void doAddFiles(@NotNull List<? extends VirtualFile> files) {
    Set<VirtualFile> chosen = new HashSet<>(files);
    if (chosen.isEmpty()) return;
    Set<Object> set = myModel.data.stream().map(o -> o.first).collect(Collectors.toSet());
    for (VirtualFile file : chosen) {
      if (!set.add(file)) continue;
      myModel.data.add(pair(file, getNewMapping(file)));
    }
    myModel.fireTableDataChanged();
    IntList rowList = new IntArrayList();
    for (int i = 0, size = myModel.data.size(); i < size; i++) {
      if (chosen.contains(myModel.data.get(i).first)) {
        rowList.add(i);
      }
    }
    selectRows(rowList.toIntArray(), true);
  }

  private void doRemoveAction() {
    TableCellEditor editor = myTable.getCellEditor();
    if (editor != null) editor.cancelCellEditing();

    int[] rows = myTable.getSelectedRows();
    int firstRow = rows[0];
    Object[] keys = new Object[rows.length];
    for (int i = 0; i < rows.length; i++) {
      keys[i] = myModel.data.get(myTable.convertRowIndexToModel(rows[i])).first;
    }
    if (clearSubdirectoriesOnDemandOrCancel(true, keys) == Messages.YES) {
      int toSelect = Math.min(myModel.data.size() - 1, firstRow);
      if (toSelect >= 0) {
        selectRows(new int[]{toSelect}, false);
      }
    }
  }

  private void doEditAction() {
    TableUtil.editCellAt(myTable, myTable.getSelectedRow(), 0);
    TextFieldWithBrowseButton panel = ObjectUtils.tryCast(myTable.getEditorComponent(), TextFieldWithBrowseButton.class);
    if (panel != null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myTable.getEditorComponent() == panel) {
          panel.getButton().doClick();
        }
      });
    }
  }

  public @Nullable T getNewMapping(@Nullable VirtualFile file) {
    for (Pair<Object, T> p : ContainerUtil.reverse(myModel.data)) {
      if (keyMatches(p.first, file, false) && p.second != null) return p.second;
    }
    ProjectFileIndex index = ProjectFileIndex.getInstance(myProject);
    for (Mapping<T> prop : ContainerUtil.reverse(myDefaultProps)) {
      if (isProjectMapping(prop) && file != null && index.isInContent(file) || isGlobalMapping(prop)) {
        T t = myDefaultVals.get(prop.name);
        if (t != null) return t;
      }
    }
    return getDefaultNewMapping(file);
  }

  protected @Nullable T getDefaultNewMapping(@Nullable VirtualFile file) {
    return myMappings.getDefaultMapping(file);
  }

  protected boolean isGlobalMapping(Mapping<T> prop) {
    return false;
  }

  protected boolean isProjectMapping(Mapping<T> prop) {
    return prop == myProjectMapping;
  }

  private static boolean keyMatches(@Nullable Object key, @Nullable VirtualFile file, boolean strict) {
    if (file == null) return key == null;
    if (key instanceof VirtualFile) return VfsUtilCore.isAncestor((VirtualFile)key, file, strict);
    // todo also patterns
    if (key == null) return true;
    return false;
  }

  @Override
  public boolean isModified() {
    for (Mapping<T> prop : myDefaultProps) {
      if (!Comparing.equal(prop.getter.get(), myDefaultVals.get(prop.name))) {
        return true;
      }
    }

    Map<VirtualFile, T> oldMapping = myMappings.getMappings();
    Map<VirtualFile, T> newMapping = getNewMappings();
    return !newMapping.equals(oldMapping);
  }

  @Override
  public void apply() throws ConfigurationException {
    myMappings.setMappings(getNewMappings());
    for (Mapping<T> prop : myDefaultProps) {
      prop.setter.consume(myDefaultVals.get(prop.name));
    }
  }

  @Override
  public void reset() {
    myModel.data.clear();
    for (Map.Entry<VirtualFile, T> e : myMappings.getMappings().entrySet()) {
      if (e.getKey() == null) continue;
      myModel.data.add(pair(e.getKey(), e.getValue()));
    }
    for (Mapping<T> prop : myDefaultProps) {
      myDefaultVals.put(prop.name, prop.getter.get());
    }

    for (Runnable runnable : myResetRunnables) {
      runnable.run();
    }
    int[] rows = myTable.getSelectedRows();
    myModel.fireTableDataChanged();
    selectRows(rows, false);
  }

  protected void selectRows(int[] rows, boolean convertModelRowsToView) {
    int[] viewRows = convertModelRowsToView ? new int[rows.length] : rows;
    if (convertModelRowsToView) {
      for (int i = 0; i < rows.length; i++) {
        viewRows[i] = myTable.convertRowIndexToView(rows[i]);
      }
    }
    TableUtil.selectRows(myTable, viewRows);
    TableUtil.scrollSelectionToVisible(myTable);
  }

  protected Map<VirtualFile, T> getNewMappings() {
    ThreadingAssertions.assertEventDispatchThread();
    if (myModel == null) {
      throw new AssertionError("createComponent() was not called first on " + getClass().getName());
    }
    HashMap<VirtualFile, T> map = new HashMap<>();
    for (Pair<Object, T> p : myModel.data) {
      if (p.second != null) {
        map.put((VirtualFile)p.first, p.second);
      }
    }
    if (myMappings instanceof LanguagePerFileMappings) {
      for (Mapping<T> prop : ContainerUtil.reverse(myDefaultProps)) {
        if (isProjectMapping(prop)) {
          T t = myDefaultVals.get(prop.name);
          if (t != null) map.put(null, t);
          break;
        }
      }
    }
    return map;
  }

  public void selectFile(@NotNull VirtualFile virtualFile) {
    selectFile(virtualFile, true);
  }

  public void selectFile(@NotNull VirtualFile virtualFile, boolean addIfMissing) {
    VirtualFile file = virtualFile instanceof VirtualFileWindow ? ((VirtualFileWindow)virtualFile).getDelegate() : virtualFile;
    int[] rows = findRow(file, addIfMissing, false);
    if (rows.length == 0 && addIfMissing ) {
      doAddFiles(Collections.singletonList(virtualFile));
    }
    else {
      selectRows(rows, true);
    }
    myFileToSelect = file;
  }

  protected int[] findRow(VirtualFile file, boolean strict, boolean all) {
    IntList rows = new IntArrayList();
    List<Pair<Object, T>> reversed = ContainerUtil.reverse(myModel.data);
    for (int i = 0, size = reversed.size(); i < size; i++) {
      Pair<Object, T> p = reversed.get(i);
      if (keyMatches(p.first, file, strict)) {
        rows.add(size - i - 1);
        if (!all) break;
      }
    }
    return rows.toIntArray();
  }

  private static String keyToString(Object o) {
    if (o == null) return "";
    if (o instanceof String) return (String)o;
    if (o instanceof VirtualFile) return FileUtil.toSystemDependentName(((VirtualFile)o).getPath());
    return String.valueOf(o);
  }

  private void setupPerFileTable() {
    myTable.setEnableAntialiasing(true);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myTable.setRowSelectionAllowed(true);
    myTable.setShowGrid(false);
    myTable.getColumnModel().setColumnMargin(0);
    myTable.getTableHeader().setReorderingAllowed(false);
    TableRowSorter<MyModel<T>> sorter = new TableRowSorter<>(myModel);
    sorter.setStringConverter(new TableStringConverter() {
      final SimpleColoredText text = new SimpleColoredText();
      @Override
      public String toString(TableModel model, int row, int column) {
        text.clear();
        Pair<Object, T> pair = myModel.data.get(row);
        if (column == 0) renderTarget(pair.first, text);
        else if (pair.second != null) renderValue(pair.first, pair.second, text);
        else renderDefaultValue(pair.first, text);
        return StringUtil.toLowerCase(text.toString());
      }
    });
    sorter.setSortable(0, true);
    sorter.setSortable(1, true);
    sorter.setSortsOnUpdates(true);
    myTable.setRowSorter(sorter);
    myTable.getRowSorter().setSortKeys(Collections.singletonList(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
    TableSpeedSearch.installOn(myTable, o -> keyToString(o));

    FontMetrics metrics = myTable.getFontMetrics(myTable.getFont());
    int maxValueWidth = 2 * metrics.stringWidth(myTable.getModel().getColumnName(1));
    SimpleColoredText text = new SimpleColoredText();
    for (T t : getValueVariants(null)) {
      text.clear();
      renderValue(null, t, text);
      maxValueWidth = Math.max(metrics.stringWidth(text.toString()), maxValueWidth);
    }
    if (maxValueWidth < 300) {
      myTable.getColumnModel().getColumn(1).setMinWidth(maxValueWidth);
    }
    myTable.getColumnModel().getColumn(0).setMinWidth(metrics.stringWidth(myTable.getModel().getColumnName(0)) * 2);
    myTable.getColumnModel().getColumn(0).setCellRenderer(new ColoredTableCellRenderer() {
      @Override
      public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
        super.acquireState(table, isSelected, false, row, column);
      }

      @Override
      protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
        renderTarget(value, this);
        SpeedSearchUtil.applySpeedSearchHighlighting(table, this, false, selected);
      }
    });
    myTable.getColumnModel().getColumn(1).setCellRenderer(new ColoredTableCellRenderer() {
      @Override
      public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
        super.acquireState(table, isSelected, false, row, column);
      }

      @Override
      protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
        Pair<Object, T> p = myModel.data.get(myTable.convertRowIndexToModel(row));
        if (p.second != null) {
          setTransparentIconBackground(true);
          renderValue(p.first, p.second, this);
        }
        else {
          renderDefaultValue(p.first, this);
        }
      }
    });
    myTable.getColumnModel().getColumn(0).setCellEditor(new AbstractTableCellEditor() {
      VirtualFile startValue;
      String newPath;

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        int modelRow = myTable.convertRowIndexToModel(row);
        Pair<Object, T> pair = myModel.data.get(modelRow);
        Object target = pair.first;
        if (!(target instanceof VirtualFile)) return null;
        startValue = (VirtualFile)target;
        newPath = null;

        TextFieldWithBrowseButton panel = new TextFieldWithBrowseButton();
        panel.setText(startValue.getPath());
        panel.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(@NotNull DocumentEvent e) {
            newPath = panel.getTextField().getText();
          }
        });
        panel.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleLocalFileDescriptor()));
        return panel;
      }

      @Override
      public Object getCellEditorValue() {
        if (newPath == null || newPath.equals(startValue.getPath())) return startValue;
        VirtualFile newFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(this.newPath);
        return ObjectUtils.notNull(newFile, startValue);
      }
    });
    myTable.getColumnModel().getColumn(1).setCellEditor(new AbstractTableCellEditor() {
      T editorValue;

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        int modelRow = myTable.convertRowIndexToModel(row);
        Pair<Object, T> pair = myModel.data.get(modelRow);
        Object target = pair.first;
        editorValue = pair.second; // (T)value
        if (!canEditTarget(target, editorValue)) return null;

        JPanel panel = createActionPanel(target, new Value<>() {
          @Override
          public T get() {
            return editorValue;
          }

          @Override
          public void set(T value) {
            editorValue = adjustChosenValue(target, value);
          }

          @Override
          public void commit() {
            TableUtil.stopEditing(myTable);
            selectRows(new int[]{modelRow}, true);
            if (Comparing.equal(editorValue, pair.second)) {
              // do nothing
            }
            else {
              int ret = clearSubdirectoriesOnDemandOrCancel(false, target);
              if (ret == Messages.CANCEL) {
                myModel.setValueAt(value, modelRow, column);
              }
              selectRows(new int[]{modelRow}, true);
            }          }
        }, true);
        return panel;
      }

      @Override
      public Object getCellEditorValue() {
        return editorValue;
      }
    });
  }

  protected @NotNull JPanel createActionPanel(@Nullable Object target, @NotNull Value<T> value) {
    return createActionPanel(target, value, false);
  }

  private @NotNull JPanel createActionPanel(@Nullable Object target, @NotNull Value<T> value, boolean editor) {
    AnAction changeAction = createValueAction(target, value);
    return createActionPanel(editor, changeAction);
  }

  protected JPanel createActionPanel(AnAction changeAction) {
    return createActionPanel(false, changeAction);
  }

  private @NotNull JPanel createActionPanel(boolean editor, AnAction changeAction) {
    JComponent comboComponent = ((CustomComponentAction)changeAction).createCustomComponent(
      changeAction.getTemplatePresentation(), ActionPlaces.UNKNOWN);
    JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public Color getBackground() {
        // track "Table.selectionInactiveBackground" switch
        Container parent = getParent();
        return parent instanceof JTable ? ((JTable)parent).getSelectionBackground() : super.getBackground();
      }
    };
    panel.add(comboComponent, BorderLayout.CENTER);
    comboComponent.setOpaque(false);
    DataContext dataContext = SimpleDataContext.getProjectContext(myProject);
    AnActionEvent event = AnActionEvent.createFromAnAction(changeAction, null, ActionPlaces.UNKNOWN, dataContext);
    changeAction.update(event);
    panel.revalidate();
    if (!editor) myResetRunnables.add(() -> changeAction.update(event));
    return panel;
  }

  private int clearSubdirectoriesOnDemandOrCancel(boolean keysToo, Object... keys) {
    IntList rows = new IntArrayList();
    boolean toOverride = false;
    for (int i = 0, size = myModel.data.size(); i < size; i++) {
      Pair<Object, T> p = myModel.data.get(i);
      if (p.first instanceof VirtualFile) {
        for (Object key : keys) {
          if (key == p.first) {
            if (keysToo) rows.add(-i - 1);
            break;
          }
          else if (keyMatches(key, (VirtualFile)p.first, true)) {
            toOverride = true;
            rows.add(i);
            break;
          }
        }
      }
    }
    int ret = !toOverride ? Messages.NO : askUserToOverrideSubdirectories();
    if (ret == Messages.CANCEL) return ret;
    int count = 0;
    for (int i : rows.toIntArray()) {
      if (i >= 0 && ret == Messages.NO) continue;
      int index = (i >= 0 ? i : -i - 1) - count;
      if (canRemoveTarget(myModel.data.get(index).first)) {
        myModel.data.remove(index);
        count ++;
      }
      else {
        myModel.data.set(index, pair(myModel.data.get(0).first, null));
      }
    }
    if (!rows.isEmpty()) {
      myModel.fireTableDataChanged();
    }
    return ret;
  }

  private int askUserToOverrideSubdirectories() {
    String question = param(OVERRIDE_QUESTION);
    String title = param(OVERRIDE_TITLE);
    if (question == null || title == null) return Messages.NO;
    return Messages.showYesNoCancelDialog(
      myProject, question, title, LangBundle.message("button.override"), LangBundle.message("button.do.not.override"),
      CommonBundle.getCancelButtonText(), Messages.getWarningIcon());
  }

  private @NlsActions.ActionText String renderValue(@Nullable Object value, @NlsActions.ActionText @NotNull String nullValue) {
    if (value == null) {
      return nullValue;
    }
    else {
      SimpleColoredText text = new SimpleColoredText();
      @SuppressWarnings("unchecked") T t = (T)value;
      renderValue(null, t, text);
      return text.toString();
    }
  }

  protected void renderTarget(@Nullable Object target, @NotNull ColoredTextContainer renderer) {
    VirtualFile file = target instanceof VirtualFile ? (VirtualFile)target : null;
    if (file != null) {
      renderer.setIcon(IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, myProject));
      VirtualFile parent = file.getParent();
      if (parent != null) {
        String projectPath = myProject.getBasePath();
        String parentPath = parent.getPath();
        String relativePath = projectPath != null && parentPath.startsWith(projectPath) ?
                              "..." + parentPath.substring(projectPath.length()) : parentPath;
        String presentablePath = FileUtil.toSystemDependentName(relativePath + "/");
        renderer.append(presentablePath, SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      renderer.append(file.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else if (target == null) {
      renderer.append(LangBundle.message("PerFileConfigurableBase.label.project"), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  protected final @NotNull AnAction createValueAction(@Nullable Object target, @NotNull Value<T> value) {
    return new PerFileConfigurableComboBoxAction(value, target, StringUtil.notNullize(getNullValueText(target)));
  }

  protected @NotNull JBPopup createValueEditorPopup(@Nullable Object target,
                                                    @Nullable T value,
                                                    @Nullable Runnable onDispose,
                                                    @NotNull DataContext dataContext,
                                                    @NotNull Consumer<? super T> onChosen,
                                                    @NotNull Runnable onCommit) {
    return createValueEditorActionListPopup(target, onDispose, dataContext, chosen -> {
      onChosen.consume(chosen);
      onCommit.run();
    });
  }

  protected final @NotNull JBPopup createValueEditorActionListPopup(@Nullable Object target,
                                                                    @Nullable Runnable onDispose,
                                                                    @NotNull DataContext dataContext,
                                                                    @NotNull Consumer<? super T> onChosen) {
    ActionGroup group = createActionListGroup(target, onChosen);
    return JBPopupFactory.getInstance().createActionGroupPopup(
      null, group, dataContext, false, false, false,
      onDispose, 30, null);
  }

  protected @Nullable Icon getActionListIcon(@Nullable Object target, T t) {
    return null;
  }

  protected @NlsActions.ActionText @Nullable String getClearValueText(@Nullable Object target) {
    return target == null ? getNullValueText(null) : null;
  }

  protected @NlsActions.ActionText @Nullable String getNullValueText(@Nullable Object target) {
    return param(NULL_TEXT);
  }

  protected @NotNull Collection<T> getValueVariants(@Nullable Object target) {
    if (myMappings instanceof PerFileMappingsBase) return ((PerFileMappingsBase<T>)myMappings).getAvailableValues();
    throw new UnsupportedOperationException();
  }

  protected @NotNull ActionGroup createActionListGroup(@Nullable Object target, @NotNull Consumer<? super T> onChosen) {
    DefaultActionGroup group = new DefaultActionGroup();
    String clearText = getClearValueText(target);
    Function<T, AnAction> choseAction = t -> {
      String nullValue = StringUtil.notNullize(clearText);
      AnAction a = new DumbAwareAction(renderValue(t, nullValue), "", getActionListIcon(target, t)) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          onChosen.consume(t);
        }
      };
      a.getTemplatePresentation().setText(renderValue(t, nullValue));
      return a;
    };
    if (clearText != null) {
      group.add(choseAction.fun(null));
    }
    SimpleColoredText text = new SimpleColoredText();
    List<T> values = new ArrayList<>(getValueVariants(target));

    if (param(SORT_VALUES)) {
      Function<T, String> toString = o -> {
        text.clear();
        renderValue(target, o, text);
        return text.toString();
      };
      values.sort((o1, o2) -> StringUtil.naturalCompare(toString.fun(o1), toString.fun(o2)));
    }
    for (T t : values) {
      group.add(choseAction.fun(t));
    }
    return group;
  }

  private static final class MyModel<T> extends AbstractTableModel {
    final String[] columnNames;
    final List<Pair<Object, T>> data = new ArrayList<>();

    MyModel(String... names) {
      columnNames = names;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return true;
    }

    @Override
    public int getRowCount() {
      return data.size();
    }

    @Override
    public String getColumnName(int column) {
      return columnNames[column];
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      return columnIndex == 0 ? data.get(rowIndex).first : data.get(rowIndex).second;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      Pair<Object, T> pair = data.get(rowIndex);
      if (columnIndex == 1) {
        if (Comparing.equal(aValue, pair.second)) return;
        @SuppressWarnings("unchecked") T t = (T)aValue;
        data.set(rowIndex, pair(pair.first, t));
      }
      else {
        if (Comparing.equal(aValue, pair.first)) return;
        data.set(rowIndex, pair(aValue, pair.second));
      }
      fireTableRowsUpdated(rowIndex, rowIndex);
    }

    @Override
    public void fireTableDataChanged() {
      data.sort((o1, o2) -> StringUtil.naturalCompare(keyToString(o1.first), keyToString(o2.first)));
      super.fireTableDataChanged();
    }
  }

  public final class PerFileConfigurableComboBoxAction extends ComboBoxAction {
    private final @NotNull Value<T> myValue;
    private final @Nullable Object myTarget;
    private final @NlsActions.ActionText @NotNull String myNullValue;

    public PerFileConfigurableComboBoxAction(@NotNull Value<T> value,
                                             @Nullable Object target,
                                             @NotNull @NlsActions.ActionText String nullValue) {
      myValue = value;
      myTarget = target;
      myNullValue = nullValue;
    }

    private void updateText(Presentation p) {
      final String text = renderValue(myValue.get(), myNullValue);
      p.setText(StringUtil.shortenTextWithEllipsis(text, 40, 0));
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      updateText(getTemplatePresentation());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    protected @NotNull ComboBoxButton createComboBoxButton(@NotNull Presentation presentation) {
      return new ComboBoxButton(presentation) {
        @Override
        protected @NotNull JBPopup createPopup(Runnable onDispose) {
          JBPopup popup = createValueEditorPopup(myTarget, myValue.get(), onDispose, getDataContext(), o -> {
            myValue.set(o);
            updateText(presentation);
          }, myValue::commit);
          popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
          return popup;
        }

        @Override
        public @Nullable String getToolTipText() {
          boolean cellEditor = UIUtil.uiParents(this, true).take(4).filter(JBTable.class).first() != null;
          return cellEditor ? null : getToolTipFor(myValue.get());
        }
      };
    }
  }
}
