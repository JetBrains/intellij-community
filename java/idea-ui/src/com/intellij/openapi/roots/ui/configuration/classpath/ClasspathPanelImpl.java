/*
 * Copyright 2004-2005 Alexey Efimov
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

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.dependencyAnalysis.AnalyzeDependenciesDialog;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.EditExistingLibraryDialog;
import com.intellij.openapi.roots.ui.configuration.projectRoot.FindUsagesInProjectStructureActionBase;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ModuleProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.SdkProjectStructureElement;
import com.intellij.openapi.roots.ui.util.CellAppearance;
import com.intellij.openapi.roots.ui.util.OrderEntryCellAppearanceUtils;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ClasspathPanelImpl extends JPanel implements ClasspathPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.classpath.ClasspathPanel");

  private final Table myEntryTable;
  private final ClasspathTableModel myModel;
  private final EventDispatcher<OrderPanelListener> myListeners = EventDispatcher.create(OrderPanelListener.class);
  private List<AddItemPopupAction<?>> myPopupActions = null;
  private JButton myEditButton;
  private final ModuleConfigurationState myState;

  public ClasspathPanelImpl(ModuleConfigurationState state) {
    super(new BorderLayout());

    myState = state;
    myModel = new ClasspathTableModel(state);
    myEntryTable = new Table(myModel);
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setShowHorizontalLines(false);
    myEntryTable.setShowVerticalLines(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));

    myEntryTable.setDefaultRenderer(ClasspathTableItem.class, new TableItemRenderer());
    myEntryTable.setDefaultRenderer(Boolean.class, new ExportFlagRenderer(myEntryTable.getDefaultRenderer(Boolean.class)));

    JComboBox scopeEditor = new JComboBox(new EnumComboBoxModel<DependencyScope>(DependencyScope.class));
    myEntryTable.setDefaultEditor(DependencyScope.class, new DefaultCellEditor(scopeEditor));
    myEntryTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    new SpeedSearchBase<Table>(myEntryTable) {
      public int getSelectedIndex() {
        return myEntryTable.getSelectedRow();
      }

      @Override
      protected int convertIndexToModel(int viewIndex) {
        return myEntryTable.convertRowIndexToModel(viewIndex);
      }

      public Object[] getAllElements() {
        final int count = myModel.getRowCount();
        Object[] elements = new Object[count];
        for (int idx = 0; idx < count; idx++) {
          elements[idx] = myModel.getItemAt(idx);
        }
        return elements;
      }

      public String getElementText(Object element) {
        return getCellAppearance((ClasspathTableItem)element, false).getText();
      }

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
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = myEntryTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (final int selectedRow : selectedRows) {
            final ClasspathTableItem item = myModel.getItemAt(myEntryTable.convertRowIndexToModel(selectedRow));
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

    add(ScrollPaneFactory.createScrollPane(myEntryTable), BorderLayout.CENTER);
    add(createButtonsBlock(), BorderLayout.EAST);

    if (myEntryTable.getRowCount() > 0) {
      myEntryTable.getSelectionModel().setSelectionInterval(0,0);
    }

    myEntryTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2){
          navigate(true);
        }
      }
    });

    DefaultActionGroup actionGroup = new DefaultActionGroup();
    final AnAction navigateAction = new AnAction(ProjectBundle.message("classpath.panel.navigate.action.text")) {
      public void actionPerformed(AnActionEvent e) {
        navigate(false);
      }

      public void update(AnActionEvent e) {
        final Presentation presentation = e.getPresentation();
        presentation.setEnabled(false);
        if (myEntryTable.getSelectedRowCount() != 1) return;
        final OrderEntry entry = myModel.getItemAt(myEntryTable.getSelectedRow()).getEntry();
        if (entry != null && entry.isValid()){
          if (!(entry instanceof ModuleSourceOrderEntry)){
            presentation.setEnabled(true);
          }
        }
      }
    };
    navigateAction.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet(), myEntryTable);
    actionGroup.add(navigateAction);
    actionGroup.add(new MyFindUsagesAction());
    PopupHandler.installPopupHandler(myEntryTable, actionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
  }

  private void setFixedColumnWidth(final int columnIndex, final String textToMeasure) {
    final FontMetrics fontMetrics = myEntryTable.getFontMetrics(myEntryTable.getFont());
    final int width = fontMetrics.stringWidth(" " + textToMeasure + " ") + 4;
    final TableColumn checkboxColumn = myEntryTable.getTableHeader().getColumnModel().getColumn(columnIndex);
    checkboxColumn.setWidth(width);
    checkboxColumn.setPreferredWidth(width);
    checkboxColumn.setMaxWidth(width);
    checkboxColumn.setMinWidth(width);
  }

  private void navigate(boolean openLibraryEditor) {
    final int selectedRow = myEntryTable.getSelectedRow();
    final OrderEntry entry = myModel.getItemAt(selectedRow).getEntry();
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
        myEditButton.doClick();
      }
    }
    else if (entry instanceof JdkOrderEntry) {
      Sdk jdk = ((JdkOrderEntry)entry).getJdk();
      if (jdk != null) {
        rootConfigurable.select(jdk, true);
      }
    }
  }


  private JComponent createButtonsBlock() {
    final boolean isAnalyzeShown = ((ApplicationEx)ApplicationManager.getApplication()).isInternal();

    final JButton addButton = new JButton(ProjectBundle.message("button.add"));
    final JButton removeButton = new JButton(ProjectBundle.message("button.remove"));
    myEditButton = new JButton(ProjectBundle.message("button.edit"));
    final JButton upButton = new JButton(ProjectBundle.message("button.move.up"));
    final JButton downButton = new JButton(ProjectBundle.message("button.move.down"));
    final JButton analyzeButton = isAnalyzeShown ? new JButton(ProjectBundle.message("classpath.panel.analyze")) : null;

    final JPanel panel = new JPanel(new GridBagLayout());
    panel.add(addButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    panel.add(removeButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    panel.add(myEditButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    panel.add(upButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    panel.add(downButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, isAnalyzeShown ? 0.0 : 0.1, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    if( isAnalyzeShown) {
      panel.add(analyzeButton, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 0, 0), 0, 0));
    }

    myEntryTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
          return;
        }
        final int[] selectedRows = myEntryTable.getSelectedRows();
        boolean removeButtonEnabled = true;
        int minRow = myEntryTable.getRowCount() + 1;
        int maxRow = -1;
        for (final int selectedRow : selectedRows) {
          minRow = Math.min(minRow, selectedRow);
          maxRow = Math.max(maxRow, selectedRow);
          final ClasspathTableItem item = myModel.getItemAt(selectedRow);
          if (!item.isRemovable()) {
            removeButtonEnabled = false;
          }
        }
        upButton.setEnabled(minRow > 0 && minRow < myEntryTable.getRowCount());
        downButton.setEnabled(maxRow >= 0 && maxRow < myEntryTable.getRowCount() - 1);
        removeButton.setEnabled(removeButtonEnabled);
        ClasspathTableItem selectedItem = selectedRows.length == 1 ? myModel.getItemAt(selectedRows[0]) : null;
        myEditButton.setEnabled(selectedItem != null && selectedItem.isEditable());
      }
    });

    upButton.addActionListener(new ClasspathPanelAction(this) {
      @Override
      public void run() {
        moveSelectedRows(-1);
      }
    });
    downButton.addActionListener(new ClasspathPanelAction(this) {
      @Override
      public void run() {
        moveSelectedRows(+1);
      }
    });

    if(isAnalyzeShown) {
      analyzeButton.addActionListener(new ClasspathPanelAction(this) {
        @Override
        public void run() {
          AnalyzeDependenciesDialog.show(getRootModel().getModule());
        }
      });
    }

    addKeyboardShortcut(myEntryTable, removeButton, KeyEvent.VK_DELETE, 0);
    addKeyboardShortcut(myEntryTable, addButton, KeyEvent.VK_INSERT, 0);
    addKeyboardShortcut(myEntryTable, upButton, KeyEvent.VK_UP, KeyEvent.CTRL_DOWN_MASK);
    addKeyboardShortcut(myEntryTable, downButton, KeyEvent.VK_DOWN, KeyEvent.CTRL_DOWN_MASK);

    addButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        initPopupActions();
        final JBPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<AddItemPopupAction<?>>(null, myPopupActions) {
          @Override
          public Icon getIconFor(AddItemPopupAction<?> aValue) {
            return aValue.getIcon();
          }

          public boolean isMnemonicsNavigationEnabled() {
            return true;
          }
          public PopupStep onChosen(final AddItemPopupAction<?> selectedValue, final boolean finalChoice) {
            return doFinalStep(new Runnable() {
              public void run() {
                selectedValue.execute();
              }
            });
          }
          @NotNull
          public String getTextFor(AddItemPopupAction<?> value) {
            return "&" + value.getIndex() + "  " + value.getTitle();
          }
        });
        popup.showUnderneathOf(addButton);
      }
    });

    removeButton.addActionListener(new ClasspathPanelAction(this) {
      @Override
      public void run() {
        final List removedRows = TableUtil.removeSelectedItems(myEntryTable);
        if (removedRows.isEmpty()) {
          return;
        }
        for (final Object removedRow : removedRows) {
          final ClasspathTableItem item = (ClasspathTableItem)((Object[])removedRow)[ClasspathTableModel.ITEM_COLUMN];
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
    });

    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        final int row = myEntryTable.getSelectedRow();
        final ClasspathTableItem item = myModel.getItemAt(row);
        final OrderEntry entry = item.getEntry();
        if (!(entry instanceof LibraryOrderEntry)) return;

        final Library library = ((LibraryOrderEntry)entry).getLibrary();
        if (library == null) {
          return;
        }
        final LibraryTableModifiableModelProvider provider;
        final LibraryTable table = library.getTable();
        if (table == null) {
          final LibraryTable moduleLibraryTable = getRootModel().getModuleLibraryTable();
          provider = new LibraryTableModifiableModelProvider() {
            public LibraryTable.ModifiableModel getModifiableModel() {
              return moduleLibraryTable.getModifiableModel();
            }

          };
        }
        else {
          provider = ProjectStructureConfigurable.getInstance(myState.getProject()).getContext().createModifiableModelProvider(table.getTableLevel());
        }
        EditExistingLibraryDialog dialog = EditExistingLibraryDialog.createDialog(ClasspathPanelImpl.this, provider, library, myState.getProject());
        dialog.addFileChooserContext(LangDataKeys.MODULE_CONTEXT, getRootModel().getModule());
        dialog.show();
        myEntryTable.repaint();
        ModuleStructureConfigurable.getInstance(myState.getProject()).getTree().repaint();
      }
    });
    return panel;
  }

  private static void addKeyboardShortcut(final JComponent target, final JButton button, final int keyEvent, final int modifiers) {
    target.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          if (button.isEnabled()) {
            button.doClick();
          }
        }
      },
      KeyStroke.getKeyStroke(keyEvent, modifiers),
      WHEN_FOCUSED
    );
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
  public void addItems(List<ClasspathTableItem> toAdd) {
    for (ClasspathTableItem item : toAdd) {
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
  public JComponent getComponent() {
    return this;
  }

  public void rootsChanged() {
    forceInitFromModel();
  }

  private void initPopupActions() {
    if (myPopupActions == null) {
      final StructureConfigurableContext context = ProjectStructureConfigurable.getInstance(myState.getProject()).getContext();
      int actionIndex = 1;
      final List<AddItemPopupAction<?>> actions = new ArrayList<AddItemPopupAction<?>>();
      actions.add(new AddSingleEntryModuleLibraryAction(this, actionIndex++));
      actions.add(new AddLibraryAction(this, actionIndex++, ProjectBundle.message("classpath.add.library.action"), context));
      actions.add(new AddItemPopupAction<Module>(this, actionIndex, ProjectBundle.message("classpath.add.module.dependency.action"),
                                                 StdModuleTypes.JAVA.getNodeIcon(false)) {
          protected ClasspathTableItem createTableItem(final Module item) {
            return ClasspathTableItem.createItem(getRootModel().addModuleOrderEntry(item));
          }
          protected ClasspathElementChooser<Module> createChooser() {
            final List<Module> chooseItems = getDependencyModules();
            if (chooseItems.isEmpty()) {
              Messages.showMessageDialog(ClasspathPanelImpl.this, ProjectBundle.message("message.no.module.dependency.candidates"), getTitle(), Messages.getInformationIcon());
              return null;
            }
            return new ModuleChooser(chooseItems, ProjectBundle.message("classpath.chooser.title.add.module.dependency"));
          }
        }
      );

      myPopupActions = actions;
    }
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
      if (entry.getPresentableName().equals(myModel.getItemAt(row).getEntry().getPresentableName())) {
        myEntryTable.getSelectionModel().setSelectionInterval(row, row);
        TableUtil.scrollSelectionToVisible(myEntryTable);
      }
    }
    IdeFocusManager.getInstance(myState.getProject()).requestFocus(myEntryTable, true);
  }

  private int moveRow(final int row, final int increment) {
    int newIndex = Math.abs(row + increment) % myModel.getRowCount();
    final ClasspathTableItem item = myModel.removeDataRow(row);
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

  private List<Module> getDependencyModules() {
    final int rowCount = myModel.getRowCount();
    final Set<String> filtered = new HashSet<String>(rowCount);
    for (int row = 0; row < rowCount; row++) {
      final OrderEntry entry = myModel.getItemAt(row).getEntry();
      if (entry instanceof ModuleOrderEntry) {
        filtered.add(((ModuleOrderEntry)entry).getModuleName());
      }
    }
    final ModulesProvider modulesProvider = myState.getModulesProvider();
    final Module self = modulesProvider.getModule(getRootModel().getModule().getName());
    filtered.add(self.getName());

    final Module[] modules = modulesProvider.getModules();
    final List<Module> elements = new ArrayList<Module>(modules.length);
    for (final Module module : modules) {
      if (!filtered.contains(module.getName())) {
        elements.add(module);
      }
    }
    return elements;
  }


  private static CellAppearance getCellAppearance(final ClasspathTableItem item, final boolean selected) {
    if (item instanceof InvalidJdkItem) {
      return OrderEntryCellAppearanceUtils.forJdk(null, false, selected);
    }
    else {
      return OrderEntryCellAppearanceUtils.forOrderEntry(item.getEntry(), selected);
    }
  }

  private static class TableItemRenderer extends ColoredTableCellRenderer {
    private final Border NO_FOCUS_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
      setPaintFocusBorder(false);
      setFocusBorderAroundIcon(true);
      setBorder(NO_FOCUS_BORDER);
      if (value instanceof ClasspathTableItem) {
        getCellAppearance((ClasspathTableItem)value, selected).customize(this);
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

    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (!table.isCellEditable(row, column)) {
        myBlankPanel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return myBlankPanel;
      }
      return myDelegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  }

  private class ModuleChooser extends ChooseModulesDialog implements ClasspathElementChooser<Module> {
    public ModuleChooser(final List<Module> items, final String title) {
      super(ClasspathPanelImpl.this, items, title);
    }

    public void doChoose() {
      show();
    }

    public void dispose() {
      super.dispose();
    }
  }

  private class MyFindUsagesAction extends FindUsagesInProjectStructureActionBase {
    private MyFindUsagesAction() {
      super(myEntryTable, myState.getProject());
    }

    protected boolean isEnabled() {
      return getSelectedElement() != null;
    }

    protected ProjectStructureElement getSelectedElement() {
      int row = myEntryTable.getSelectedRow();
      if (0 <= row && row < myModel.getRowCount()) {
        ClasspathTableItem item = myModel.getItemAt(row);
        final OrderEntry entry = item.getEntry();
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
      }
      return null;
    }

    protected RelativePoint getPointToShowResults() {
      Rectangle rect = myEntryTable.getCellRect(myEntryTable.getSelectedRow(), 1, false);
      Point location = rect.getLocation();
      location.y += rect.height;
      return new RelativePoint(myEntryTable, location);
    }
  }

}
