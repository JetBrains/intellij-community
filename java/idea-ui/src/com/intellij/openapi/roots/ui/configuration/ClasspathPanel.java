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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.ui.configuration.dependencyAnalysis.AnalyzeDependenciesDialog;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ChooseModulesDialog;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryFileChooser;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor;
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
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Icons;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class ClasspathPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.ui.configuration.ClasspathPanel");

  private final Table myEntryTable;
  private final MyTableModel myModel;
  private final EventDispatcher<OrderPanelListener> myListeners = EventDispatcher.create(OrderPanelListener.class);
  private PopupAction[] myPopupActions = null;
  private Icon[] myIcons = null;
  private JButton myEditButton;
  private final ModuleConfigurationState myState;

  protected ClasspathPanel(ModuleConfigurationState state) {
    super(new BorderLayout());

    myState = state;
    myModel = new MyTableModel(state);
    myEntryTable = new Table(myModel);
    myEntryTable.setShowGrid(false);
    myEntryTable.setDragEnabled(false);
    myEntryTable.setShowHorizontalLines(false);
    myEntryTable.setShowVerticalLines(false);
    myEntryTable.setIntercellSpacing(new Dimension(0, 0));

    myEntryTable.setDefaultRenderer(TableItem.class, new TableItemRenderer());
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
        return getCellAppearance((TableItem)element, false).getText();
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


    setFixedColumnWidth(MyTableModel.EXPORT_COLUMN, MyTableModel.EXPORT_COLUMN_NAME);
    setFixedColumnWidth(MyTableModel.SCOPE_COLUMN, DependencyScope.COMPILE.toString() + "     ");  // leave space for combobox border

    myEntryTable.registerKeyboardAction(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          final int[] selectedRows = myEntryTable.getSelectedRows();
          boolean currentlyMarked = true;
          for (final int selectedRow : selectedRows) {
            final TableItem item = myModel.getItemAt(myEntryTable.convertRowIndexToModel(selectedRow));
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
          final TableItem item = myModel.getItemAt(selectedRow);
          if (!item.isRemovable()) {
            removeButtonEnabled = false;
          }
        }
        upButton.setEnabled(minRow > 0 && minRow < myEntryTable.getRowCount());
        downButton.setEnabled(maxRow >= 0 && maxRow < myEntryTable.getRowCount() - 1);
        removeButton.setEnabled(removeButtonEnabled);
        TableItem selectedItem = selectedRows.length == 1 ? myModel.getItemAt(selectedRows[0]) : null;
        myEditButton.setEnabled(selectedItem instanceof LibItem && selectedItem.isEditable());
      }
    });

    upButton.addActionListener(new ButtonAction() {
      protected void executeImpl() {
        moveSelectedRows(-1);
      }
    });
    downButton.addActionListener(new ButtonAction() {
      protected void executeImpl() {
        moveSelectedRows(+1);
      }
    });
    if( isAnalyzeShown) {
      analyzeButton.addActionListener(new ButtonAction() {
        @Override
        protected void executeImpl() {
          AnalyzeDependenciesDialog.show(getRootModel().getModule());
        }
      });
    }

    addKeyboardShortcut(myEntryTable, removeButton, KeyEvent.VK_DELETE, 0);
    addKeyboardShortcut(myEntryTable, addButton, KeyEvent.VK_INSERT, 0);
    addKeyboardShortcut(myEntryTable, upButton, KeyEvent.VK_UP, KeyEvent.CTRL_DOWN_MASK);
    addKeyboardShortcut(myEntryTable, downButton, KeyEvent.VK_DOWN, KeyEvent.CTRL_DOWN_MASK);

    addButton.addActionListener(new ButtonAction() {
      protected void executeImpl() {
        initPopupActions();
        final JBPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<PopupAction>(null, myPopupActions, myIcons) {
          public boolean isMnemonicsNavigationEnabled() {
            return true;
          }
          public boolean isSelectable(PopupAction value) {
            return value.isSelectable();
          }
          public PopupStep onChosen(final PopupAction selectedValue, final boolean finalChoice) {
            return doFinalStep(new Runnable() {
              public void run() {
                selectedValue.execute();
              }
            });
          }
          @NotNull
          public String getTextFor(PopupAction value) {
            return "&" + value.getIndex() + "  " + value.getTitle();
          }
        });
        popup.showUnderneathOf(addButton);
      }
    });

    removeButton.addActionListener(new ButtonAction() {
      protected void executeImpl() {
        final List removedRows = TableUtil.removeSelectedItems(myEntryTable);
        if (removedRows.isEmpty()) {
          return;
        }
        for (final Object removedRow : removedRows) {
          final TableItem item = (TableItem)((Object[])removedRow)[MyTableModel.ITEM_COLUMN];
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
        final TableItem item = myModel.getItemAt(row);
        if (!(item instanceof LibItem)) {
          return;
        }
        final LibraryOrderEntry libraryOrderEntry = ((LibItem)item).getEntry();
        if (libraryOrderEntry == null) {
          return;
        }
        final Library library = libraryOrderEntry.getLibrary();
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

            public String getTableLevel() {
              return moduleLibraryTable.getTableLevel();
            }

            public LibraryTablePresentation getLibraryTablePresentation() {
              return moduleLibraryTable.getPresentation();
            }

            public boolean isLibraryTableEditable() {
              return false;
            }
          };
        }
        else {
          provider = ProjectStructureConfigurable.getInstance(myState.getProject()).getContext().createModifiableModelProvider(table.getTableLevel(), false);
        }
        final LibraryTableEditor editor = LibraryTableEditor.editLibrary(provider, library, myState.getProject());
        editor.addFileChooserContext(LangDataKeys.MODULE_CONTEXT, getRootModel().getModule());
        editor.openDialog(ClasspathPanel.this, Collections.singletonList(library), true);
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

  private ModifiableRootModel getRootModel() {
    return myState.getRootModel();
  }

  public void rootsChanged() {
    forceInitFromModel();
  }

  private abstract class ButtonAction implements ActionListener {
    public final void actionPerformed(ActionEvent e) {
      execute();
    }

    public final void execute() {
      try {
        disableModelUpdate();
        executeImpl();
      }
      finally {
        enableModelUpdate();
        myEntryTable.requestFocus();
      }
    }

    protected abstract void executeImpl();
  }

  private class PopupAction extends ButtonAction{
    private final String myTitle;
    private final Icon myIcon;
    private final int myIndex;

    protected PopupAction(String title, Icon icon, final int index) {
      myTitle = title;
      myIcon = icon;
      myIndex = index;
    }

    public String getTitle() {
      return myTitle;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public int getIndex() {
      return myIndex;
    }

    public boolean isSelectable() {
      return true;
    }

    protected void executeImpl() {
    }
  }

  private abstract class ChooseAndAddAction<ItemType> extends PopupAction{
    public ChooseAndAddAction(int index, String title, Icon icon) {
      super(title, icon, index);
    }

    protected final void executeImpl() {
      final ChooserDialog<ItemType> dialog = createChooserDialog();
      if (dialog == null) {
        return;
      }
      try {
        dialog.doChoose();
        if (!dialog.isOK()) {
          return;
        }
        final List<ItemType> chosen = dialog.getChosenElements();
        if (chosen.isEmpty()) {
          return;
        }
        final ModuleStructureConfigurable rootConfigurable = ModuleStructureConfigurable.getInstance(myState.getProject());
        //int insertionIndex = myEntryTable.getSelectedRow();
        for (ItemType item : chosen) {
          //myModel.addItemAt(createTableItem(item), insertionIndex++);
          final TableItem tableItem = createTableItem(item);
          if ( tableItem != null ) {
            myModel.addItem(tableItem);
          }
        }
        myModel.fireTableDataChanged();
        final ListSelectionModel selectionModel = myEntryTable.getSelectionModel();
        //selectionModel.setSelectionInterval(insertionIndex - chosen.size(), insertionIndex - 1);
        selectionModel.setSelectionInterval(myModel.getRowCount() - chosen.size(), myModel.getRowCount() - 1);
        TableUtil.scrollSelectionToVisible(myEntryTable);

        final StructureConfigurableContext context = rootConfigurable.getContext();
        context.getDaemonAnalyzer().queueUpdate(new ModuleProjectStructureElement(context, getRootModel().getModule()));
      }
      finally {
        if (dialog instanceof ChooseNamedLibraryAction.MyChooserDialog) {
          Disposer.dispose(dialog);
        }
      }
    }

    @Nullable
    protected abstract TableItem createTableItem(final ItemType item);

    @Nullable
    protected abstract ChooserDialog<ItemType> createChooserDialog();
  }

  private void initPopupActions() {
    if (myPopupActions == null) {
      final StructureConfigurableContext context = ProjectStructureConfigurable.getInstance(myState.getProject()).getContext();
      int actionIndex = 1;
      final List<PopupAction> actions = new ArrayList<PopupAction>(Arrays.<PopupAction>asList(
        new ChooseAndAddAction<Library>(actionIndex++, ProjectBundle.message("classpath.add.simple.module.library.action"), Icons.JAR_ICON) {
          protected TableItem createTableItem(final Library item) {
            final OrderEntry[] entries = getRootModel().getOrderEntries();
            for (OrderEntry entry : entries) {
              if (entry instanceof LibraryOrderEntry) {
                final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
                if (item.equals(libraryOrderEntry.getLibrary())) {
                  return new LibItem(libraryOrderEntry);
                }
              }
            }
            LOG.error("Unknown library " + item);
            return null;
          }

          protected ChooserDialog<Library> createChooserDialog() {
            return new ChooseModuleLibrariesDialog(ClasspathPanel.this, getRootModel().getModuleLibraryTable(), null);
          }
        },
        new ChooseAndAddAction<Library>(actionIndex++, ProjectBundle.message("classpath.add.module.library.action"), Icons.JAR_ICON) {
          protected TableItem createTableItem(final Library item) {
            final OrderEntry[] entries = getRootModel().getOrderEntries();
            for (OrderEntry entry : entries) {
              if (entry instanceof LibraryOrderEntry) {
                final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
                if (item.equals(libraryOrderEntry.getLibrary())) {
                  return new LibItem(libraryOrderEntry);
                }
              }
            }
            LOG.error("Unknown library " + item);
            return null;
          }

          protected ChooserDialog<Library> createChooserDialog() {
            return new CreateModuleLibraryDialog(ClasspathPanel.this, getRootModel().getModuleLibraryTable());
          }
        },
        new ChooseNamedLibraryAction(actionIndex++, ProjectBundle.message("classpath.add.project.library.action"), context.getProjectLibrariesProvider(true)),
        new ChooseNamedLibraryAction(actionIndex++, ProjectBundle.message("classpath.add.global.library.action"), context.getGlobalLibrariesProvider(true))));

      for (final LibraryTableModifiableModelProvider provider : context.getCustomLibrariesProviders(true)) {
        actions.add(new ChooseNamedLibraryAction(actionIndex++, provider.getLibraryTablePresentation().getDisplayName(false) + "...", provider));
      }

      actions.add(new ChooseAndAddAction<Module>(actionIndex, ProjectBundle.message("classpath.add.module.dependency.action"),
                                                 StdModuleTypes.JAVA.getNodeIcon(false)) {
          protected TableItem createTableItem(final Module item) {
            return new ModuleItem(getRootModel().addModuleOrderEntry(item));
          }
          protected ChooserDialog<Module> createChooserDialog() {
            final List<Module> chooseItems = getDependencyModules();
            if (chooseItems.isEmpty()) {
              Messages.showMessageDialog(ClasspathPanel.this, ProjectBundle.message("message.no.module.dependency.candidates"), getTitle(), Messages.getInformationIcon());
              return null;
            }
            return new ChooseModulesToAddDialog(chooseItems, ProjectBundle.message("classpath.chooser.title.add.module.dependency"));
          }
        }
     );

      myPopupActions = actions.toArray(new PopupAction[actions.size()]);

      myIcons = new Icon[myPopupActions.length];
      for (int idx = 0; idx < myPopupActions.length; idx++) {
        myIcons[idx] = myPopupActions[idx].getIcon();
      }
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
    final TableItem item = myModel.removeDataRow(row);
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

  void forceInitFromModel() {
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


  private abstract static class TableItem<T extends OrderEntry> {
    @Nullable protected final T myEntry;

    protected TableItem(@Nullable T entry) {
      myEntry = entry;
    }

    public final boolean isExportable() {
      return myEntry instanceof ExportableOrderEntry;
    }

    public final boolean isExported() {
      return isExportable() && ((ExportableOrderEntry)getEntry()).isExported();
    }

    public final void setExported(boolean isExported) {
      if (isExportable()) {
        ((ExportableOrderEntry)getEntry()).setExported(isExported);
      }
    }

    @Nullable
    public final DependencyScope getScope() {
      return myEntry instanceof ExportableOrderEntry ? ((ExportableOrderEntry) myEntry).getScope() : null;
    }

    public final void setScope(DependencyScope scope) {
      if (myEntry instanceof ExportableOrderEntry) {
        ((ExportableOrderEntry) myEntry).setScope(scope);
      }
    }

    public final T getEntry() {
      return myEntry;
    }

    public abstract boolean isRemovable();
    public abstract boolean isEditable();
  }

  private static class LibItem extends TableItem<LibraryOrderEntry> {
    public LibItem(LibraryOrderEntry entry) {
      super(entry);
    }

    public boolean isRemovable() {
      return true;
    }

    public boolean isEditable() {
      final LibraryOrderEntry orderEntry = getEntry();
      return orderEntry != null && orderEntry.isValid();
    }
  }

  private static class ModuleItem extends TableItem<ModuleOrderEntry> {
    public ModuleItem(final ModuleOrderEntry entry) {
      super(entry);
    }

    public boolean isRemovable() {
      return true;
    }

    public boolean isEditable() {
      return true;
    }
  }

  private static class JdkItem extends TableItem<JdkOrderEntry> {
    public JdkItem(final JdkOrderEntry entry) {
      super(entry);
    }

    public boolean isRemovable() {
      return false;
    }

    public boolean isEditable() {
      return true;
    }
  }

  private static class SelfModuleItem extends TableItem<ModuleSourceOrderEntry> {
    public SelfModuleItem(final ModuleSourceOrderEntry entry) {
      super(entry);
    }

    public boolean isRemovable() {
      return false;
    }

    public boolean isEditable() {
      return false;
    }
  }

  private static class MyTableModel extends AbstractTableModel implements ItemRemovable {
    private static final String EXPORT_COLUMN_NAME = ProjectBundle.message("modules.order.export.export.column");
    private static final String SCOPE_COLUMN_NAME = ProjectBundle.message("modules.order.export.scope.column");
    public static final int EXPORT_COLUMN = 0;
    public static final int ITEM_COLUMN = 1;
    public static final int SCOPE_COLUMN = 2;
    private final List<TableItem> myItems = new ArrayList<TableItem>();
    private final ModuleConfigurationState myState;

    public MyTableModel(final ModuleConfigurationState state) {
      myState = state;
      init();
    }

    private ModifiableRootModel getModel() {
      return myState.getRootModel();
    }

    public void init() {
      final OrderEntry[] orderEntries = getModel().getOrderEntries();
      boolean hasJdkOrderEntry = false;
      for (final OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) {
          hasJdkOrderEntry = true;
        }
        addOrderEntry(orderEntry);
      }
      if (!hasJdkOrderEntry) {
        addItemAt(new JdkItem(null), 0);
      }
    }

    private void addOrderEntry(OrderEntry orderEntry) {
      if (orderEntry instanceof JdkOrderEntry) {
        addItem(new JdkItem((JdkOrderEntry)orderEntry));
      }
      else if (orderEntry instanceof LibraryOrderEntry) {
        addItem(new LibItem((LibraryOrderEntry)orderEntry));
      }
      else if (orderEntry instanceof ModuleOrderEntry) {
        addItem(new ModuleItem((ModuleOrderEntry)orderEntry));
      }
      else if (orderEntry instanceof ModuleSourceOrderEntry) {
        addItem(new SelfModuleItem((ModuleSourceOrderEntry)orderEntry));
      }
    }

    public TableItem getItemAt(int row) {
      return myItems.get(row);
    }

    public void addItem(TableItem item) {
      myItems.add(item);
    }

    public void addItemAt(TableItem item, int row) {
      myItems.add(row, item);
    }

    public TableItem removeDataRow(int row) {
      return myItems.remove(row);
    }

    public void removeRow(int row) {
      removeDataRow(row);
    }

    public void clear() {
      myItems.clear();
    }

    public int getRowCount() {
      return myItems.size();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      final TableItem item = myItems.get(rowIndex);
      if (columnIndex == EXPORT_COLUMN) {
        return item.isExported();
      }
      if (columnIndex == SCOPE_COLUMN) {
        return item.getScope();
      }
      if (columnIndex == ITEM_COLUMN) {
        return item;
      }
      LOG.error("Incorrect column index: " + columnIndex);
      return null;
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      final TableItem item = myItems.get(rowIndex);
      if (columnIndex == EXPORT_COLUMN) {
        item.setExported(((Boolean)aValue).booleanValue());
      }
      else if (columnIndex == SCOPE_COLUMN && aValue instanceof DependencyScope) {
        item.setScope((DependencyScope) aValue);
      }
    }

    public String getColumnName(int column) {
      if (column == EXPORT_COLUMN) {
        return EXPORT_COLUMN_NAME;
      }
      if (column == SCOPE_COLUMN) {
        return SCOPE_COLUMN_NAME;
      }
      return "";
    }

    public Class getColumnClass(int column) {
      if (column == EXPORT_COLUMN) {
        return Boolean.class;
      }
      if (column == SCOPE_COLUMN) {
        return DependencyScope.class;
      }
      if (column == ITEM_COLUMN) {
        return TableItem.class;
      }
      return super.getColumnClass(column);
    }

    public int getColumnCount() {
      return 3;
    }

    public boolean isCellEditable(int row, int column) {
      if (column == EXPORT_COLUMN || column == SCOPE_COLUMN) {
        final TableItem item = myItems.get(row);
        return item != null && item.isExportable();
      }
      return false;
    }
  }

  private static CellAppearance getCellAppearance(final TableItem item, final boolean selected) {
    if (item instanceof JdkItem && item.getEntry() == null) {
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
      if (value instanceof TableItem) {
        getCellAppearance((TableItem)value, selected).customize(this);
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

  private interface ChooserDialog<T> extends Disposable {
    List<T> getChosenElements();
    void doChoose();
    boolean isOK();
  }

  private class ChooseModulesToAddDialog extends ChooseModulesDialog implements ChooserDialog<Module>{
    public ChooseModulesToAddDialog(final List<Module> items, final String title) {
      super(ClasspathPanel.this, items, title);
    }

    public void doChoose() {
      show();
    }

    public void dispose() {
      super.dispose();
    }
  }
  private static class CreateModuleLibraryDialog implements ChooserDialog<Library> {
    private boolean myIsOk;
    private final ClasspathPanel myParent;
    private final LibraryTable myLibraryTable;
    private Library myChosenLibrary;

    public CreateModuleLibraryDialog(ClasspathPanel parent, final LibraryTable libraryTable) {
      myParent = parent;
      myLibraryTable = libraryTable;
    }

    public List<Library> getChosenElements() {
      return myChosenLibrary == null? Collections.<Library>emptyList() : Collections.singletonList(myChosenLibrary);
    }

    public void doChoose() {
      final LibraryTable.ModifiableModel libraryModifiableModel = myLibraryTable.getModifiableModel();
      final LibraryTableModifiableModelProvider provider = new LibraryTableModifiableModelProvider() {
        public LibraryTable.ModifiableModel getModifiableModel() {
          return libraryModifiableModel;
        }

        public String getTableLevel() {
          return myLibraryTable.getTableLevel();
        }

        public LibraryTablePresentation getLibraryTablePresentation() {
          return myLibraryTable.getPresentation();
        }

        public boolean isLibraryTableEditable() {
          return false;
        }
      };
      final Library library = myLibraryTable.createLibrary();
      final LibraryTableEditor editor = LibraryTableEditor.editLibrary(provider, library, myParent.myState.getProject());
      final Module contextModule = DataKeys.MODULE_CONTEXT.getData(DataManager.getInstance().getDataContext(myParent));
      editor.addFileChooserContext(LangDataKeys.MODULE_CONTEXT, contextModule);
      myIsOk = editor.openDialog(myParent, Collections.singletonList(library), true) != null;
      if (myIsOk) {
        myChosenLibrary = library;
      }
    }

    public boolean isOK() {
      return myIsOk;
    }

    public void dispose() {
    }
  }

  private static class ChooseModuleLibrariesDialog extends LibraryFileChooser implements ChooserDialog<Library> {
    private Pair<String, VirtualFile[]> myLastChosen;
    private final LibraryTable myLibraryTable;
    @Nullable private final VirtualFile myFileToSelect;

    public ChooseModuleLibrariesDialog(Component parent, final LibraryTable libraryTable, final VirtualFile fileToSelect) {
      super(createFileChooserDescriptor(parent), parent, false, null);
      myLibraryTable = libraryTable;
      myFileToSelect = fileToSelect;
    }

    private static FileChooserDescriptor createFileChooserDescriptor(Component parent) {
      final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, true, false, false, true);
      final Module contextModule = LangDataKeys.MODULE_CONTEXT.getData(DataManager.getInstance().getDataContext(parent));
      descriptor.putUserData(LangDataKeys.MODULE_CONTEXT, contextModule);
      return descriptor;
    }

    public List<Library> getChosenElements() {
      if (myLastChosen == null) {
        return Collections.emptyList();
      }
      final VirtualFile[] files = filterAlreadyAdded(myLastChosen.getSecond());
      if (files.length == 0) {
        return Collections.emptyList();
      }
      final LibraryTable.ModifiableModel modifiableModel = myLibraryTable.getModifiableModel();
      final List<Library> addedLibraries = new ArrayList<Library>(files.length);
      for (VirtualFile file : files) {
        final Library library = modifiableModel.createLibrary(null);
        final Library.ModifiableModel libModel = library.getModifiableModel();
        libModel.addRoot(file, OrderRootType.CLASSES);
        libModel.commit();
        addedLibraries.add(library);
      }
      return addedLibraries;
    }

    private VirtualFile[] filterAlreadyAdded(final VirtualFile[] files) {
      if (files == null || files.length == 0) {
        return VirtualFile.EMPTY_ARRAY;
      }
      final Set<VirtualFile> chosenFilesSet = new HashSet<VirtualFile>(Arrays.asList(files));
      final Set<VirtualFile> alreadyAdded = new HashSet<VirtualFile>();
      final Library[] libraries = myLibraryTable.getLibraries();
      for (Library library : libraries) {
        ContainerUtil.addAll(alreadyAdded, library.getFiles(OrderRootType.CLASSES));
      }
      chosenFilesSet.removeAll(alreadyAdded);
      return VfsUtil.toVirtualFileArray(chosenFilesSet);
    }

    public void doChoose() {
      myLastChosen = chooseNameAndFiles(myFileToSelect);
    }
  }

  private class ChooseNamedLibraryAction extends ChooseAndAddAction<Library> {
    private final LibraryTableModifiableModelProvider myLibraryTableModelProvider;

    public ChooseNamedLibraryAction(final int index, final String title, final LibraryTableModifiableModelProvider libraryTable) {
      super(index, title, Icons.LIBRARY_ICON);
      myLibraryTableModelProvider = libraryTable;
    }

    @Nullable
    protected TableItem createTableItem(final Library item) {
      // clear invalid order entry corresponding to added library if any
      final OrderEntry[] orderEntries = getRootModel().getOrderEntries();
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry ) {
          if (item.getName().equals(((LibraryOrderEntry)orderEntry).getLibraryName())) {
            if ( orderEntry.isValid() ) {
              Messages.showErrorDialog(ProjectBundle.message("classpath.message.library.already.added",item.getName()),
                                       ProjectBundle.message("classpath.title.adding.dependency"));
              return null;
            } else {
              getRootModel().removeOrderEntry(orderEntry);
            }
          }
        }
      }
      return new LibItem(getRootModel().addLibraryEntry(item));
    }

    protected ChooserDialog<Library> createChooserDialog() {
      return new MyChooserDialog();
    }

    private Collection<Library> getAlreadyAddedLibraries() {
      final OrderEntry[] orderEntries = getRootModel().getOrderEntries();
      final Set<Library> result = new HashSet<Library>(orderEntries.length);
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof LibraryOrderEntry && orderEntry.isValid()) {
          final LibraryImpl library = (LibraryImpl)((LibraryOrderEntry)orderEntry).getLibrary();
          if (library != null) {
            result.add(library.getSource());
          }
        }
      }
      return result;
    }

    private class MyChooserDialog implements ChooserDialog<Library> {
      private final LibraryTableEditor myEditor;
      private Library[] myLibraries;

      MyChooserDialog(){
        myEditor = LibraryTableEditor.editLibraryTable(myLibraryTableModelProvider, myState.getProject());
        Disposer.register(this, myEditor);
      }

      public List<Library> getChosenElements() {
        final List<Library> chosen = new ArrayList<Library>(Arrays.asList(myLibraries));
        chosen.removeAll(getAlreadyAddedLibraries());
        return chosen;
      }

      public void doChoose() {
        final Iterator iter = myLibraryTableModelProvider.getModifiableModel().getLibraryIterator();
        myLibraries = myEditor.openDialog(ClasspathPanel.this, iter.hasNext()? Collections.singleton((Library)iter.next()) : Collections.<Library>emptyList(), false);
      }

      public boolean isOK() {
        return myLibraries != null;
      }

      public void dispose() {
      }
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
        TableItem item = myModel.getItemAt(row);
        if (item instanceof LibItem) {
          LibraryOrderEntry orderEntry = ((LibItem)item).getEntry();
          if (orderEntry != null) {
            final Library library = orderEntry.getLibrary();
            if (library != null) {
              return new LibraryProjectStructureElement(getContext(), library);
            }
          }
        }
        else if (item instanceof ModuleItem) {
          ModuleOrderEntry orderEntry = ((ModuleItem)item).getEntry();
          if (orderEntry != null) {
            final Module module = orderEntry.getModule();
            if (module != null) {
              return new ModuleProjectStructureElement(getContext(), module);
            }
          }
        }
        else if (item instanceof JdkItem) {
          JdkOrderEntry orderEntry = ((JdkItem)item).getEntry();
          if (orderEntry != null) {
            final Sdk jdk = orderEntry.getJdk();
            if (jdk != null) {
              return new SdkProjectStructureElement(getContext(), jdk);
            }
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
