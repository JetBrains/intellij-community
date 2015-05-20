/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.ide.DataManager;
import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.diff.DiffType;
import com.intellij.ide.diff.DirDiffElement;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.impl.dir.actions.DirDiffToolbarActions;
import com.intellij.openapi.diff.impl.dir.actions.RefreshDirDiffAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ClickListener;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBLoadingPanelListener;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"unchecked"})
public class DirDiffPanel implements Disposable, DataProvider {
  public static final String DIVIDER_PROPERTY = "dir.diff.panel.divider.location";
  private JPanel myDiffPanel;
  private JBTable myTable;
  private JPanel myComponent;
  private JSplitPane mySplitPanel;
  private TextFieldWithBrowseButton mySourceDirField;
  private TextFieldWithBrowseButton myTargetDirField;
  private JBLabel myTargetDirLabel;
  private JBLabel mySourceDirLabel;
  private JPanel myToolBarPanel;
  private JPanel myRootPanel;
  private JPanel myFilterPanel;
  private JBLabel myFilterLabel;
  private JPanel myFilesPanel;
  private JPanel myHeaderPanel;
  private FilterComponent myFilter;
  private final DirDiffTableModel myModel;
  public JLabel myErrorLabel;
  private final DirDiffWindow myDiffWindow;
  private JComponent myDiffPanelComponent;
  private JComponent myViewComponent;
  private DiffElement myCurrentElement;
  private String oldFilter;
  public static final DataKey<DirDiffTableModel> DIR_DIFF_MODEL = DataKey.create("DIR_DIFF_MODEL");
  public static final DataKey<JTable> DIR_DIFF_TABLE = DataKey.create("DIR_DIFF_TABLE");

