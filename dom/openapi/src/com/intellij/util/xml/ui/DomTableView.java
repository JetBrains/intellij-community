/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.ui;

import com.intellij.ide.actions.CommonActionsFactory;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Icons;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

/**
 * @author peter
 */
public class DomTableView extends JPanel implements DataProvider{
  @NonNls private static final String TREE = "Tree";
  @NonNls private static final String EMPTY_PANE = "EmptyPane";
  private final EventDispatcher<ChangeListener> myDispatcher = EventDispatcher.create(ChangeListener.class);
  private final MyListTableModel myTableModel = new MyListTableModel();
  private final MyTableView myTable = new MyTableView();
  private final String myHelpID;
  private final String myEmptyPaneText;
  private final JPanel myInnerPanel;
  private final Project myProject;
  private TableCellRenderer[][] myCachedRenderers;
  private EmptyPane myEmptyPane;

  public DomTableView(final Project project) {
    this(project, null, null);
  }

  public DomTableView(final Project project, final String emptyPaneText, final String helpID) {
    super(new BorderLayout());
    myProject = project;
    myTableModel.setSortable(false);

    myEmptyPaneText = emptyPaneText;
    myHelpID = helpID;

    //ToolTipHandlerProvider.getToolTipHandlerProvider().install(myTable);

    final JTableHeader header = myTable.getTableHeader();
    header.addMouseMotionListener(new MouseMotionAdapter() {
      public void mouseMoved(MouseEvent e) {
        updateTooltip(e);
      }
    });
    header.addMouseListener(new MouseAdapter() {
      public void mouseEntered(MouseEvent e) {
        updateTooltip(e);
      }
    });
    header.setReorderingAllowed(false);

    myTable.setRowHeight(Icons.CLASS_ICON.getIconHeight());
    myTable.setPreferredScrollableViewportSize(new Dimension(-1, 150));
    myTable.setSelectionMode(allowMultipleRowsSelection() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);

    myInnerPanel = new JPanel(new CardLayout());
    myInnerPanel.add(ScrollPaneFactory.createScrollPane(myTable), TREE);
    if (getEmptyPaneText() != null) {
      //noinspection HardCodedStringLiteral
      myEmptyPane = new EmptyPane("<html>" + getEmptyPaneText() + "</html>");
      final JComponent emptyPanel = myEmptyPane.getComponent();
      myInnerPanel.add(emptyPanel, EMPTY_PANE);
    }

    add(myInnerPanel, BorderLayout.CENTER);

    ToolTipManager.sharedInstance().registerComponent(myTable);
  }

  protected TableCellRenderer getTableCellRenderer(final int row, final int column, final TableCellRenderer superRenderer, final Object value) {
    return getTableModel().getColumnInfos()[column].getCustomizedRenderer(value, new StripeTableCellRenderer(superRenderer));
  }

  protected final void installPopup(final DefaultActionGroup group) {
    PopupHandler.installPopupHandler(myTable, group, ActionPlaces.J2EE_ATTRIBUTES_VIEW_POPUP, ActionManager.getInstance());
  }

  public final void setToolbarActions(final AnAction... actions) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (final AnAction action : actions) {
      actionGroup.add(action);
    }
    if (getHelpId() != null) {
      actionGroup.add(Separator.getInstance());
      actionGroup.add(CommonActionsFactory.getCommonActionsFactory().createContextHelpAction(getHelpId()));
    }

    final ActionManager actionManager = ActionManager.getInstance();
    final ToolbarPosition position = getToolbarPosition();
    final ActionToolbar myActionToolbar = actionManager.createActionToolbar(ActionPlaces.PROJECT_VIEW_TOOLBAR, actionGroup, position == ToolbarPosition.TOP || position == ToolbarPosition.BOTTOM);
    final JComponent toolbarComponent = myActionToolbar.getComponent();
    final MatteBorder matteBorder = BorderFactory.createMatteBorder(0, 0, position == ToolbarPosition.TOP ? 1 : 0, 0, Color.darkGray);
    toolbarComponent.setBorder(BorderFactory.createCompoundBorder(matteBorder, toolbarComponent.getBorder()));

