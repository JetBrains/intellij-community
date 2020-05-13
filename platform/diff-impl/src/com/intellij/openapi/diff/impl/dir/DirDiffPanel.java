// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.dir;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.impl.CacheDiffRequestProcessor;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.ide.DataManager;
import com.intellij.ide.diff.DiffElement;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.dir.actions.DirDiffToolbarActions;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBLoadingPanelListener;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.table.JBTable;
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
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings({"unchecked"})
public class DirDiffPanel implements Disposable, DataProvider {
  private static final Logger LOG = Logger.getInstance(DirDiffPanel.class);

  public static final String DIVIDER_PROPERTY = "dir.diff.panel.divider.location";

  private static final int DIVIDER_PROPERTY_DEFAULT_VALUE = 200;

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
  private final FilterComponent myFilter;
  private final DirDiffTableModel myModel;
  private final DirDiffWindow myDiffWindow;
  private final MyDiffRequestProcessor myDiffRequestProcessor;
  private final PrevNextDifferenceIterable myPrevNextDifferenceIterable;
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
    myTable.getTableHeader().setReorderingAllowed(false);
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
    myTable.setDefaultRenderer(Object.class, renderer);
    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    final Project project = myModel.getProject();
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        ListSelectionModel selectionModel = (ListSelectionModel)e.getSource();
        int lastIndex = e.getLastIndex();
        int firstIndex = e.getFirstIndex();
        DirDiffElementImpl last = myModel.getElementAt(lastIndex);
        DirDiffElementImpl first = myModel.getElementAt(firstIndex);
        if (last == null || first == null) {
          update(false);
          return;
        }
        if (last.isSeparator() && selectionModel.isSelectedIndex(lastIndex)) {
          int ind = lastIndex + ((lastIndex < firstIndex) ? 1 : -1);
          selectionModel.addSelectionInterval(ind, ind);
        }
        if (first.isSeparator() && selectionModel.isSelectedIndex(firstIndex)) {
          int ind = firstIndex + ((firstIndex < lastIndex) ? 1 : -1);
          selectionModel.addSelectionInterval(ind, ind);
        }
        else {
          update(false);
        }
        myDiffWindow.setTitle(myModel.getTitle());
      }
    });
    if (model.isOperationsEnabled()) {
      new DumbAwareAction(DiffBundle.messagePointer("action.Anonymous.text.change.diff.operation")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
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

    myTable.getActionMap().put(TableActions.Up.ID, createNavigationAction(false, false));
    myTable.getActionMap().put(TableActions.Down.ID, createNavigationAction(true, false));
    myTable.getActionMap().put(TableActions.ShiftUp.ID, createNavigationAction(false, true));
    myTable.getActionMap().put(TableActions.ShiftDown.ID, createNavigationAction(true, true));

    final TableColumnModel columnModel = myTable.getColumnModel();
    for (int i = 0; i < columnModel.getColumnCount(); i++) {
      DirDiffTableModel.ColumnType type = myModel.getColumnType(i);
      TableColumn column = columnModel.getColumn(i);
      if (type == DirDiffTableModel.ColumnType.DATE) {
        column.setPreferredWidth(JBUIScale.scale(110));
        column.setMinWidth(JBUIScale.scale(90));
      }
      else if (type == DirDiffTableModel.ColumnType.SIZE) {
        column.setPreferredWidth(JBUIScale.scale(120));
        column.setMinWidth(JBUIScale.scale(90));
      }
      else if (type == DirDiffTableModel.ColumnType.NAME) {
        column.setPreferredWidth(JBUIScale.scale(800));
        column.setMinWidth(JBUIScale.scale(120));
      }
      else if (type == DirDiffTableModel.ColumnType.OPERATION) {
        column.setMaxWidth(JBUIScale.scale(25));
        column.setMinWidth(JBUIScale.scale(25));
      }
    }

    final DirDiffToolbarActions actions = new DirDiffToolbarActions(myModel, myDiffPanel);
    final ActionManager actionManager = ActionManager.getInstance();
    final ActionToolbar toolbar = actionManager.createActionToolbar("DirDiff", actions, true);
    registerCustomShortcuts(actions, myTable);
    myToolBarPanel.add(toolbar.getComponent(), BorderLayout.CENTER);
    if (model.isOperationsEnabled()) {
      final JBLabel label = new JBLabel(DiffBundle.message("use.space.button.or.mouse.click"), SwingConstants.CENTER);
      label.setForeground(UIUtil.getInactiveTextColor());
      UIUtil.applyStyle(UIUtil.ComponentStyle.MINI, label);
      myFilesPanel.add(label, BorderLayout.SOUTH);
    }
    DataManager.registerDataProvider(myFilesPanel, this);
    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        final JPopupMenu popupMenu =
          actionManager.createActionPopupMenu("DirDiffPanel", (ActionGroup)actionManager.getAction("DirDiffMenu")).getComponent();
        popupMenu.show(comp, x, y);
      }
    });
    final JBLoadingPanel loadingPanel = new JBLoadingPanel(new BorderLayout(), wnd.getDisposable());
    loadingPanel.addListener(new JBLoadingPanelListener.Adapter() {
      boolean showHelp = true;

      @Override
      public void onLoadingFinish() {
        if (showHelp && myModel.isOperationsEnabled() && myModel.getRowCount() > 0) {
          final long count = PropertiesComponent.getInstance().getLong("dir.diff.space.button.info", 0);
          if (count < 3) {
            JBPopupFactory.getInstance().createBalloonBuilder(new JLabel(
              DiffBundle.message("use.space.button.to.change.operation")))
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
    ComponentUtil.putClientProperty(myTable, DirDiffTableModel.DECORATOR_KEY, loadingPanel);
    myTable.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentShown(ComponentEvent e) {
        myTable.removeComponentListener(this);
        myModel.reloadModel(false);
      }
    });
    myRootPanel.removeAll();
    myRootPanel.add(loadingPanel, BorderLayout.CENTER);
    myFilter = new MyFilterComponent();

    myModel.addModelListener(new DirDiffModelListener() {
      @Override
      public void updateStarted() {
        UIUtil.setEnabled(myFilter, false, true);
      }

      @Override
      public void updateFinished() {
        UIUtil.setEnabled(myFilter, true, true);
      }
    });
    myFilter.getTextEditor().setColumns(10);
    myFilter.setFilter(myModel.getSettings().getFilter());
    oldFilter = myFilter.getFilter();
    myFilterPanel.add(myFilter, BorderLayout.CENTER);
    myFilterLabel.setLabelFor(myFilter);

    setDirFieldChooser(myModel.getSourceDir().getElementChooser(project), false);
    setDirFieldChooser(myModel.getTargetDir().getElementChooser(project), true);

    myDiffRequestProcessor = new MyDiffRequestProcessor(project);
    Disposer.register(this, myDiffRequestProcessor);
    myDiffPanel.add(myDiffRequestProcessor.getComponent(), BorderLayout.CENTER);

    myPrevNextDifferenceIterable = new MyPrevNextDifferenceIterable();
  }

  private void setDirFieldChooser(@Nullable Callable<DiffElement<?>> chooser, boolean isTarget) {
    @NotNull TextFieldWithBrowseButton dirField = isTarget ? myTargetDirField : mySourceDirField;
    dirField.setEditable(false);

    if (chooser != null && myModel.getSettings().enableChoosers) {
      dirField.setButtonEnabled(true);
      dirField.addActionListener(new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          try {
            final DiffElement<?> newElement = chooser.call();
            if (newElement == null) return;
            if (StringUtil.equals(dirField.getText(), newElement.getPath())) return;

            dirField.setText(newElement.getPath());
            if (isTarget) {
              myModel.setTargetDir(newElement);
            }
            else {
              myModel.setSourceDir(newElement);
            }

            myModel.clear();
            myModel.reloadModel(true);
            myModel.updateFromUI();
          }
          catch (Exception err) {
            LOG.warn(err);
          }
        }
      });
    }
    else {
      Dimension preferredSize = dirField.getPreferredSize();
      dirField.setButtonEnabled(false);
      dirField.getButton().setVisible(false);
      dirField.setPreferredSize(preferredSize);
    }
  }

  @NotNull
  private AbstractAction createNavigationAction(boolean goDown, boolean withSelection) {
    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int row = goDown ? getNextRow() : getPrevRow();
        if (row != -1) {
          selectRow(row, withSelection);
        }
      }
    };
  }

  private int getNextRow() {
    if (myTable.getSelectedRows().length == 0) return -1;
    int rowCount = myTable.getRowCount();
    int row = myTable.getSelectionModel().getLeadSelectionIndex();

    while (true) {
      if (row >= rowCount) return -1;
      row++;
      DirDiffElementImpl element = myModel.getElementAt(row);
      if (element == null) return -1;
      if (!element.isSeparator()) break;
    }

    return row;
  }

  private int getPrevRow() {
    if (myTable.getSelectedRows().length == 0) return -1;
    int row = myTable.getSelectionModel().getLeadSelectionIndex();

    while (true) {
      if (row <= 0) return -1;
      row--;
      DirDiffElementImpl element = myModel.getElementAt(row);
      if (element == null) return -1;
      if (!element.isSeparator()) break;
    }

    return row;
  }

  private void selectRow(int row, boolean extend) {
    if (row == -1) return;
    DirDiffElementImpl element = myModel.getElementAt(row);
    if (element == null || element.isSeparator()) return;
    myTable.changeSelection(row, (myModel.getColumnCount() - 1) / 2, false, extend);
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
    myDiffRequestProcessor.updateRequest(force);
  }

  private static void registerCustomShortcuts(DirDiffToolbarActions actions, JComponent component) {
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
    final IdeFocusManager focusManager = project == null || project.isDefault() ?
                                         IdeFocusManager.getGlobalInstance() :
                                         IdeFocusManager.getInstance(project);
    focusManager.requestFocus(myTable, true);
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

  public JComponent getPanel() {
    return myRootPanel;
  }

  public JBTable getTable() {
    return myTable;
  }

  @Override
  public void dispose() {
    myModel.stopUpdating();
    PropertiesComponent.getInstance().setValue(DIVIDER_PROPERTY, mySplitPanel.getDividerLocation(), DIVIDER_PROPERTY_DEFAULT_VALUE);
  }

  private void createUIComponents() {
    myTable = new MyJBTable();

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
    int value = PropertiesComponent.getInstance().getInt(DIVIDER_PROPERTY, DIVIDER_PROPERTY_DEFAULT_VALUE);
    mySplitPanel.setDividerLocation(Integer.valueOf(value));
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myModel.getProject();
    }
    else if (DIR_DIFF_MODEL.is(dataId)) {
      return myModel;
    }
    else if (DIR_DIFF_TABLE.is(dataId)) {
      return myTable;
    }
    else if (DiffDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getNavigatableArray();
    }
    else if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.is(dataId)) {
      return myPrevNextDifferenceIterable;
    }
    return null;
  }

  private Navigatable @NotNull [] getNavigatableArray() {
    Project project = myModel.getProject();
    List<DirDiffElementImpl> elements = myModel.getSelectedElements();
    List<Navigatable> navigatables = new ArrayList<>();
    for (DirDiffElementImpl element : elements) {
      DiffElement source = element.getSource();
      DiffElement target = element.getTarget();
      Navigatable navigatable1 = source != null ? source.getNavigatable(project) : null;
      Navigatable navigatable2 = target != null ? target.getNavigatable(project) : null;
      if (navigatable1 != null) navigatables.add(navigatable1);
      if (navigatable2 != null) navigatables.add(navigatable2);
    }
    return navigatables.toArray(new Navigatable[0]);
  }

  private static class MyJBTable extends JBTable {
    @Override
    public void doLayout() {
      super.doLayout();

      int totalWidth1 = 0;
      int totalWidth2 = 0;
      for (int i = 0; i < (columnModel.getColumnCount() - 1) / 2; i++) {
        TableColumn column1 = columnModel.getColumn(i);
        TableColumn column2 = columnModel.getColumn(columnModel.getColumnCount() - i - 1);
        int delta = (column2.getWidth() - column1.getWidth()) / 2;
        if (Math.abs(delta) > 0) {
          column1.setWidth(column1.getWidth() + delta);
          column2.setWidth(column2.getWidth() - delta);
        }

        totalWidth1 += column1.getWidth();
        totalWidth2 += column2.getWidth();
      }


      TableColumn column1 = columnModel.getColumn(0);
      TableColumn column2 = columnModel.getColumn(columnModel.getColumnCount() - 1);

      int delta = (totalWidth2 - totalWidth1) / 2;
      if (Math.abs(delta) > 0) {
        column1.setWidth(column1.getWidth() + delta);
        column2.setWidth(column2.getWidth() - delta);
        totalWidth1 += delta;
        totalWidth2 -= delta;
      }

      if (totalWidth1 != totalWidth2 && totalWidth1 % 2 != 0) {
        column1.setWidth(column1.getWidth() - 1);
        column2.setWidth(column2.getWidth() + 1);
      }
    }
  }

  private class MyPrevNextDifferenceIterable implements PrevNextDifferenceIterable {
    @Override
    public boolean canGoPrev() {
      return getPrevRow() != -1;
    }

    @Override
    public boolean canGoNext() {
      return getNextRow() != -1;
    }

    @Override
    public void goPrev() {
      selectRow(getPrevRow(), false);
    }

    @Override
    public void goNext() {
      selectRow(getNextRow(), false);
    }
  }

  private class MyDiffRequestProcessor extends CacheDiffRequestProcessor<ElementWrapper> {
    MyDiffRequestProcessor(@Nullable Project project) {
      super(project, DiffPlaces.DIR_DIFF);
    }

    @Nullable
    @Override
    protected String getRequestName(@NotNull ElementWrapper element) {
      return null;
    }

    @Override
    protected ElementWrapper getCurrentRequestProvider() {
      DirDiffElementImpl element = myModel.getElementAt(myTable.getSelectedRow());
      return element != null ? new ElementWrapper(element) : null;
    }

    @NotNull
    @Override
    protected DiffRequest loadRequest(@NotNull ElementWrapper element, @NotNull ProgressIndicator indicator)
      throws ProcessCanceledException, DiffRequestProducerException {
      final Project project = myModel.getProject();
      DiffElement sourceElement = element.sourceElement;
      DiffElement targetElement = element.targetElement;

      DiffContent sourceContent = sourceElement != null ? sourceElement.createDiffContent(project, indicator) :
                                  DiffContentFactory.getInstance().createEmpty();
      DiffContent targetContent = targetElement != null ? targetElement.createDiffContent(project, indicator) :
                                  DiffContentFactory.getInstance().createEmpty();

      return new SimpleDiffRequest(null, sourceContent, targetContent, null, null);
    }

    //
    // Navigation
    //

    @Override
    protected boolean hasNextChange(boolean fromUpdate) {
      return getNextRow() != -1;
    }

    @Override
    protected boolean hasPrevChange(boolean fromUpdate) {
      return getPrevRow() != -1;
    }

    @Override
    protected void goToNextChange(boolean fromDifferences) {
      selectRow(getNextRow(), false);
      updateRequest(false, fromDifferences ? ScrollToPolicy.FIRST_CHANGE : null);
    }

    @Override
    protected void goToPrevChange(boolean fromDifferences) {
      selectRow(getPrevRow(), false);
      updateRequest(false, fromDifferences ? ScrollToPolicy.LAST_CHANGE : null);
    }

    @Override
    protected boolean isNavigationEnabled() {
      return myModel.getRowCount() > 0;
    }
  }

  private static class ElementWrapper {
    @Nullable public final DiffElement sourceElement;
    @Nullable public final DiffElement targetElement;

    ElementWrapper(@NotNull DirDiffElementImpl element) {
      sourceElement = element.getSource();
      targetElement = element.getTarget();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ElementWrapper wrapper = (ElementWrapper)o;
      return Objects.equals(sourceElement, wrapper.sourceElement) && Objects.equals(targetElement, wrapper.targetElement);
    }

    @Override
    public int hashCode() {
      int result = sourceElement != null ? sourceElement.hashCode() : 0;
      result = 31 * result + (targetElement != null ? targetElement.hashCode() : 0);
      return result;
    }
  }

  private class MyFilterComponent extends FilterComponent {
    MyFilterComponent() {
      super("dir.diff.filter", 15, false);

      DumbAwareAction.create(e -> userTriggeredFilter())
        .registerCustomShortcutSet(CommonShortcuts.ENTER, this);
    }

    @Override
    public void filter() {
      fireFilterUpdated();
    }

    @Override
    protected void onEscape(@NotNull KeyEvent e) {
      e.consume();
      focusTable();
    }

    @Override
    protected JComponent getPopupLocationComponent() {
      return UIUtil.findComponentOfType(super.getPopupLocationComponent(), JTextComponent.class);
    }
  }
}