  public DirDiffPanel(DirDiffTableModel model, DirDiffWindow wnd) {
    myModel = model;
    myDiffWindow = wnd;
    mySourceDirField.setText(model.getSourceDir().getPath());
    myTargetDirField.setText(model.getTargetDir().getPath());
    mySourceDirField.setBorder(JBUI.Borders.emptyRight(8));
    myTargetDirField.setBorder(JBUI.Borders.emptyRight(12));
    mySourceDirLabel.setIcon(model.getSourceDir().getIcon());
    myTargetDirLabel.setIcon(model.getTargetDir().getIcon());
    myTargetDirLabel.setBorder(JBUI.Borders.emptyLeft(8));
    myModel.setTable(myTable);
    myModel.setPanel(this);
    Disposer.register(this, myModel);
    myTable.setModel(myModel);
    new TableSpeedSearch(myTable);

    final DirDiffTableCellRenderer renderer = new DirDiffTableCellRenderer();
    myTable.setExpandableItemsEnabled(false);
    myTable.setDefaultRenderer(Object.class, renderer);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    final Project project = myModel.getProject();
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        final int lastIndex = e.getLastIndex();
        final int firstIndex = e.getFirstIndex();
        final DirDiffElementImpl last = myModel.getElementAt(lastIndex);
        final DirDiffElementImpl first = myModel.getElementAt(firstIndex);
        if (last == null || first == null) {
          update(false);
          return;
        }
        if (last.isSeparator()) {
          final int ind = lastIndex + ((lastIndex < firstIndex) ? 1 : -1);
          myTable.getSelectionModel().addSelectionInterval(ind, ind);
        }
        else if (first.isSeparator()) {
          final int ind = firstIndex + ((firstIndex < lastIndex) ? 1 : -1);
          myTable.getSelectionModel().addSelectionInterval(ind, ind);
        }
        else {
          update(false);
        }
        myDiffWindow.setTitle(myModel.getTitle());
      }
    });
    if (model.isOperationsEnabled()) {
      new AnAction("Change diff operation") {
        @Override
        public void actionPerformed(AnActionEvent e) {
          changeOperationForSelection();
        }
      }.registerCustomShortcutSet(CustomShortcutSet.fromString("SPACE"), myTable);
      new ClickListener() {
        @Override
        public boolean onClick(@NotNull MouseEvent e, int clickCount) {
          if (e.getButton() == MouseEvent.BUTTON3) return false;
          if (myTable.getRowCount() > 0) {
            final int row = myTable.rowAtPoint(e.getPoint());
            final int col = myTable.columnAtPoint(e.getPoint());

            if (row != -1 && col == ((myTable.getColumnCount() - 1) / 2)) {
              changeOperationForSelection();
            }
          }
          return true;
        }
      }.installOn(myTable);

    }
    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        final int rowCount = myTable.getRowCount();
        int row = myTable.getSelectionModel().getLeadSelectionIndex();
        final int[] rows = myTable.getSelectedRows();
        if (rows.length == 0) return;
        if (keyCode == KeyEvent.VK_DOWN && row < rowCount - 1) {
          row++;
          final DirDiffElementImpl element = myModel.getElementAt(row);
          if (element == null) return;
          if (element.isSeparator()) {
            row++;
          }
        }
        else if (keyCode == KeyEvent.VK_UP && row > 0) {
          row--;
          final DirDiffElementImpl element = myModel.getElementAt(row);
          if (element == null) return;
          if (element.isSeparator()) {
            row--;
          }
        }
        else {
          return;
        }
        final DirDiffElementImpl element = myModel.getElementAt(row);
        if (element == null) return;
        if (!element.isSeparator()) {
          e.consume();
          myTable.changeSelection(row, (myModel.getColumnCount() - 1) / 2, false, e.isShiftDown());
        }
      }
    });
    final TableColumnModel columnModel = myTable.getColumnModel();
    final TableColumn operationColumn = columnModel.getColumn((columnModel.getColumnCount() - 1) / 2);
    operationColumn.setMaxWidth(JBUI.scale(25));
    operationColumn.setMinWidth(JBUI.scale(25));
    for (int i = 0; i < columnModel.getColumnCount(); i++) {
      final String name = myModel.getColumnName(i);
      final TableColumn column = columnModel.getColumn(i);
      if (DirDiffTableModel.COLUMN_DATE.equals(name)) {
        column.setMaxWidth(JBUI.scale(90));
        column.setMinWidth(JBUI.scale(90));
      } else if (DirDiffTableModel.COLUMN_SIZE.equals(name)) {
        column.setMaxWidth(JBUI.scale(120));
        column.setMinWidth(JBUI.scale(120));
      }
    }
    final DirDiffToolbarActions actions = new DirDiffToolbarActions(myModel, myDiffPanel);
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionToolbar toolbar = actionManager.createActionToolbar("DirDiff", actions, true);
    registerCustomShortcuts(actions, myTable);
    myToolBarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);
    final JBLabel label = new JBLabel("Use Space button or mouse click to change operation for the selected elements. Enter to perform.", SwingConstants.CENTER);
    label.setForeground(UIUtil.getInactiveTextColor());
    UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, label);
    DataManager.registerDataProvider(myFilesPanel, this);
    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final JPopupMenu popupMenu =
          actionManager.createActionPopupMenu("DirDiffPanel", (ActionGroup)actionManager.getAction("DirDiffMenu")).getComponent();
        popupMenu.show(comp, x, y);
      }
    });
    myFilesPanel.add(label, BorderLayout.SOUTH);
    final JBLoadingPanel loadingPanel = new JBLoadingPanel(new BorderLayout(), wnd.getDisposable());
    loadingPanel.addListener(new JBLoadingPanelListener.Adapter() {
      boolean showHelp = true;

      @Override
      public void onLoadingFinish() {
        if (showHelp && myModel.isOperationsEnabled() && myModel.getRowCount() > 0) {
          final long count = PropertiesComponent.getInstance().getOrInitLong("dir.diff.space.button.info", 0);
          if (count < 3) {
            JBPopupFactory.getInstance().createBalloonBuilder(new JLabel(" Use Space button to change operation"))
              .setFadeoutTime(5000)
              .setContentInsets(JBUI.insets(15))
              .createBalloon().show(new RelativePoint(myTable, new Point(myTable.getWidth() / 2, 0)), Balloon.Position.above);
            PropertiesComponent.getInstance().setValue("dir.diff.space.button.info", String.valueOf(count + 1));
          }
        }
        showHelp = false;
      }
    });
    loadingPanel.add(myComponent, BorderLayout.CENTER);
    myTable.putClientProperty(myModel.DECORATOR, loadingPanel);
    myTable.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        myTable.removeComponentListener(this);
        myModel.reloadModel(false);
      }
    });
    myRootPanel.removeAll();
    myRootPanel.add(loadingPanel, BorderLayout.CENTER);
    myFilter = new FilterComponent("dir.diff.filter", 15, false) {
      @Override
      public void filter() {
        fireFilterUpdated();
      }

      @Override
      protected void onEscape(KeyEvent e) {
        e.consume();
        focusTable();
      }

      @Override
      protected JComponent getPopupLocationComponent() {
        return UIUtil.findComponentOfType(super.getPopupLocationComponent(), JTextComponent.class);
      }
    };

    myModel.addModelListener(new DirDiffModelListener() {
      @Override
      public void updateStarted() {
        myFilter.setEnabled(false);
      }

      @Override
      public void updateFinished() {
        myFilter.setEnabled(true);
      }
    });
    myFilter.getTextEditor().setColumns(10);
    myFilter.setFilter(myModel.getSettings().getFilter());
    //oldFilter = myFilter.getText();
    oldFilter = myFilter.getFilter();
    myFilterPanel.add(myFilter, BorderLayout.CENTER);
    myFilterLabel.setLabelFor(myFilter);
    final Callable<DiffElement> srcChooser = myModel.getSourceDir().getElementChooser(project);
    final Callable<DiffElement> trgChooser = myModel.getTargetDir().getElementChooser(project);
    mySourceDirField.setEditable(false);
    myTargetDirField.setEditable(false);

    if (srcChooser != null && myModel.getSettings().enableChoosers) {
      mySourceDirField.setButtonEnabled(true);
      mySourceDirField.addActionListener(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            final Callable<DiffElement> chooser = myModel.getSourceDir().getElementChooser(project);
            if (chooser == null) return;
            final DiffElement newElement = chooser.call();
            if (newElement != null) {
              if (!StringUtil.equals(mySourceDirField.getText(), newElement.getPath())) {
                myModel.setSourceDir(newElement);
                mySourceDirField.setText(newElement.getPath());
                myModel.clearWithMessage("Source or Target has been changed. Please run Refresh (" + KeymapUtil.getShortcutsText(
                  RefreshDirDiffAction.REFRESH_SHORTCUT.getShortcuts()) + ")");
              }
            }
          } catch (Exception e1) {//
          }
        }
      });
    } else {
      Dimension preferredSize = mySourceDirField.getPreferredSize();
      mySourceDirField.setButtonEnabled(false);
      mySourceDirField.getButton().setVisible(false);
      mySourceDirField.setPreferredSize(preferredSize);
    }

    if (trgChooser != null && myModel.getSettings().enableChoosers) {
      myTargetDirField.setButtonEnabled(true);
      myTargetDirField.addActionListener(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            final Callable<DiffElement> chooser = myModel.getTargetDir().getElementChooser(project);
            if (chooser == null) return;
            final DiffElement newElement = chooser.call();
            if (newElement != null) {
              myModel.setTargetDir(newElement);
              myTargetDirField.setText(newElement.getPath());
            }
          } catch (Exception e1) {//
          }
        }
      });
    } else {
      Dimension preferredSize = myTargetDirField.getPreferredSize();
      myTargetDirField.setButtonEnabled(false);
      myTargetDirField.getButton().setVisible(false);
      myTargetDirField.setPreferredSize(preferredSize);
    }
  }

  public AnAction[] getActions() {
    return new DirDiffToolbarActions(myModel, myDiffPanel).getChildren(null);
  }

  public JComponent extractFilterPanel() {
    myHeaderPanel.setVisible(false);
    return myFilterPanel;
  }

  private void changeOperationForSelection() {
    for (int row : myTable.getSelectedRows()) {
      if (row != -1) {
        final DirDiffElementImpl element = myModel.getElementAt(row);
        if (element != null) {
          element.setNextOperation();
          myModel.fireTableRowsUpdated(row, row);
        }
      }
    }
  }

  public void update(boolean force) {
    final Project project = myModel.getProject();
    final DirDiffElementImpl element = myModel.getElementAt(myTable.getSelectedRow());
    if (element == null) {
      clearDiffPanel();
      return;
    }
    if (!force
        && myCurrentElement != null
        && (myCurrentElement == element.getSource() || myCurrentElement == element.getTarget())) {
      return;
    }
    clearDiffPanel();
    if (element.getType() == DiffType.CHANGED) {
      try {
        myDiffPanelComponent = element.getSource().getDiffComponent(element.getTarget(), project, myDiffWindow.getWindow(), myModel);
      }
      catch (FilesTooBigForDiffException e) {
        // todo KB: check
        myDiffPanelComponent = null;
        myErrorLabel = new JLabel("Can not build diff for file " + element.getTarget().getPath() + ". File is too big and there are too many changes.");
      }
      if (myDiffPanelComponent != null) {
        myDiffPanel.add(myDiffPanelComponent, BorderLayout.CENTER);
        myCurrentElement = element.getSource();
      } else {
        myDiffPanel.add(getErrorLabel(), BorderLayout.CENTER);
      }
    } else {
      final DiffElement object;
      final DiffElement target;
      if (element.getType() == DiffType.ERROR) {
        object = element.getSource() == null ? element.getTarget() : element.getSource();
        target = element.getSource() == null ? element.getSource() : element.getTarget();
      } else {
        object = element.isSource() ? element.getSource() : element.getTarget();
        target = element.isSource() ? element.getTarget() : element.getSource();
      }
      myViewComponent = object.getViewComponent(project, target, myModel);

      if (myViewComponent != null) {
        myCurrentElement = object;
        myDiffPanel.add(myViewComponent, BorderLayout.CENTER);
        DataProvider dataProvider = myCurrentElement.getDataProvider(project);
        if (dataProvider != null) {
          DataManager.registerDataProvider(myDiffPanel, dataProvider);
        }
        else {
          DataManager.removeDataProvider(myDiffPanel);
        }
      } else {
        myDiffPanel.add(getErrorLabel(), BorderLayout.CENTER);
      }
    }
    myDiffPanel.revalidate();
    myDiffPanel.repaint();
  }

  private void registerCustomShortcuts(DirDiffToolbarActions actions, JComponent component) {
    for (AnAction action : actions.getChildren(null)) {
      if (action instanceof ShortcutProvider) {
        final ShortcutSet shortcut = ((ShortcutProvider)action).getShortcut();
        if (shortcut != null) {
          action.registerCustomShortcutSet(shortcut, component);
        }
      }
    }
  }

  public void focusTable() {
    final Project project = myModel.getProject();
    final IdeFocusManager focusManager = project == null || project.isDefault()
                                         ? IdeFocusManager.getGlobalInstance() : IdeFocusManager.getInstance(project);
    focusManager.doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        focusManager.requestFocus(myTable, true);
      }
    });
  }

  public String getFilter() {
    return myFilter.getFilter();
  }

  private void fireFilterUpdated() {
    final String newFilter = myFilter.getFilter();
    if (!StringUtil.equals(oldFilter, newFilter)) {
      oldFilter = newFilter;
      myModel.getSettings().setFilter(newFilter);
      myModel.applySettings();
    }
  }

  private JLabel getErrorLabel() {
    return myErrorLabel == null ? myErrorLabel = new JLabel("Unknown or binary file type", SwingConstants.CENTER) : myErrorLabel;
  }

  private void clearDiffPanel() {
    if (myDiffPanelComponent != null) {
      myDiffPanel.remove(myDiffPanelComponent);
      myDiffPanelComponent = null;
      if (myCurrentElement != null) {
        myCurrentElement.disposeDiffComponent();
      }
    }
    if (myViewComponent != null) {
      myDiffPanel.remove(myViewComponent);
      myViewComponent = null;
      if (myCurrentElement != null) {
        myCurrentElement.disposeViewComponent();
      }
    }
    myCurrentElement = null;
    myDiffPanel.remove(getErrorLabel());
    DataManager.removeDataProvider(myDiffPanel);
    myDiffPanel.repaint();
  }

  public JComponent getPanel() {
    return myRootPanel;
  }

  public JBTable getTable() {
    return myTable;
  }

  public void dispose() {
    myModel.stopUpdating();
    PropertiesComponent.getInstance().setValue(DIVIDER_PROPERTY, String.valueOf(mySplitPanel.getDividerLocation()));
    clearDiffPanel();
  }

  private void createUIComponents() {
    mySourceDirField = new TextFieldWithBrowseButton(null, this);
    myTargetDirField = new TextFieldWithBrowseButton(null, this);

    final AtomicBoolean callUpdate = new AtomicBoolean(true);
    myRootPanel = new JPanel(new BorderLayout()) {
      @Override
      protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (callUpdate.get()) {
          callUpdate.set(false);
          myModel.reloadModel(false);
        }
      }
    };
  }

  public void setupSplitter() {
    mySplitPanel.setDividerLocation(Integer.valueOf(PropertiesComponent.getInstance().getValue(DIVIDER_PROPERTY, "200")));
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myModel.getProject();
    }
    else if (DIR_DIFF_MODEL.is(dataId)) {
      return myModel;
    }
    else if (DIR_DIFF_TABLE.is(dataId)) {
      return myTable;
    }
    else if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getOpenFileDescriptorsArray();
    }
    else if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      return getOpenFileDescriptor();
    }
    else if (DiffDataKeys.OPEN_FILE_DESCRIPTOR.is(dataId)) {
      return getOpenFileDescriptor();
    }
    DataProvider provider = DataManager.getDataProvider(myDiffPanel);
    return provider != null ? provider.getData(dataId) : null;
  }

  @Nullable
  private OpenFileDescriptor getOpenFileDescriptor() {
    Project project = myModel.getProject();
    List<DirDiffElementImpl> elements = myModel.getSelectedElements();
    if (elements.isEmpty()) return null;
    DirDiffElement element = elements.get(0);
    DiffElement source = element.getSource();
    DiffElement target = element.getTarget();
    OpenFileDescriptor descriptor1 = source != null ? source.getOpenFileDescriptor(project) : null;
    OpenFileDescriptor descriptor2 = target != null ? target.getOpenFileDescriptor(project) : null;
    return descriptor2 != null ? descriptor2 : descriptor1;
  }

  @Nullable
  private OpenFileDescriptor[] getOpenFileDescriptorsArray() {
    Project project = myModel.getProject();
    List<DirDiffElementImpl> elements = myModel.getSelectedElements();
    List<OpenFileDescriptor> descriptors = new ArrayList<OpenFileDescriptor>();
    for (DirDiffElementImpl element : elements) {
      DiffElement source = element.getSource();
      DiffElement target = element.getTarget();
      OpenFileDescriptor descriptor1 = source != null ? source.getOpenFileDescriptor(project) : null;
      OpenFileDescriptor descriptor2 = target != null ? target.getOpenFileDescriptor(project) : null;
      if (descriptor1 != null) descriptors.add(descriptor1);
      if (descriptor2 != null) descriptors.add(descriptor2);
    }
    return ContainerUtil.toArray(descriptors, new OpenFileDescriptor[descriptors.size()]);
  }
}
