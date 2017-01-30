/*
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
package com.intellij.util.ui.tree;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.LanguagePerFileMappings;
import com.intellij.lang.PerFileMappings;
import com.intellij.lang.PerFileMappingsBase;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.table.TableStringConverter;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.intellij.ui.IdeBorderFactory.*;

/**
 * @author peter
 */
public abstract class PerFileConfigurableBase<T> implements SearchableConfigurable, Configurable.NoScroll {

  protected static final Key<String> DESCRIPTION = KeyWithDefaultValue.create("DESCRIPTION", "");
  protected static final Key<String> TARGET_TITLE = KeyWithDefaultValue.create("TARGET_TITLE", "Path");
  protected static final Key<String> MAPPING_TITLE = KeyWithDefaultValue.create("MAPPING_TITLE", "Mapping");
  protected static final Key<String> EMPTY_TEXT = KeyWithDefaultValue.create("EMPTY_TEXT", "New Mapping $addShortcut");
  protected static final Key<String> OVERRIDE_QUESTION = Key.create("OVERRIDE_QUESTION");
  protected static final Key<String> OVERRIDE_TITLE = Key.create("OVERRIDE_TITLE");
  protected static final Key<String> CLEAR_TEXT = KeyWithDefaultValue.create("CLEAR_TEXT", "<Clear>");
  protected static final Key<String> NULL_TEXT = KeyWithDefaultValue.create("NULL_TEXT", "<None>");

  protected final Project myProject;
  protected final PerFileMappings<T> myMappings;

  /** @noinspection FieldCanBeLocal */
  private JPanel myPanel;
  private JBTable myTable;
  private MyModel<T> myModel;

  private final List<Runnable> myResetRunnables = ContainerUtil.newArrayList();
  private final Map<String, T> myDefaultVals = ContainerUtil.newHashMap();
  private final List<Trinity<String, Producer<T>, Consumer<T>>> myDefaultProps = ContainerUtil.newArrayList();
  private VirtualFile myFileToSelect;

  protected PerFileConfigurableBase(@NotNull Project project, @NotNull PerFileMappings<T> mappings) {
    myProject = project;
    myMappings = mappings;
  }

  @Override
  @NotNull
  public String getId() {
    return getDisplayName();
  }

  @Nullable
  protected abstract <S> Object getParameter(@NotNull Key<S> key);

