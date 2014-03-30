/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.classpath;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.find.FindBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.LibraryScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
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
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.dependencyAnalysis.AnalyzeDependenciesDialog;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.EditExistingLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.SdkProjectStructureElement;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.actions.AnalyzeDependenciesOnSpecifiedTargetHandler;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.JBTable;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ClasspathPanelImpl extends JPanel implements ClasspathPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.classpath.ClasspathPanelImpl");
  private final JBTable myEntryTable;
  private final ClasspathTableModel myModel;
  private final EventDispatcher<OrderPanelListener> myListeners = EventDispatcher.create(OrderPanelListener.class);
  private List<AddItemPopupAction<?>> myPopupActions = null;
  private AnActionButton myEditButton;
  private final ModuleConfigurationState myState;
  private AnActionButton myRemoveButton;

  public ClasspathPanelImpl(ModuleConfigurationState state) {
    super(new BorderLayout());

    myState = state;
    myModel = new ClasspathTableModel(state, getStructureConfigurableContext());
    myEntryTable = new JBTable(myModel);
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));

    myEntryTable.setDefaultRenderer(ClasspathTableItem.class, new TableItemRenderer(getStructureConfigurableContext()));
    myEntryTable.setDefaultRenderer(Boolean.class, new ExportFlagRenderer(myEntryTable.getDefaultRenderer(Boolean.class)));

    JComboBox scopeEditor = new JComboBox(new EnumComboBoxModel<DependencyScope>(DependencyScope.class));
    myEntryTable.setDefaultEditor(DependencyScope.class, new DefaultCellEditor(scopeEditor));
    myEntryTable.setDefaultRenderer(DependencyScope.class, new ComboBoxTableRenderer<DependencyScope>(DependencyScope.values()) {
        @Override
        protected String getTextFor(@NotNull final DependencyScope value) {
          return value.getDisplayName();
        }
      });

    myEntryTable.setTransferHandler(new TransferHandler() {
      @Nullable
      @Override
      protected Transferable createTransferable(JComponent c) {
        OrderEntry entry = getSelectedEntry();
        if (entry == null) return null;
        String text = entry.getPresentableName();
        return new TextTransferable(text);
      }

      @Override
      public int getSourceActions(JComponent c) {
        return COPY;
      }
    });

    myEntryTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    new SpeedSearchBase<JBTable>(myEntryTable) {
      @Override
      public int getSelectedIndex() {
        return myEntryTable.getSelectedRow();
      }

      @Override
      protected int convertIndexToModel(int viewIndex) {
        return myEntryTable.convertRowIndexToModel(viewIndex);
      }

      @Override
      public Object[] getAllElements() {
        final int count = myModel.getRowCount();
        Object[] elements = new Object[count];
        for (int idx = 0; idx < count; idx++) {
          elements[idx] = myModel.getItemAt(idx);
        }
        return elements;
      }

      @Override
      public String getElementText(Object element) {
        return getCellAppearance((ClasspathTableItem<?>)element, getStructureConfigurableContext(), false).getText();
      }

      @Override
      public void selectElement(Object element, String selectedText) {
        final int count = myModel.getRowCount();
        for (int row = 0; row < count; row++) {
          if (element.equals(myModel.getItemAt(row))) {
            final int viewRow = myEntryTable.convertRowIndexToView(row);
            myEntryTable.getSelectionModel().setSelectionInterval(viewRow, viewRow);
            TableUtil.scrollSelectionToVisible(myEntryTable);
            break;
          }
        }
      }
    };
    setFixedColumnWidth(ClasspathTableModel.EXPORT_COLUMN, ClasspathTableModel.EXPORT_COLUMN_NAME);
    setFixedColumnWidth(ClasspathTableModel.SCOPE_COLUMN, DependencyScope.COMPILE.toString() + "     ");  // leave space for combobox border

    myEntryTable.registerKeyboardAction(
      new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = myEntryTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (final int selectedRow : selectedRows) {
            final ClasspathTableItem<?> item = myModel.getItemAt(myEntryTable.convertRowIndexToModel(selectedRow));
            if (selectedRow < 0 || !item.isExportable()) {
              return;
            }
            currentlyMarked &= item.isExported();
          }
          for (final int selectedRow : selectedRows) {
            myModel.getItemAt(myEntryTable.convertRowIndexToModel(selectedRow)).setExported(!currentlyMarked);
          }
          myModel.fireTableDataChanged();
          TableUtil.selectRows(myEntryTable, selectedRows);
        }
      },
      KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0),
      WHEN_FOCUSED
    );

    myEditButton = new AnActionButton(ProjectBundle.message("module.classpath.button.edit"), null, IconUtil.getEditIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        doEdit();
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };
    add(createTableWithButtons(), BorderLayout.CENTER);

    if (myEntryTable.getRowCount() > 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(0,0);
    }

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        navigate(true);
        return true;
      }
    }.installOn(myEntryTable);

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    final AnAction navigateAction = new AnAction(ProjectBundle.message("classpath.panel.navigate.action.text")) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        navigate(false);
      }

      @Override
      public void update(AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        presentation.setEnabled(false);
        final OrderEntry entry = getSelectedEntry();
        if (entry != null && entry.isValid()){
          if (!(entry instanceof ModuleSourceOrderEntry)){
            presentation.setEnabled(true);
          }
        }
      }
    };
    navigateAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(),
                                             myEntryTable);
    actionGroup.add(myEditButton);
    actionGroup.add(myRemoveButton);
    actionGroup.add(navigateAction);
    actionGroup.add(new MyFindUsagesAction());
    actionGroup.add(new AnalyzeDependencyAction());
    addChangeLibraryLevelAction(actionGroup, LibraryTablesRegistrar.PROJECT_LEVEL);
    addChangeLibraryLevelAction(actionGroup, LibraryTablesRegistrar.APPLICATION_LEVEL);
    addChangeLibraryLevelAction(actionGroup, LibraryTableImplUtil.MODULE_LEVEL);
    PopupHandler.installPopupHandler(myEntryTable, actionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  private void addChangeLibraryLevelAction(DefaultActionGroup actionGroup, String tableLevel) {
    final LibraryTablePresentation presentation = LibraryEditingUtil.getLibraryTablePresentation(getProject(), tableLevel);
    actionGroup.add(new ChangeLibraryLevelInClasspathAction(this, presentation.getDisplayName(true), tableLevel));
  }

  @Override
  @Nullable
  public OrderEntry getSelectedEntry() {
    if (myEntryTable.getSelectedRowCount() != 1) return null;
    return myModel.getItemAt(myEntryTable.getSelectedRow()).getEntry();
  }

  private void setFixedColumnWidth(final int columnIndex, final String textToMeasure) {
    final TableColumn column = myEntryTable.getTableHeader().getColumnModel().getColumn(columnIndex);
    column.setResizable(false);
    column.setMaxWidth(column.getPreferredWidth());
  }

  @Override
  public void navigate(boolean openLibraryEditor) {
    final OrderEntry entry = getSelectedEntry();
    final ProjectStructureConfigurable rootConfigurable = ProjectStructureConfigurable.getInstance(myState.getProject());
    if (entry instanceof ModuleOrderEntry){
      Module module = ((ModuleOrderEntry)entry).getModule();
      if (module != null) {
        rootConfigurable.select(module.getName(), null, true);
      }
    }
    else if (entry instanceof LibraryOrderEntry){
      if (!openLibraryEditor) {
        rootConfigurable.select((LibraryOrderEntry)entry, true);
      }
      else {
        myEditButton.actionPerformed(null);
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
    final boolean isAnalyzeShown = false;

    final ClasspathPanelAction removeAction = new ClasspathPanelAction(this) {
      @Override
      public void run() {
        removeSelectedItems(TableUtil.removeSelectedItems(myEntryTable));
      }
    };

    final AnActionButton analyzeButton = new AnActionButton(ProjectBundle.message("classpath.panel.analyze"), null, IconUtil.getAnalyzeIcon()) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        AnalyzeDependenciesDialog.show(getRootModel().getModule());
      }
    };

    //addButton.setShortcut(CustomShortcutSet.fromString("alt A", "INSERT"));
    //removeButton.setShortcut(CustomShortcutSet.fromString("alt DELETE"));
    //upButton.setShortcut(CustomShortcutSet.fromString("alt UP"));
    //downButton.setShortcut(CustomShortcutSet.fromString("alt DOWN"));

    // we need to register our listener before ToolbarDecorator registers its own. Otherwise
    myEntryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        updateButtons();
      }
    });

    final ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myEntryTable);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        initPopupActions();
        final JBPopup popup = JBPopupFactory.getInstance().createListPopup(
          new BaseListPopupStep<AddItemPopupAction<?>>(null, myPopupActions) {
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
            public PopupStep onChosen(final AddItemPopupAction<?> selectedValue, final boolean finalChoice) {
              if (selectedValue.hasSubStep()) {
                return selectedValue.createSubStep();
              }
              return doFinalStep(new Runnable() {
                @Override
                public void run() {
                  selectedValue.execute();
                }
              });
            }

            @Override
            @NotNull
            public String getTextFor(AddItemPopupAction<?> value) {
              return "&" + value.getIndex() + "  " + value.getTitle();
            }
          });
        popup.show(button.getPreferredPopupPoint());
      }
    })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeAction.actionPerformed(null);
        }
      })
      .setMoveUpAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveSelectedRows(-1);
        }
      })
      .setMoveDownAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          moveSelectedRows(+1);
        }
      })
      .addExtraAction(myEditButton);
    if (isAnalyzeShown) {
      decorator.addExtraAction(analyzeButton);
    }

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
    dialog.show();
    myEntryTable.repaint();
    ModuleStructureConfigurable.getInstance(myState.getProject()).getTree().repaint();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    updateButtons();
  }

  private void updateButtons() {
    final int[] selectedRows = myEntryTable.getSelectedRows();
    boolean removeButtonEnabled = true;
    int minRow = myEntryTable.getRowCount() + 1;
    int maxRow = -1;
    for (final int selectedRow : selectedRows) {
      minRow = Math.min(minRow, selectedRow);
      maxRow = Math.max(maxRow, selectedRow);
      final ClasspathTableItem<?> item = myModel.getItemAt(selectedRow);
      if (!item.isRemovable()) {
        removeButtonEnabled = false;
      }
    }
    if (myRemoveButton != null) {
      myRemoveButton.setEnabled(removeButtonEnabled && selectedRows.length > 0);
    }
    ClasspathTableItem<?> selectedItem = selectedRows.length == 1 ? myModel.getItemAt(selectedRows[0]) : null;
    myEditButton.setEnabled(selectedItem != null && selectedItem.isEditable());
  }

  private void removeSelectedItems(final List removedRows) {
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
    final StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(myState.getProject()).getContext();
    context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, getRootModel().getModule()));
  }

  @Override
  @NotNull
  public LibraryTableModifiableModelProvider getModifiableModelProvider(@NotNull String tableLevel) {
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
      myEntryTable.requestFocus();
    }
  }

  @Override
  public void addItems(List<ClasspathTableItem<?>> toAdd) {
    for (ClasspathTableItem<?> item : toAdd) {
      myModel.addItem(item);
    }
    myModel.fireTableDataChanged();
    final ListSelectionModel selectionModel = myEntryTable.getSelectionModel();
    selectionModel.setSelectionInterval(myModel.getRowCount() - toAdd.size(), myModel.getRowCount() - 1);
    TableUtil.scrollSelectionToVisible(myEntryTable);

    final StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(myState.getProject()).getContext();
    context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, getRootModel().getModule()));
  }

  @Override
  public ModifiableRootModel getRootModel() {
    return myState.getRootModel();
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
      final List<AddItemPopupAction<?>> actions = new ArrayList<AddItemPopupAction<?>>();
      final StructureConfigurableContext context = getStructureConfigurableContext();
      actions.add(new AddNewModuleLibraryAction(this, actionIndex++, context));
      actions.add(new AddLibraryDependencyAction(this, actionIndex++, ProjectBundle.message("classpath.add.library.action"), context));
      actions.add(new AddModuleDependencyAction(this, actionIndex, context)
      );

      myPopupActions = actions;
    }
  }

  private StructureConfigurableContext getStructureConfigurableContext() {
    return ProjectStructureConfigurable.getInstance(myState.getProject()).getContext();
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
    if (increment == 0) {
      return;
    }
    if (myEntryTable.isEditing()){
      myEntryTable.getCellEditor().stopCellEditing();
    }
    final ListSelectionModel selectionModel = myEntryTable.getSelectionModel();
    for(int row = increment < 0? 0 : myModel.getRowCount() - 1; increment < 0? row < myModel.getRowCount() : row >= 0; row +=
      increment < 0? +1 : -1){
      if (selectionModel.isSelectedIndex(row)) {
        final int newRow = moveRow(row, increment);
        selectionModel.removeSelectionInterval(row, row);
        selectionModel.addSelectionInterval(newRow, newRow);
      }
    }
    myModel.fireTableRowsUpdated(0, myModel.getRowCount() - 1);
    Rectangle cellRect = myEntryTable.getCellRect(selectionModel.getMinSelectionIndex(), 0, true);
    if (cellRect != null) {
      myEntryTable.scrollRectToVisible(cellRect);
    }
    myEntryTable.repaint();
    myListeners.getMulticaster().entryMoved();
  }

  public void selectOrderEntry(@NotNull OrderEntry entry) {
    for (int row = 0; row < myModel.getRowCount(); row++) {
      final OrderEntry orderEntry = myModel.getItemAt(row).getEntry();
      if (orderEntry != null && entry.getPresentableName().equals(orderEntry.getPresentableName())) {
        myEntryTable.getSelectionModel().setSelectionInterval(row, row);
        TableUtil.scrollSelectionToVisible(myEntryTable);
      }
    }
    IdeFocusManager.getInstance(myState.getProject()).requestFocus(myEntryTable, true);
  }

  private int moveRow(final int row, final int increment) {
    int newIndex = Math.abs(row + increment) % myModel.getRowCount();
    final ClasspathTableItem<?> item = myModel.removeDataRow(row);
    myModel.addItemAt(item, newIndex);
    return newIndex;
  }

  public void stopEditing() {
    TableUtil.stopEditing(myEntryTable);
  }

  public List<OrderEntry> getEntries() {
    final int count = myModel.getRowCount();
    final List<OrderEntry> entries = new ArrayList<OrderEntry>(count);
    for (int row = 0; row < count; row++) {
      final OrderEntry entry = myModel.getItemAt(row).getEntry();
      if (entry != null) {
        entries.add(entry);
      }
    }
    return entries;
  }

  private int myInsideChange = 0;
  public void initFromModel() {
    if (myInsideChange == 0) {
      forceInitFromModel();
    }
  }

  public void forceInitFromModel() {
    final int[] selection = myEntryTable.getSelectedRows();
    myModel.clear();
    myModel.init();
    myModel.fireTableDataChanged();
    TableUtil.selectRows(myEntryTable, selection);
  }

  private static CellAppearanceEx getCellAppearance(final ClasspathTableItem<?> item,
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
    private StructureConfigurableContext myContext;

    public TableItemRenderer(StructureConfigurableContext context) {
      myContext = context;
    }

    @Override
    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setPaintFocusBorder(false);
      setFocusBorderAroundIcon(true);
      setBorder(NO_FOCUS_BORDER);
      if (value instanceof ClasspathTableItem<?>) {
        final ClasspathTableItem<?> tableItem = (ClasspathTableItem<?>)value;
        getCellAppearance(tableItem, myContext, selected).customize(this);
        setToolTipText(tableItem.getTooltipText());
      }
    }
  }

  private static class ExportFlagRenderer implements TableCellRenderer {
    private final TableCellRenderer myDelegate;
    private final JPanel myBlankPanel;

    public ExportFlagRenderer(TableCellRenderer delegate) {
      myDelegate = delegate;
      myBlankPanel = new JPanel();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (!table.isCellEditable(row, column)) {
        myBlankPanel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return myBlankPanel;
      }
      return myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  }

  private class MyFindUsagesAction extends FindUsagesInProjectStructureActionBase {
    private MyFindUsagesAction() {
      super(myEntryTable, myState.getProject());
    }

    @Override
    protected boolean isEnabled() {
      return getSelectedElement() != null;
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
  
  private class AnalyzeDependencyAction extends AnAction {
    private AnalyzeDependencyAction() {
      super("Analyze This Dependency");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final OrderEntry selectedEntry = getSelectedEntry();
      GlobalSearchScope targetScope;
      if (selectedEntry instanceof ModuleOrderEntry) {
        final Module module = ((ModuleOrderEntry)selectedEntry).getModule();
        LOG.assertTrue(module != null);
        targetScope = GlobalSearchScope.moduleScope(module);
      }
      else {
        Library library = ((LibraryOrderEntry)selectedEntry).getLibrary();
        LOG.assertTrue(library != null);
        targetScope = new LibraryScope(getProject(), library);
      }
      new AnalyzeDependenciesOnSpecifiedTargetHandler(getProject(), new AnalysisScope(myState.getRootModel().getModule()),
                                                      targetScope) {
        @Override
        protected boolean canStartInBackground() {
          return false;
        }

        @Override
        protected boolean shouldShowDependenciesPanel(List<DependenciesBuilder> builders) {
          for (DependenciesBuilder builder : builders) {
            for (Set<PsiFile> files : builder.getDependencies().values()) {
              if (!files.isEmpty()) {
                Messages.showInfoMessage(myEntryTable,
                                         "Dependencies were successfully collected in \"" +
                                         ToolWindowId.DEPENDENCIES + "\" toolwindow",
                                         FindBundle.message("find.pointcut.applications.not.found.title"));
                return true;
              }
            }
          }
          if (Messages.showOkCancelDialog(myEntryTable,
                                          "No code dependencies were found. Would you like to remove the dependency?",
                                          CommonBundle.getWarningTitle(), Messages.getWarningIcon()) == Messages.OK) {
            removeSelectedItems(TableUtil.removeSelectedItems(myEntryTable));
          }
          return false;
        }
      }.analyze();
    }

    @Override
    public void update(AnActionEvent e) {
      final OrderEntry entry = getSelectedEntry();
      e.getPresentation().setVisible(entry instanceof ModuleOrderEntry && ((ModuleOrderEntry)entry).getModule() != null
                                   || entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).getLibrary() != null);
    }
  }
}
