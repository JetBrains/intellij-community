// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.OrderEntryAppearanceService;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.EditExistingLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ConvertModuleLibraryToRepositoryLibraryAction;
import com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.SdkProjectStructureElement;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.TextTransferable;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public final class ClasspathPanelImpl extends JPanel implements ClasspathPanel {
  private static final Logger LOG = Logger.getInstance(ClasspathPanelImpl.class);
  private final JBTable myEntryTable;
  private final ClasspathTableModel myModel;
  private final EventDispatcher<OrderPanelListener> myListeners = EventDispatcher.create(OrderPanelListener.class);
  private List<AddItemPopupAction<?>> myPopupActions = null;
  private final AnAction myEditButton;
  private final ModuleConfigurationState myState;
  private AnAction myRemoveButton;

  public ClasspathPanelImpl(ModuleConfigurationState state) {
    super(new BorderLayout());

    myState = state;
    myModel = new ClasspathTableModel(state, getStructureConfigurableContext());
    myEntryTable = new JBTable(myModel) {
      @Override
      protected TableRowSorter<TableModel> createRowSorter(TableModel model) {
        return new DefaultColumnInfoBasedRowSorter(model) {
          @Override
          public void toggleSortOrder(int column) {
            if (isSortable(column)) {
              SortKey oldKey = ContainerUtil.getFirstItem(getSortKeys());
              SortOrder oldOrder;
              if (oldKey == null || oldKey.getColumn() != column) {
                oldOrder = SortOrder.UNSORTED;
              }
              else {
                oldOrder = oldKey.getSortOrder();
              }
              setSortKeys(Collections.singletonList(new SortKey(column, getNextSortOrder(oldOrder))));
            }
          }
        };
      }
    };
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));

    myEntryTable.setDefaultRenderer(ClasspathTableItem.class, new TableItemRenderer(getStructureConfigurableContext()));
    myEntryTable.setDefaultRenderer(Boolean.class, new ExportFlagRenderer());

    JComboBox<DependencyScope> scopeEditor = new ComboBox<>(new EnumComboBoxModel<>(DependencyScope.class));
    myEntryTable.setDefaultEditor(DependencyScope.class, new DefaultCellEditor(scopeEditor));
    myEntryTable.setDefaultRenderer(DependencyScope.class, new ComboBoxTableRenderer<>(DependencyScope.values()) {
      @Override
      protected String getTextFor(final @NotNull DependencyScope value) {
        return value.getDisplayName();
      }
    });

    myEntryTable.setTransferHandler(new TransferHandler() {
      @Override
      protected @Nullable Transferable createTransferable(JComponent c) {
        OrderEntry entry = getSelectedEntry();
        if (entry == null) {
          return null;
        }
        return new TextTransferable(entry.getPresentableName());
      }

      @Override
      public int getSourceActions(JComponent c) {
        return COPY;
      }
    });

    myEntryTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    SpeedSearchBase<JBTable> search = new SpeedSearchBase<>(myEntryTable, null) {
      @Override
      public int getSelectedIndex() {
        return myEntryTable.getSelectedRow();
      }

      @Override
      protected int getElementCount() {
        return myModel.getRowCount();
      }

      @Override
      protected Object getElementAt(int viewIndex) {
        return myModel.getItem(myEntryTable.convertRowIndexToModel(viewIndex));
      }

      @Override
      public String getElementText(Object element) {
        return getCellAppearance((ClasspathTableItem<?>)element, getStructureConfigurableContext(), false).getText();
      }

      @Override
      public void selectElement(Object element, String selectedText) {
        final int count = myModel.getRowCount();
        for (int row = 0; row < count; row++) {
          if (element.equals(myModel.getItem(row))) {
            final int viewRow = myEntryTable.convertRowIndexToView(row);
            myEntryTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            TableUtil.scrollSelectionToVisible(myEntryTable);
            break;
          }
        }
      }
    };
    search.setupListeners();
    setFixedColumnWidth(ClasspathTableModel.EXPORT_COLUMN, ClasspathTableModel.getExportColumnName());
    setFixedColumnWidth(ClasspathTableModel.SCOPE_COLUMN, DependencyScope.COMPILE.toString() + "     ");  // leave space for combobox border
    myEntryTable.getTableHeader().getColumnModel().getColumn(ClasspathTableModel.ITEM_COLUMN).setPreferredWidth(10000); // consume all available space

    myEntryTable.registerKeyboardAction(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = myEntryTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (final int selectedRow : selectedRows) {
            final ClasspathTableItem<?> item = getItemAt(selectedRow);
            if (selectedRow < 0 || !item.isExportable()) {
              return;
            }
            currentlyMarked &= item.isExported();
          }
          for (final int selectedRow : selectedRows) {
            getItemAt(selectedRow).setExported(!currentlyMarked);
          }
          myModel.fireTableDataChanged();
          TableUtil.selectRows(myEntryTable, selectedRows);
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      WHEN_FOCUSED
    );

    myEditButton = new DumbAwareAction(JavaUiBundle.message("module.classpath.button.edit"), null, IconUtil.getEditIcon()) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        doEdit();
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        ClasspathTableItem<?> selectedItem = getSelectedItem();
        e.getPresentation().setEnabled(selectedItem != null && selectedItem.isEditable());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };
    add(createTableWithButtons(), BorderLayout.CENTER);

    if (myEntryTable.getRowCount() > 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(0,0);
    }

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        navigate(true);
        return true;
      }
    }.installOn(myEntryTable);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    final AnAction navigateAction = new AnAction(JavaUiBundle.message("classpath.panel.navigate.action.text")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        navigate(false);
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        presentation.setEnabled(false);
        final OrderEntry entry = getSelectedEntry();
        if (entry != null && entry.isValid()){
          if (!(entry instanceof ModuleSourceOrderEntry)){
            presentation.setEnabled(true);
          }
        }
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }
    };
    navigateAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(),
                                             myEntryTable);
    actionGroup.add(myEditButton);
    actionGroup.add(myRemoveButton);
    actionGroup.add(navigateAction);
    actionGroup.add(new InlineModuleDependencyAction(this));
    actionGroup.add(new MyFindUsagesAction());
    actionGroup.add(new AnalyzeModuleDependencyAction(this));
    addChangeLibraryLevelAction(actionGroup, LibraryTablesRegistrar.PROJECT_LEVEL);
    addChangeLibraryLevelAction(actionGroup, LibraryTablesRegistrar.APPLICATION_LEVEL);
    addChangeLibraryLevelAction(actionGroup, LibraryTableImplUtil.MODULE_LEVEL);
    actionGroup.add(new ConvertModuleLibraryToRepositoryLibraryAction(this, getStructureConfigurableContext()));
    PopupHandler.installPopupMenu(myEntryTable, actionGroup, "ClassPathEntriesPopup");
  }

  private static @NotNull SortOrder getNextSortOrder(@NotNull SortOrder order) {
    return switch (order) {
      case ASCENDING -> SortOrder.DESCENDING;
      case DESCENDING -> SortOrder.UNSORTED;
      case UNSORTED -> SortOrder.ASCENDING;
    };
  }

  private ClasspathTableItem<?> getItemAt(int selectedRow) {
    return myModel.getItem(myEntryTable.convertRowIndexToModel(selectedRow));
  }

  private void addChangeLibraryLevelAction(DefaultActionGroup actionGroup, String tableLevel) {
    final LibraryTablePresentation presentation = LibraryEditingUtil.getLibraryTablePresentation(getProject(), tableLevel);
    actionGroup.add(new ChangeLibraryLevelInClasspathAction(this, presentation.getDisplayName(true), tableLevel));
  }

  @Override
  public @Nullable OrderEntry getSelectedEntry() {
    ClasspathTableItem<?> item = getSelectedItem();
    return item != null ? item.getEntry() : null;
  }

  private @Nullable ClasspathTableItem<?> getSelectedItem() {
    if (myEntryTable.getSelectedRowCount() != 1) return null;
    return getItemAt(myEntryTable.getSelectedRow());
  }

  private void setFixedColumnWidth(final int columnIndex, String sampleText) {
    final TableColumn column = myEntryTable.getTableHeader().getColumnModel().getColumn(columnIndex);
    final FontMetrics fontMetrics = myEntryTable.getFontMetrics(myEntryTable.getFont());
    final int width = fontMetrics.stringWidth(" " + sampleText + " ") + JBUIScale.scale(10);
    column.setPreferredWidth(width);
    column.setMinWidth(width);
    column.setResizable(false);
  }

  @Override
  public void navigate(boolean openLibraryEditor) {
    final OrderEntry entry = getSelectedEntry();
    final ProjectStructureConfigurable rootConfigurable = getProjectStructureConfigurable();
    if (entry instanceof ModuleOrderEntry){
      Module module = ((ModuleOrderEntry)entry).getModule();
      if (module != null) {
        rootConfigurable.select(module.getName(), null, true);
      }
    }
    else if (entry instanceof LibraryOrderEntry){
      if (!openLibraryEditor && !((LibraryOrderEntry)entry).getLibraryLevel().equals(LibraryTableImplUtil.MODULE_LEVEL)) {
        rootConfigurable.select((LibraryOrderEntry)entry, true);
      }
      else {
        doEdit();
      }
    }
    else if (entry instanceof JdkOrderEntry) {
      Sdk jdk = ((JdkOrderEntry)entry).getJdk();
      if (jdk != null) {
        rootConfigurable.select(jdk, true);
      }
    }
  }


  private JComponent createTableWithButtons() {
    final ClasspathPanelAction removeAction = new ClasspathPanelAction(this) {
      @Override
      public void run() {
        removeSelectedItems(TableUtil.removeSelectedItems(myEntryTable));
      }
    };

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myEntryTable);
    AnActionButtonUpdater moveUpDownUpdater = e -> {
      for (RowSorter.SortKey key : myEntryTable.getRowSorter().getSortKeys()) {
        if (key.getSortOrder() != SortOrder.UNSORTED) {
          return false;
        }
      }
      return true;
    };
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        initPopupActions();
        final JBPopup popup = JBPopupFactory.getInstance().createListPopup(
          new BaseListPopupStep<>(null, myPopupActions) {
            @Override
            public Icon getIconFor(AddItemPopupAction<?> aValue) {
              return aValue.getIcon();
            }

            @Override
            public boolean hasSubstep(AddItemPopupAction<?> selectedValue) {
              return selectedValue.hasSubStep();
            }

            @Override
            public boolean isMnemonicsNavigationEnabled() {
              return true;
            }

            @Override
            public PopupStep<?> onChosen(final AddItemPopupAction<?> selectedValue, final boolean finalChoice) {
              if (selectedValue.hasSubStep()) {
                return selectedValue.createSubStep();
              }
              return doFinalStep(() -> selectedValue.execute());
            }

            @Override
            public @NotNull String getTextFor(AddItemPopupAction<?> value) {
              return "&" + value.getIndex() + "  " + value.getTitle();
            }
          });
        final RelativePoint point = button.getPreferredPopupPoint();
        popup.show(point);
      }
    })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeAction.actionPerformed(null);
        }
      })
      .setRemoveActionUpdater(e -> {
        final int[] selectedRows = myEntryTable.getSelectedRows();
        for (final int selectedRow : selectedRows) {
          if (!getItemAt(selectedRow).isRemovable()) {
            return false;
          }
        }
        return selectedRows.length > 0;
      })
      .setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveSelectedRows(-1);
        }
      })
      .setMoveUpActionUpdater(moveUpDownUpdater)
      .setMoveUpActionName(JavaUiBundle.message("action.text.class.path.move.up"))
      .setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveSelectedRows(+1);
        }
      })
      .setMoveDownActionUpdater(moveUpDownUpdater)
      .setMoveDownActionName(JavaUiBundle.message("action.text.class.path.move.down"))
      .addExtraAction(myEditButton);

    final JPanel panel = decorator.createPanel();
    myRemoveButton = ToolbarDecorator.findRemoveButton(panel);
    return panel;
  }

  private void doEdit() {
    final OrderEntry entry = getSelectedEntry();
    if (!(entry instanceof LibraryOrderEntry)) return;

    final Library library = ((LibraryOrderEntry)entry).getLibrary();
    if (library == null) {
      return;
    }
    final LibraryTable table = library.getTable();
    final String tableLevel = table != null ? table.getTableLevel() : LibraryTableImplUtil.MODULE_LEVEL;
    final LibraryTablePresentation presentation = LibraryEditingUtil.getLibraryTablePresentation(getProject(), tableLevel);
    final LibraryTableModifiableModelProvider provider = getModifiableModelProvider(tableLevel);
    EditExistingLibraryDialog dialog = EditExistingLibraryDialog.createDialog(this, provider, library, myState.getProject(),
                                                                              presentation, getStructureConfigurableContext());
    dialog.setContextModule(getRootModel().getModule());
    if (dialog.showAndGet()) {
      if (table == null) {
        rootsChanged();
      }
      myEntryTable.repaint();
      getProjectStructureConfigurable().getModulesConfig().getTree().repaint();
    }
  }

  private void removeSelectedItems(final List<Object[]> removedRows) {
    if (removedRows.isEmpty()) {
      return;
    }
    for (final Object removedRow : removedRows) {
      final ClasspathTableItem<?> item = (ClasspathTableItem<?>)((Object[])removedRow)[ClasspathTableModel.ITEM_COLUMN];
      final OrderEntry orderEntry = item.getEntry();
      if (orderEntry == null) {
        continue;
      }

      getRootModel().removeOrderEntry(orderEntry);
    }
    final int[] selectedRows = myEntryTable.getSelectedRows();
    myModel.fireTableDataChanged();
    TableUtil.selectRows(myEntryTable, selectedRows);
    final StructureConfigurableContext context = getProjectStructureConfigurable().getContext();
    context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, getRootModel().getModule()));
  }

  private ProjectStructureConfigurable getProjectStructureConfigurable() {
    return ((ModulesConfigurator)myState.getModulesProvider()).getProjectStructureConfigurable();
  }

  @Override
  public @NotNull LibraryTableModifiableModelProvider getModifiableModelProvider(@NotNull String tableLevel) {
    if (LibraryTableImplUtil.MODULE_LEVEL.equals(tableLevel)) {
      final LibraryTable moduleLibraryTable = getRootModel().getModuleLibraryTable();
      return new LibraryTableModifiableModelProvider() {
        @Override
        public LibraryTable.ModifiableModel getModifiableModel() {
          return moduleLibraryTable.getModifiableModel();
        }
      };
    }
    else {
      return getStructureConfigurableContext().createModifiableModelProvider(tableLevel);
    }
  }

  @Override
  public void runClasspathPanelAction(Runnable action) {
    try {
      disableModelUpdate();
      action.run();
    }
    finally {
      enableModelUpdate();
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myEntryTable, true));
    }
  }

  @Override
  public void addItems(List<? extends ClasspathTableItem<?>> toAdd) {
    for (ClasspathTableItem<?> item : toAdd) {
      myModel.addRow(item);
    }
    IntList toSelect = new IntArrayList();
    for (int i = myModel.getRowCount() - toAdd.size(); i < myModel.getRowCount(); i++) {
      toSelect.add(myEntryTable.convertRowIndexToView(i));
    }
    TableUtil.selectRows(myEntryTable, toSelect.toIntArray());
    TableUtil.scrollSelectionToVisible(myEntryTable);

    final StructureConfigurableContext context = getProjectStructureConfigurable().getContext();
    context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, getRootModel().getModule()));
  }

  @Override
  public ModifiableRootModel getRootModel() {
    return myState.getModifiableRootModel();
  }

  @Override
  public Project getProject() {
    return myState.getProject();
  }

  @Override
  public ModuleConfigurationState getModuleConfigurationState() {
    return myState;
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  public void rootsChanged() {
    forceInitFromModel();
  }

  private void initPopupActions() {
    if (myPopupActions == null) {
      int actionIndex = 1;
      final List<AddItemPopupAction<?>> actions = new ArrayList<>();
      final StructureConfigurableContext context = getStructureConfigurableContext();
      actions.add(new AddNewModuleLibraryAction(this, actionIndex++, context));
      actions.add(new AddLibraryDependencyAction(this, actionIndex++, JavaUiBundle.message("classpath.add.library.action"), context));
      actions.add(new AddModuleDependencyAction(this, actionIndex, context)
      );

      myPopupActions = actions;
    }
  }

  private StructureConfigurableContext getStructureConfigurableContext() {
    return getProjectStructureConfigurable().getContext();
  }


  private void enableModelUpdate() {
    myInsideChange--;
  }

  private void disableModelUpdate() {
    myInsideChange++;
  }

  public void addListener(OrderPanelListener listener) {
    myListeners.addListener(listener);
  }

  public void removeListener(OrderPanelListener listener) {
    myListeners.removeListener(listener);
  }

  private void moveSelectedRows(int increment) {
    LOG.assertTrue(increment == -1 || increment == 1);
    if (myEntryTable.isEditing()) {
      myEntryTable.getCellEditor().stopCellEditing();
    }
    final ListSelectionModel selectionModel = myEntryTable.getSelectionModel();
    for (int row = increment < 0 ? 0 : myModel.getRowCount() - 1; increment < 0 ? row < myModel.getRowCount() : row >= 0; row +=
      increment < 0 ? +1 : -1) {
      if (selectionModel.isSelectedIndex(row)) {
        final int newRow = moveRow(row, increment);
        selectionModel.removeSelectionInterval(row, row);
        selectionModel.addSelectionInterval(newRow, newRow);
      }
    }
    Rectangle cellRect = myEntryTable.getCellRect(selectionModel.getMinSelectionIndex(), 0, true);
    myEntryTable.scrollRectToVisible(cellRect);
    myEntryTable.repaint();
  }

  public void selectOrderEntry(@NotNull OrderEntry entry) {
    for (int row = 0; row < myModel.getRowCount(); row++) {
      final OrderEntry orderEntry = getItemAt(row).getEntry();
      if (orderEntry != null && entry.getPresentableName().equals(orderEntry.getPresentableName())) {
        if (orderEntry instanceof ExportableOrderEntry && entry instanceof ExportableOrderEntry &&
            ((ExportableOrderEntry)entry).getScope() != ((ExportableOrderEntry)orderEntry).getScope()) {
          continue;
        }
        myEntryTable.getSelectionModel().setSelectionInterval(row, row);
        TableUtil.scrollSelectionToVisible(myEntryTable);
      }
    }
    IdeFocusManager.getInstance(myState.getProject()).requestFocus(myEntryTable, true);
  }

  private int moveRow(final int row, final int increment) {
    int newIndex = Math.abs(row + increment) % myModel.getRowCount();
    myModel.exchangeRows(row, newIndex);
    return newIndex;
  }

  public void stopEditing() {
    TableUtil.stopEditing(myEntryTable);
  }

  private int myInsideChange = 0;
  public void initFromModel() {
    if (myInsideChange == 0) {
      forceInitFromModel();
    }
  }

  public void forceInitFromModel() {
    Set<ClasspathTableItem<?>> oldSelection = new HashSet<>();
    for (int i : myEntryTable.getSelectedRows()) {
      ContainerUtil.addIfNotNull(oldSelection, getItemAt(i));
    }
    myModel.clear();
    myModel.init();
    myModel.fireTableDataChanged();
    IntList newSelection = new IntArrayList();
    for (int i = 0; i < myModel.getRowCount(); i++) {
      if (oldSelection.contains(getItemAt(i))) {
        newSelection.add(i);
      }
    }
    TableUtil.selectRows(myEntryTable, newSelection.toIntArray());
  }

  static CellAppearanceEx getCellAppearance(final ClasspathTableItem<?> item,
                                            final StructureConfigurableContext context,
                                            final boolean selected) {
    final OrderEntryAppearanceService service = OrderEntryAppearanceService.getInstance();
    if (item instanceof InvalidJdkItem) {
      return service.forJdk(null, false, selected, true);
    }
    else {
      final OrderEntry entry = item.getEntry();
      assert entry != null : item;
      return service.forOrderEntry(context.getProject(), entry, selected);
    }
  }

  private static class TableItemRenderer extends ColoredTableCellRenderer {
    private final Border NO_FOCUS_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);
    private final StructureConfigurableContext myContext;

    TableItemRenderer(StructureConfigurableContext context) {
      myContext = context;
    }

    @Override
    protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setPaintFocusBorder(false);
      setFocusBorderAroundIcon(true);
      setBorder(NO_FOCUS_BORDER);
      if (value instanceof ClasspathTableItem<?> tableItem) {
        getCellAppearance(tableItem, myContext, selected).customize(this);
        setToolTipText(tableItem.getTooltipText());
      }
    }
  }

  private static class ExportFlagRenderer extends BooleanTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      return super.getTableCellRendererComponent(table, table.isCellEditable(row, column) ? value : null, isSelected, hasFocus, row, column);
    }
  }

  private final class MyFindUsagesAction extends FindUsagesInProjectStructureActionBase {
    private MyFindUsagesAction() {
      super(myEntryTable, getProjectStructureConfigurable());
    }

    @Override
    protected boolean isEnabled() {
      return getSelectedElement() != null;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    protected ProjectStructureElement getSelectedElement() {
      final OrderEntry entry = getSelectedEntry();
      if (entry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library != null) {
          return new LibraryProjectStructureElement(getContext(), library);
        }
      }
      else if (entry instanceof ModuleOrderEntry) {
        final Module module = ((ModuleOrderEntry)entry).getModule();
        if (module != null) {
          return new ModuleProjectStructureElement(getContext(), module);
        }
      }
      else if (entry instanceof JdkOrderEntry) {
        final Sdk jdk = ((JdkOrderEntry)entry).getJdk();
        if (jdk != null) {
          return new SdkProjectStructureElement(getContext(), jdk);
        }
      }
      return null;
    }

    @Override
    protected RelativePoint getPointToShowResults() {
      Rectangle rect = myEntryTable.getCellRect(myEntryTable.getSelectedRow(), 1, false);
      Point location = rect.getLocation();
      location.y += rect.height;
      return new RelativePoint(myEntryTable, location);
    }
  }
}