  @NotNull
  protected List<Trinity<String, Producer<T>, Consumer<T>>> getDefaultMappings() {
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

  protected void renderDefaultValue(@Nullable Object target, @NotNull ColoredTextContainer renderer) {
  }


  private <S> S param(@NotNull Key<S> key) {
    Object o = getParameter(key);
    if (o == null && key instanceof KeyWithDefaultValue) return ((KeyWithDefaultValue<S>)key).getDefaultValue();
    //noinspection unchecked
    return (S)o;
  }

  @NotNull
  @Override
  public JComponent createComponent() {
    //todo multi-editing, separate project/ide combos _if_ needed by specific configurable (SQL, no Web)
    myPanel = new JPanel(new BorderLayout());
    myTable = new JBTable(myModel = new MyModel<>(param(TARGET_TITLE), param(MAPPING_TITLE)));
    setupPerFileTable();
    JPanel tablePanel = ToolbarDecorator.createDecorator(myTable)
      .disableUpAction()
      .disableDownAction()
      .setAddAction(button -> doAddAction(button))
      .setRemoveAction(button -> doRemoveAction(button))
      .setEditAction(button -> doEditAction(button))
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

  @Nullable
  protected JComponent createDefaultMappingComponent() {
    myDefaultProps.addAll(getDefaultMappings());
    if (myMappings instanceof LanguagePerFileMappings) {
      myDefaultProps.add(Trinity.create("Project " + StringUtil.capitalize(param(MAPPING_TITLE)),
                                        () -> ((LanguagePerFileMappings<T>)myMappings).getConfiguredMapping(null),
                                        o -> myMappings.setMapping(null, o)));
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
    panel.add(Box.createGlue(), new GridBagConstraints(2, 0, 1, 1, 1., 1., GridBagConstraints.CENTER, GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));

    for (Trinity<String, Producer<T>, Consumer<T>> prop : myDefaultProps) {
      myDefaultVals.put(prop.first, prop.second.produce());
      JPanel p = createActionPanel(null,
                                   () -> myDefaultVals.get(prop.first),
                                   o -> myDefaultVals.put(prop.first, adjustChosenValue(null, o)));
      panel.add(new JBLabel(prop.first + ":"), cons1);
      panel.add(p, cons2);
    }
    return panel;
  }

  private void doAddAction(@NotNull AnActionButton button) {
    int row = myTable.getSelectedRow();
    Pair<Object, T> selectedRow = myModel.data.get(myTable.convertRowIndexToModel(row));
    VirtualFile toSelect = myFileToSelect != null ? myFileToSelect :
                           row >= 0 ? ObjectUtils.tryCast(selectedRow.first, VirtualFile.class) : null;
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, true, true, true, true);
    Set<VirtualFile> chosen = ContainerUtil.newHashSet(FileChooser.chooseFiles(descriptor, myProject, toSelect));
    Set<Object> set = myModel.data.stream().map(o -> o.first).collect(Collectors.toSet());
    for (VirtualFile file : chosen) {
      if (!set.add(file)) continue;
      myModel.data.add(Pair.create(file, null));
    }
    fireMappingChanged();
    TIntArrayList rowList = new TIntArrayList();
    for (int i = 0, size = myModel.data.size(); i < size; i++) {
      if (chosen.contains(myModel.data.get(i).first)) rowList.add(i);
    }
    int[] rows = rowList.toNativeArray();
    for (int i = 0; i < rows.length; i++) {
      rows[i] = myTable.convertRowIndexToView(rows[i]);
    }
    TableUtil.selectRows(myTable, rows);
  }

  private void doRemoveAction(@NotNull AnActionButton button) {
    int[] rows = myTable.getSelectedRows();
    int firstRow = rows[0];
    Object[] keys = new Object[rows.length];
    for (int i = 0; i < rows.length; i++) {
      keys[i] = myModel.data.get(myTable.convertRowIndexToModel(rows[i])).first;
    }
    if (clearSubdirectoriesOnDemandOrCancel(true, keys)) {
      int toSelect = Math.min(myModel.data.size() - 1, firstRow);
      if (toSelect >= 0) {
        TableUtil.selectRows(myTable, new int[]{toSelect});
      }
    }
  }

  private void doEditAction(@NotNull AnActionButton button) {
    myTable.editCellAt(myTable.getSelectedRow(), 1);
  }

  @Nullable
  public T getNewMapping(VirtualFile file) {
    for (Pair<Object, T> p : ContainerUtil.reverse(myModel.data)) {
      if (keyMatches(p.first, file, false) && p.second != null) return p.second;
    }
    for (Trinity<String, Producer<T>, Consumer<T>> prop : ContainerUtil.reverse(myDefaultProps)) {
      if (prop.first.startsWith("Project ") || prop.first.startsWith("Global ")) {
        T t = myDefaultVals.get(prop.first);
        if (t != null) return t;
      }
    }
    return myMappings.getDefaultMapping(file);
  }

  private boolean keyMatches(Object key, VirtualFile file, boolean strict) {
    if (key instanceof VirtualFile) return VfsUtilCore.isAncestor((VirtualFile)key, file, strict);
    // todo also patterns
    if (key == null) return true;
    return false;
  }

  @Override
  public boolean isModified() {
    for (Trinity<String, Producer<T>, Consumer<T>> prop : myDefaultProps) {
      if (!Comparing.equal(prop.second.produce(), myDefaultVals.get(prop.first))) {
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
    for (Trinity<String, Producer<T>, Consumer<T>> prop : myDefaultProps) {
      prop.third.consume(myDefaultVals.get(prop.first));
    }
  }

  @Override
  public void reset() {
    myModel.data.clear();
    for (Map.Entry<VirtualFile, T> e : myMappings.getMappings().entrySet()) {
      if (myMappings instanceof LanguagePerFileMappings && e.getKey() == null) continue;
      myModel.data.add(Pair.create(e.getKey(), e.getValue()));
    }
    for (Trinity<String, Producer<T>, Consumer<T>> prop : myDefaultProps) {
      myDefaultVals.put(prop.first, prop.second.produce());
    }

    for (Runnable runnable : myResetRunnables) {
      runnable.run();
    }
    int[] rows = myTable.getSelectedRows();
    fireMappingChanged();
    TableUtil.selectRows(myTable, rows);
  }

  protected void fireMappingChanged() {
    Collections.sort(myModel.data, (o1, o2) -> StringUtil.naturalCompare(keyToString(o1.first), keyToString(o2.first)));
    myModel.fireTableDataChanged();
  }

  protected Map<VirtualFile, T> getNewMappings() {
    HashMap<VirtualFile, T> map = ContainerUtil.newHashMap();
    for (Pair<Object, T> p : myModel.data) {
      if (p.second != null) {
        map.put((VirtualFile)p.first, p.second);
      }
    }
    if (myMappings instanceof LanguagePerFileMappings) {
      for (Trinity<String, Producer<T>, Consumer<T>> prop : ContainerUtil.reverse(myDefaultProps)) {
        if (prop.first.startsWith("Project ")) {
          T t = myDefaultVals.get(prop.first);
          if (t != null) map.put(null, t);
          break;
        }
      }
    }
    return map;
  }

  public void selectFile(@NotNull VirtualFile virtualFile) {
    VirtualFile file = virtualFile instanceof VirtualFileWindow ? ((VirtualFileWindow)virtualFile).getDelegate() : virtualFile;
    int[] rows = findRow(file, false, false);
    for (int i = 0; i < rows.length; i++) {
      rows[i] = myTable.convertRowIndexToView(rows[i]);
    }
    TableUtil.selectRows(myTable, rows);
    myFileToSelect = file;
  }

  protected int[] findRow(VirtualFile file, boolean strict, boolean all) {
    TIntArrayList rows = new TIntArrayList();
    List<Pair<Object, T>> reversed = ContainerUtil.reverse(myModel.data);
    for (int i = 0, size = reversed.size(); i < size; i++) {
      Pair<Object, T> p = reversed.get(i);
      if (keyMatches(p.first, file, strict)) {
        rows.add(size - i - 1);
        if (!all) break;
      }
    }
    return rows.toNativeArray();
  }

  private static String keyToString(Object o) {
    if (o == null) return "";
    if (o instanceof String) return (String)o;
    if (o instanceof VirtualFile) return ((VirtualFile)o).getPath();
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
    myTable.setRowSorter(sorter);
    new TableSpeedSearch(myTable, o -> keyToString(o));

    FontMetrics metrics = myTable.getFontMetrics(myTable.getFont());
    int maxValueWidth = 2 * metrics.stringWidth(myTable.getModel().getColumnName(1));
    SimpleColoredText text = new SimpleColoredText();
    for (T t : getValueVariants(null)) {
      text.clear();
      renderValue(null, t, text);
      maxValueWidth = Math.max(metrics.stringWidth(text.toString()), maxValueWidth);
    }
    myTable.getColumnModel().getColumn(1).setMinWidth(maxValueWidth);
    myTable.getColumnModel().getColumn(1).setMaxWidth(2 * maxValueWidth);
    myTable.getColumnModel().getColumn(0).setCellRenderer(new ColoredTableCellRenderer() {
      @Override
      public void acquireState(JTable table, boolean isSelected, boolean hasFocus, int row, int column) {
        super.acquireState(table, isSelected, false, row, column);
      }

      @Override
      protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
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
      protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
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
    myTable.getColumnModel().getColumn(1).setCellEditor(new AbstractTableCellEditor() {
      VirtualFile targetFile;

      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        int modelRow = myTable.convertRowIndexToModel(row);
        Object target = table.getModel().getValueAt(modelRow, 0);
        if (!canEditTarget(target, (T)value)) return null;
        targetFile = target instanceof Project ? null : (VirtualFile)target;

        JPanel panel = createActionPanel(target, () -> (T)value, chosen -> {
          TableUtil.stopEditing(myTable);
          if (clearSubdirectoriesOnDemandOrCancel(false, targetFile)) {
            myModel.setValueAt(adjustChosenValue(target, chosen), modelRow, column);
            myModel.fireTableDataChanged();
            selectFile(targetFile);
          }
        }, true);

        AbstractButton button = UIUtil.uiTraverser(panel).filter(JButton.class).first();
        if (button != null) {
          AtomicInteger clickCount = new AtomicInteger();
          button.addActionListener(e -> clickCount.incrementAndGet());
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            if (clickCount.get() == 0 && myTable.getEditorComponent() == panel) {
              button.doClick();
            }
          });
        }

        return panel;
      }

      @Override
      public Object getCellEditorValue() {
        return myModel.getValueAt(myTable.getEditingRow(), 1);
      }
    });
  }

  @NotNull
  protected JPanel createActionPanel(@Nullable Object target, Producer<T> value, @NotNull Consumer<T> consumer) {
    return createActionPanel(target, value, consumer, false);
  }

  @NotNull
  private JPanel createActionPanel(@Nullable Object target, Producer<T> value, @NotNull Consumer<T> consumer, boolean editor) {
    AnAction changeAction = createValueAction(target, value, consumer);
    JComponent comboComponent = ((CustomComponentAction)changeAction).createCustomComponent(changeAction.getTemplatePresentation());
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
    if (!editor) myResetRunnables.add(() -> changeAction.update(null));
    return panel;
  }

  private boolean clearSubdirectoriesOnDemandOrCancel(boolean keysToo, Object... keys) {
    TIntArrayList rows = new TIntArrayList();
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
    if (ret == Messages.CANCEL) return false;
    int count = 0;
    for (int i : rows.toNativeArray()) {
      if (i >= 0 && ret == Messages.NO) continue;
      int index = (i >= 0 ? i : -i - 1) - count;
      if (canRemoveTarget(myModel.data.get(index).first)) {
        myModel.data.remove(index);
        count ++;
      }
      else {
        myModel.data.set(index, Pair.create(myModel.data.get(0).first, null));
      }
    }
    if (!rows.isEmpty()) fireMappingChanged();
    return true;
  }

  private int askUserToOverrideSubdirectories() {
    String question = param(OVERRIDE_QUESTION);
    String title = param(OVERRIDE_TITLE);
    if (question == null || title == null) return Messages.NO;
    return Messages.showYesNoCancelDialog(
      myProject, question, title, "Override", "Do Not Override", "Cancel", Messages.getWarningIcon());
  }

  private String renderValue(@Nullable Object value, @NotNull String nullValue) {
    if (value == null) {
      return nullValue;
    }
    else {
      SimpleColoredText text = new SimpleColoredText();
      renderValue(null, (T)value, text);
      return text.toString();
    }
  }

  protected void renderTarget(@Nullable Object target, @NotNull ColoredTextContainer renderer) {
    VirtualFile file = target instanceof VirtualFile ? (VirtualFile)target : null;
    if (file != null) {
      renderer.setIcon(IconUtil.getIcon(file, Iconable.ICON_FLAG_READ_STATUS, myProject));
      VirtualFile parent = file.getParent();
      if (parent != null) {
        String parentPath = parent.getPath();
        renderer.append(parentPath, SimpleTextAttributes.GRAY_ATTRIBUTES);
        if (!parentPath.endsWith(File.separator)) {
          renderer.append(File.separator, SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
      }
      renderer.append(file.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else if (target == null) {
      renderer.append("Project", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @NotNull
  protected final AnAction createValueAction(@Nullable Object target, Producer<T> value, @NotNull Consumer<T> consumer) {
    return new ComboBoxAction() {
      void updateText() {
        getTemplatePresentation().setText(renderValue(value.produce(), getNullValueText(target)));
      }

      @Override
      public void update(AnActionEvent e) {
        updateText();
      }

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        throw new UnsupportedOperationException();
      }

      @Override
      protected ComboBoxButton createComboBoxButton(Presentation presentation) {
        return new ComboBoxButton(presentation) {
          protected JBPopup createPopup(Runnable onDispose) {
            JBPopup popup = createValueEditorPopup(target, onDispose, getDataContext(), o -> {
              consumer.consume(o);
              updateText();
            });
            popup.setMinimumSize(new Dimension(getMinWidth(), getMinHeight()));
            return popup;
          }
        };
      }
    };
  }

  @NotNull
  protected JBPopup createValueEditorPopup(@Nullable Object target,
                                           @Nullable Runnable onDispose,
                                           @NotNull DataContext dataContext,
                                           @NotNull Consumer<T> onChosen) {
    return createValueEditorActionListPopup(target, onDispose, dataContext, onChosen);
  }

  @NotNull
  protected final JBPopup createValueEditorActionListPopup(@Nullable Object target,
                                                           @Nullable Runnable onDispose,
                                                           @NotNull DataContext dataContext,
                                                           @NotNull Consumer<T> onChosen) {
    ActionGroup group = createActionListGroup(target, onChosen);
    return JBPopupFactory.getInstance().createActionGroupPopup(
      null, group, dataContext, false, false, false,
      onDispose, 30, null);
  }

  @Nullable
  protected Icon getActionListIcon(@Nullable Object target, T t) {
    return null;
  }

  @Nullable
  protected String getClearValueText(@Nullable Object target) {
    return target == null ? getNullValueText(null) : param(CLEAR_TEXT);
  }

  @Nullable
  protected String getNullValueText(@Nullable Object target) {
    return param(NULL_TEXT);
  }

  @NotNull
  protected Collection<T> getValueVariants(@Nullable Object target) {
    if (myMappings instanceof PerFileMappingsBase) return ((PerFileMappingsBase<T>)myMappings).getAvailableValues();
    throw new UnsupportedOperationException();
  }

  @NotNull
  protected ActionGroup createActionListGroup(@Nullable Object target, @NotNull Consumer<T> onChosen) {
    DefaultActionGroup group = new DefaultActionGroup();
    String clearText = getClearValueText(target);
    Function<T, AnAction> choseAction = t -> {
      String nullValue = StringUtil.notNullize(clearText);
      AnAction a = new DumbAwareAction(renderValue(t, nullValue), "", getActionListIcon(target, t)) {
        @Override
        public void actionPerformed(AnActionEvent e) {
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
    Function<T, String> toString = o -> {
      text.clear();
      renderValue(target, o, text);
      return text.toString();
    };
    Collections.sort(values, (o1, o2) -> StringUtil.naturalCompare(toString.fun(o1), toString.fun(o2)));
    for (T t : values) {
      group.add(choseAction.fun(t));
    }
    return group;
  }

  private static class MyModel<T> extends AbstractTableModel {

    final String[] columnNames;
    final List<Pair<Object, T>> data = ContainerUtil.newArrayList();

    public MyModel(String... names) {
      columnNames = names;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex > 0;
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
      data.set(rowIndex, Pair.create(data.get(rowIndex).first, (T)aValue));
    }
  }
}