    getTable().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myActionToolbar.updateActionsImmediately();
      }
    });

    add(toolbarComponent, position.getPosition());
  }


  protected final void setErrorMessages(String[] messages) {
    final boolean empty = messages.length == 0;
    final String tooltipText = TooltipUtils.getTooltipText(messages);
    if (myEmptyPane != null) {
      myEmptyPane.getComponent().setBackground(empty ? UIUtil.getTreeTextBackground() : BaseControl.ERROR_BACKGROUND);
      myEmptyPane.getComponent().setToolTipText(tooltipText);
    }
    final JViewport viewport = (JViewport)myTable.getParent();
    final Color tableBackground = empty ? UIUtil.getTableBackground() : BaseControl.ERROR_BACKGROUND;
    viewport.setBackground(tableBackground);
    viewport.setToolTipText(tooltipText);
    myTable.setBackground(tableBackground);
    myTable.setToolTipText(tooltipText);
    if (tooltipText == null) ToolTipManager.sharedInstance().registerComponent(myTable);
  }

  protected final void initializeTable() {
    myTable.setModel(myTableModel);
    if (getEmptyPaneText() != null) {
      final CardLayout cardLayout = ((CardLayout)myInnerPanel.getLayout());
      myTable.getModel().addTableModelListener(new TableModelListener() {
        public void tableChanged(TableModelEvent e) {
          cardLayout.show(myInnerPanel, myTable.getRowCount() == 0 ? EMPTY_PANE : TREE);
        }
      });
    }
    tuneTable(myTable);
  }

  protected final void fireTableChanged() {
    final int row = myTable.getSelectedRow();
    getTableModel().fireTableDataChanged();
    if (row >= 0 && row < myTableModel.getRowCount()) {
      myTable.getSelectionModel().setSelectionInterval(row, row);
    }
  }

  protected void adjustColumnWidths() {
    final ColumnInfo[] columnInfos = myTableModel.getColumnInfos();
    for (int i = 0; i < columnInfos.length; i++) {
      ColumnInfo columnInfo = columnInfos[i];
      final TableColumn column = myTable.getColumnModel().getColumn(i);
      int width = -1;
      for (int j = 0; j < myTableModel.getRowCount(); j++) {
        Object t = myTableModel.getItems().get(j);
        final Component component = myTable.getCellRenderer(j, i).getTableCellRendererComponent(myTable, columnInfo.valueOf(t), false, false, j, i);
        final int prefWidth = component.getPreferredSize().width;
        if (prefWidth > width) {
          width = prefWidth;
        }
      }
      if (width > 0) {
        column.setPreferredWidth(width);
      }
    }
  }

  protected String getEmptyPaneText() {
    return myEmptyPaneText;
  }

  protected final void updateTooltip(final MouseEvent e) {
    final int i = myTable.columnAtPoint(e.getPoint());
    final int k = myTable.rowAtPoint(e.getPoint());

    //myTable.getTableHeader().setToolTipText(((DefaultTableCellRenderer)myTable.getCellRenderer(i,k)).getToolTipText();

    if (i >= 0) {
      myTable.getTableHeader().setToolTipText(myTableModel.getColumnInfos()[i].getTooltipText());
    }
  }

  protected void tuneTable(JTable table) {
  }

  protected boolean allowMultipleRowsSelection() {
    return true;
  }

  public final JTable getTable() {
    return myTable;
  }

  public final ListTableModel getTableModel() {
    return myTableModel;
  }

  @Nullable
  public Object getData(String dataId) {
    if (DataConstantsEx.HELP_ID.equals(dataId)) {
      return getHelpId();
    }
    return null;
  }

  protected final String getHelpId() {
    return myHelpID;
  }

  public void addChangeListener(ChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  public final void reset(ColumnInfo[] columnInfos, List data) {
    if (myTable.isEditing()) {
      myTable.getCellEditor().cancelCellEditing();
    }
    final boolean columnsChanged = myTableModel.setColumnInfos(columnInfos);
    final boolean dataChanged = !data.equals(myTableModel.getItems());
    if (dataChanged) {
      final int selectedRow = myTable.getSelectedRow();
      myTableModel.setItems(new ArrayList(data));
      if (selectedRow >= 0 && selectedRow < myTableModel.getRowCount()) {
        myTable.getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
      }
    }

    myTableModel.cacheValues();
    final int rowCount = myTableModel.getRowCount();
    final int columnCount = myTableModel.getColumnCount();
    myCachedRenderers = new TableCellRenderer[rowCount][columnCount];
    for (int row = 0; row < rowCount; row++) {
      for (int column = 0; column < columnCount; column++) {
        final TableCellRenderer superRenderer = myTable.getSuperCellRenderer(row, column);
        myCachedRenderers[row][column] = getTableCellRenderer(row, column, superRenderer, myTableModel.getItems().get(row));
      }
    }
    if (columnsChanged) {
      adjustColumnWidths();
    }
  }

  private class MyListTableModel extends ListTableModel {
    private Object[][] myTableData;

    public MyListTableModel() {
      super(ColumnInfo.EMPTY_ARRAY);
    }

    public Object getValueAt(final int rowIndex, final int columnIndex) {
      return myTableData[rowIndex][columnIndex];
    }

    void cacheValues() {
      final int rowCount = getRowCount();
      final int columnCount = getColumnCount();
      final Object[][] objects = new Object[rowCount][columnCount];
      for (int i = 0; i < rowCount; i++) {
        for (int j = 0; j < columnCount; j++) {
          objects[i][j] = super.getValueAt(i, j);
        }
      }
      myTableData = objects;
    }

    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      final Object oldValue = getValueAt(rowIndex, columnIndex);
      if (!Comparing.equal(oldValue, aValue)) {
        final DomElement domElement = (DomElement)getItems().get(rowIndex);
        if (domElement.isValid()) {
          new WriteCommandAction(myProject, domElement.getRoot().getFile()) {
            protected void run(final Result result) throws Throwable {
              MyListTableModel.super.setValueAt("".equals(aValue) ? null : aValue, rowIndex, columnIndex);
            }
          }.execute();
          myDispatcher.getMulticaster().changed();
        }
      }
    }
  }

  protected void dispose() {
  }

  protected ToolbarPosition getToolbarPosition() {
    return ToolbarPosition.TOP;
  }

  protected static enum ToolbarPosition {
    TOP(BorderLayout.NORTH),
    LEFT(BorderLayout.WEST),
    RIGHT(BorderLayout.EAST),
    BOTTOM(BorderLayout.SOUTH);

    private final String myPosition;

    private ToolbarPosition(final String position) {
      myPosition = position;
    }

    public String getPosition() {
      return myPosition;
    }
  }

  public interface ChangeListener extends EventListener {
    void changed();
  }

  private class MyTableView extends TableView {

    public final TableCellRenderer getSuperCellRenderer(int row, int column) {
      return super.getCellRenderer(row, column);
    }

    public final TableCellRenderer getCellRenderer(int row, int column) {
      return myCachedRenderers[row][column];
    }
  }
}
