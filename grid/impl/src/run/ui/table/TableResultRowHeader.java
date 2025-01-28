package com.intellij.database.run.ui.table;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.grid.CellAttributes;
import com.intellij.database.run.ui.grid.CellRenderingUtils;
import com.intellij.database.run.ui.grid.GridRowHeader;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.database.util.DataGridUIUtil;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.CellRendererPanel;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;

import static com.intellij.database.run.actions.ChangeColumnDisplayTypeAction.isBinary;
import static java.awt.event.InputEvent.ALT_DOWN_MASK;
import static javax.swing.SwingUtilities.isLeftMouseButton;

/**
 * @author gregsh
 */
public class TableResultRowHeader extends GridRowHeader {
  private final DataGrid myResultPanel;
  private final TableResultView myTable;
  private final ActionGroup myPopupActions;
  private final RowHeaderCellRenderer myRenderer;

  public TableResultRowHeader(@NotNull DataGrid resultPanel, @NotNull TableResultView view, @Nullable ActionGroup popupActions) {
    super(view, () -> view.getShowHorizontalLines(), () -> view.getShowLastHorizontalLine());
    myResultPanel = resultPanel;
    myTable = view;
    myPopupActions = popupActions;
    myRenderer = new MyRowHeaderCellRenderer(createTransposedRenderer(), createRegularRenderer());

    GutterMouseListener mouseListener = new GutterMouseListener();
    addMouseMotionListener(mouseListener);
    addMouseListener(mouseListener);
    new TableHoverListener() {
      @Override
      public void onHover(@NotNull JTable table, int row, int column) {
        revalidate();
        repaint();
      }
    }.addTo(myTable);
  }

  protected @NotNull RowHeaderCellComponentBase createRegularRenderer() {
    return new RowNumberRowHeaderCellComponent();
  }

  protected @NotNull RowHeaderCellComponentBase createTransposedRenderer() {
    return new ColumnInfoRowHeaderCellComponent();
  }

  @Override
  public @NotNull RowHeaderCellRenderer getCellRenderer() {
    return myRenderer;
  }

  @Override
  public @NotNull JTable getTable() {
    return myTable;
  }

  private boolean isTransposed() {
    return myTable.isTransposed();
  }

  public int rowForPoint(@Nullable Point point) {
    return point != null ? myTable.rowAtPoint(point) : -1;
  }

  @Override
  public void paintComponent(Graphics g) {
    super.paintComponent(g);
    paintCellsEffects(g);
  }

  @Override
  protected int calcPreferredWidthWithoutInsets() {
    if (isTransposed() || myTable.isEmpty()) return super.calcPreferredWidthWithoutInsets();

    int lastRowIdx = myTable.getModel().getRowCount() - 1;
    return ((RowNumberRowHeaderCellComponent)getCellRenderer().getRendererComponent(lastRowIdx, false)).getPreferredWidth();
  }

  private void paintCellsEffects(final Graphics g) {
    CellRenderingUtils.processVisibleRows(myTable, g, (row, y) -> {
      paintCellEffects(g, row, new Rectangle(0, y, getWidth(), myTable.getRowHeight(row)));
      return true;
    });
  }

  private void paintCellEffects(Graphics g, int row, Rectangle cellRect) {
    CellAttributes attributes;
    if (isTransposed()) {
      attributes = myResultPanel.getMarkupModel().getColumnHeaderAttributes(
        ViewIndex.forColumn(myResultPanel, row).toModel(myResultPanel),
        myResultPanel.getColorsScheme());
    }
    else {
      attributes = myResultPanel.getMarkupModel().getRowHeaderAttributes(
        ViewIndex.forRow(myResultPanel, row).toModel(myResultPanel),
        myResultPanel.getColorsScheme());
    }
    if (attributes != null) {
      CellRenderingUtils.paintCellEffect(g, cellRect, attributes, DataGridUIUtil.softHighlightOf(attributes.getEffectColor()));
    }
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    Point point = event.getPoint();
    int viewRowIdx = rowForPoint(point);
    if (viewRowIdx < 0 || !isTransposed()) return null;

    ModelIndex<GridColumn> columnIdx = ModelIndex.forColumn(myResultPanel, myTable.convertRowIndexToModel(viewRowIdx));
    return GridHelper.get(myResultPanel).getColumnTooltipHtml(myResultPanel, columnIdx);
  }

  private class GutterMouseListener extends PopupHandler {
    private boolean mySelectWhileDraggingInExclusiveMode;
    private int myResizingRow = -1;
    private boolean myResizingColumn = false;

    @Override
    public void mouseClicked(MouseEvent e) {
      if (isTransposed() && isLeftMouseButton(e) && e.getModifiersEx() == ALT_DOWN_MASK) {
        int viewRow = myTable.rowAtPoint(e.getPoint());
        int modelColumn = myResultPanel.getRawIndexConverter().column2Model().applyAsInt(viewRow);
        DataGridSettings settings = GridUtil.getSettings(myResultPanel);
        myTable.toggleSortOrder(ModelIndex.forColumn(myResultPanel, modelColumn), settings == null || settings.isAddToSortViaAltClick());
        return;
      }
      if (isLeftMouseButton(e) && e.getClickCount() % 2 == 0) {
        packRow(e);
        toggleColumnSorting(e);
      }
      super.mouseClicked(e);
    }

    @Override
    public void invokePopup(Component comp, int x, int y) {
      Point point = new Point(x, y);
      int viewRow = myTable.rowAtPoint(point);
      if (viewRow < 0) return;
      if (isTransposed()) {
        int modelColumn = myResultPanel.getRawIndexConverter().column2Model().applyAsInt(viewRow);
        if (modelColumn >= 0) {
          myTable.invokeColumnPopup(ModelIndex.forColumn(myResultPanel, modelColumn), comp, point);
        }
      }
      else if (myPopupActions != null) {
        ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.EDITOR_POPUP, myPopupActions);
        menu.getComponent().show(comp, x, y);
      }
      else {
        int modelRow = myResultPanel.getRawIndexConverter().row2Model().applyAsInt(viewRow);
        if (modelRow >= 0) {
          myTable.invokeRowPopup(comp, x, y);
        }
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if (shouldResizeColumn(e)) {
        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
      }
      else if (shouldResizeRow(e)) {
        setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
      }
      else {
        setCursor(Cursor.getDefaultCursor());
      }
    }

    private boolean shouldResizeRow(MouseEvent e) {
      return getRowToResize(e) != -1;
    }

    private boolean shouldResizeColumn(MouseEvent e) {
      return myTable.isTransposed() && e.getX() >= (getWidth() - 5) && e.getX() <= getWidth();
    }

    @Override
    public void mousePressed(MouseEvent e) {
      if (shouldResizeColumn(e)) {
        myResizingColumn = true;
      }
      else {
        int rowToResize = getRowToResize(e);
        if (rowToResize != -1) {
          myResizingRow = rowToResize;
        }
        else {
          myResultPanel.getAutoscrollLocker().runWithLock(() -> processSelectionEvent(e, false));
        }
      }
      super.mousePressed(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      myResizingRow = -1;
      myResizingColumn = false;
      super.mouseReleased(e);
    }

    @Override
    public void mouseDragged(@NotNull MouseEvent e) {
      if (myResizingColumn) {
        TableResultRowHeader.this.updatePreferredSize(e.getX(), true);
        TableResultRowHeader.this.revalidate();
        TableResultRowHeader.this.repaint();
      }
      else if (myResizingRow != -1) {
        Rectangle cellRect = myTable.getCellRect(myResizingRow, 0, true);
        int oldRowHeight = myTable.getRowHeight(myResizingRow);
        int newRowHeight = Math.max(myTable.getRowHeight(), e.getY() - cellRect.y);
        if (oldRowHeight != newRowHeight) {
          myTable.setRowHeight(myResizingRow, newRowHeight);
        }
      }
      else {
        processSelectionEvent(e, true);
      }
    }

    private int getRowToResize(MouseEvent e) {
      int rowAtPoint = rowForPoint(e.getPoint());
      if (rowAtPoint == -1) return -1;

      // resize area is 6px
      Rectangle cellRect = myTable.getCellRect(rowAtPoint, 0, true);
      int yInCellRect = e.getY() - cellRect.y;
      return yInCellRect < 4 ? (rowAtPoint == 0 ? -1 : rowAtPoint - 1) :
             yInCellRect > cellRect.height - 4 ? rowAtPoint : -1;
    }

    private void processSelectionEvent(MouseEvent e, boolean isDragEvent) {
      int currentRow = rowForPoint(e.getPoint());
      if (currentRow == -1) {
        e.consume();
        return;
      }
      if (!myTable.hasFocus()) IdeFocusManager.getInstance(myResultPanel.getProject()).requestFocus(myTable, true);
      boolean selected = processSelectionEventInternal(e, isDragEvent, currentRow);
      myResultPanel.getHiddenColumnSelectionHolder().setWholeRowSelected(!isTransposed() && selected);
      e.consume();
    }

    private boolean processSelectionEventInternal(MouseEvent e, boolean isDragEvent, int currentRow) {
      boolean interval = GridUtil.isIntervalModifierSet(e);
      boolean exclusive = GridUtil.isExclusiveModifierSet(e);
      TableSelectionModel selectionModel = ObjectUtils.tryCast(SelectionModelUtil.get(myResultPanel, myTable), TableSelectionModel.class);
      if (selectionModel == null) return false;
      if (interval) {
        int lead = myTable.getSelectionModel().getLeadSelectionIndex();
        if (exclusive) {
          selectionModel.addRowSelectionInterval(currentRow, lead);
          selectionModel.addColumnSelectionInterval(myTable.getColumnCount() - 1, 0);
        }
        else {
          selectionModel.setRowSelectionInterval(currentRow, lead);
          selectionModel.setColumnSelectionInterval(myTable.getColumnCount() - 1, 0);
        }
        return true;
      }
      else if (exclusive) {
        if (!isDragEvent) {
          mySelectWhileDraggingInExclusiveMode = !myTable.isRowSelected(currentRow) ||
                                                 myTable.getSelectedColumnCount() != myTable.getColumnCount();
        }
        if (mySelectWhileDraggingInExclusiveMode) {
          selectionModel.addRowSelection(ModelIndexSet.forRows(myResultPanel, currentRow));
          selectionModel.addColumnSelectionInterval(myTable.getColumnCount() - 1, 0);
        }
        else {
          myTable.removeRowSelectionInterval(currentRow, currentRow);
        }
        return mySelectWhileDraggingInExclusiveMode;
      }
      else {
        int lead = isDragEvent ? myTable.getSelectionModel().getLeadSelectionIndex() : currentRow;
        selectionModel.setRowSelectionInterval(currentRow, lead);
        selectionModel.setColumnSelectionInterval(myTable.getColumnCount() - 1, 0);
        return true;
      }
    }

    private void toggleColumnSorting(MouseEvent e) {
      if (!e.isConsumed() && isTransposed() && myResultPanel.isSortViaOrderBy()) {
        ViewIndex<GridColumn> viewIndex = ViewIndex.forColumn(myResultPanel, rowForPoint(e.getPoint()));
        ModelIndex<GridColumn> idx = viewIndex.toModel(myResultPanel);
        myResultPanel.toggleSortColumns(Collections.singletonList(idx), false);
        e.consume();
      }
    }

    private void packRow(MouseEvent e) {
      int rowToResize = getRowToResize(e);
      if (e.isConsumed() || rowToResize == -1) return;

      int expandedHeight = getExpandedRowHeight(rowToResize);
      int newHeight = myTable.getRowHeight(rowToResize) >= expandedHeight ? myTable.getRowHeight() : expandedHeight;

      myTable.setRowHeight(rowToResize, newHeight);
    }

    private int getExpandedRowHeight(int row) {
      int expandedHeight = myTable.getRowHeight();
      for (int column = 0; column < myTable.getColumnCount(); column++) {
        TableCellRenderer renderer = myTable.getCellRenderer(row, column);
        if (renderer != null) {
          Component c = myTable.prepareRenderer(renderer, row, column);
          expandedHeight = Math.max(expandedHeight, c.getPreferredSize().height);
        }
      }
      return expandedHeight;
    }
  }

  protected abstract class RowHeaderCellComponentBase extends CellRendererPanel {
    private int myRow;

    protected RowHeaderCellComponentBase() {
    }

    public void setRow(int row, boolean forDisplay) {
      myRow = row;
    }

    public int getRow() {
      return myRow;
    }

    @Override
    public boolean isShowing() {
      return true;
    }

    @Override
    protected void paintComponent(Graphics g) {
      paintBackground(g);
    }

    @Override
    public Color getBackground() {
      Color background = myTable.isTransposed() ?
                         myResultPanel.getColorModel().getColumnHeaderBackground(getColumnModelIndex()) :
                         myResultPanel.getColorModel().getRowHeaderBackground(getRowModelIndex());
      if (background != null) {
        return background;
      }
      Color hoveredColor = myTable.getShowHorizontalLines() || myTable.isStriped() ? null : myResultPanel.getHoveredRowBackground();
      Color stripedColor = myTable.isStriped() ? myResultPanel.getStripeRowBackground() : null;
      return hoveredColor != null && myRow == TableHoverListener.getHoveredRow(myTable)
             ? hoveredColor
             : stripedColor != null && myRow % 2 == 0
               ? stripedColor
               : myTable.getBackground();
    }

    @Override
    public Color getForeground() {
      return myTable.isTransposed() ?
             myResultPanel.getColorModel().getColumnHeaderForeground(getColumnModelIndex()) :
             myResultPanel.getColorModel().getRowHeaderForeground(getRowModelIndex());
    }

    private void paintBackground(Graphics g) {
      Color backup = g.getColor();
      g.setColor(getBackground());
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(backup);
    }

    private @NotNull ModelIndex<GridRow> getRowModelIndex() {
      assert !isTransposed();
      return ModelIndex.forRow(myResultPanel, myResultPanel.getRawIndexConverter().row2Model().applyAsInt(myRow));
    }

    private @NotNull ModelIndex<GridColumn> getColumnModelIndex() {
      assert isTransposed();
      return ModelIndex.forColumn(myResultPanel, myResultPanel.getRawIndexConverter().column2Model().applyAsInt(myRow));
    }
  }

  protected class RowNumberRowHeaderCellComponent extends RowHeaderCellComponentBase {

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      paintLineNumber(g);
    }

    @Override
    public Dimension getPreferredSize() {
      int preferredWidth = getPreferredWidth();
      return new Dimension(preferredWidth, myTable.getRowHeight(getRow()));
    }

    private int getPreferredWidth() {
      Insets insets = getInsets();
      return preferredWidthOf(getRowNumberString()) + (insets.right + insets.left);
    }

    private void paintLineNumber(Graphics g) {
      Font fontBackup = g.getFont();
      g.setFont(myTable.getFont());
      Insets insets = getInsets();
      int middleX = insets.left + (getWidth() - insets.left - insets.right) / 2;
      String rowNum = getRowNumberString();
      int stringWidth = g.getFontMetrics().stringWidth(rowNum);
      int baseline = getBaseline();
      g.drawString(rowNum, middleX - stringWidth / 2, baseline);
      FontMetrics metrics = myTable.getFontMetrics(myTable.getFont());
      int height = metrics.getAscent() - metrics.getDescent();
      drawEffects(g, new Rectangle(middleX - stringWidth / 2, baseline - height, stringWidth, height));
      g.setFont(fontBackup);
    }

    private @NotNull String getRowNumberString() {
      return GridUtil.getRowName(myResultPanel, getRow());
    }

    protected void drawEffects(Graphics g, Rectangle r) {
    }

    private int getBaseline() {
      FontMetrics metrics = myTable.getFontMetrics(myTable.getFont());
      return (myTable.getTextLineHeight() + metrics.getAscent() - metrics.getDescent() + JBUI.scale(4)) / 2;
    }

    private int preferredWidthOf(String text) {
      return myTable.getFontMetrics(myTable.getFont()).stringWidth(text);
    }
  }

  protected class ColumnInfoRowHeaderCellComponent extends RowHeaderCellComponentBase {
    private final JLabel myNameLabel = new TableResultView.LabelWithFallbackFont(myTable);
    private final JLabel mySortLabel = new TableResultView.LabelWithFallbackFont(myTable);

    public ColumnInfoRowHeaderCellComponent() {
      setLayout(new BorderLayout());
      myNameLabel.setBorder(IdeBorderFactory.createEmptyBorder(CellRenderingUtils.NAME_LABEL_ROW_INSETS));
      mySortLabel.setBorder(IdeBorderFactory.createEmptyBorder(CellRenderingUtils.SORT_LABEL_ROW_INSETS));
      mySortLabel.setVerticalAlignment(SwingConstants.CENTER);
      add(myNameLabel, BorderLayout.CENTER);
      add(mySortLabel, BorderLayout.EAST);
    }

    @Override
    public void setRow(int viewRowIdx, boolean forDisplay) {
      super.setRow(viewRowIdx, forDisplay);
      assert isTransposed();

      ModelIndex<GridColumn> modelColumnIdx =
        ViewIndex.forColumn(myResultPanel, viewRowIdx).toModel(myResultPanel);
      GridColumn column = myResultPanel.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(modelColumnIdx);
      if (column == null) return;

      Font font = myTable.getTableHeader().getFont();
      Color fg = getForeground();
      myNameLabel.setFont(font);
      mySortLabel.setFont(font);
      myNameLabel.setForeground(fg);
      mySortLabel.setForeground(fg);

      Icon icon = GridHelper.get(myResultPanel).getColumnIcon(myResultPanel, column, forDisplay);
      myNameLabel.setIcon(icon);
      String displayTypeName = isBinary(modelColumnIdx, myResultPanel) ?
                               " (" + myResultPanel.getDisplayType(modelColumnIdx).getName() + ")" :
                               "";
      myNameLabel.setText(myResultPanel.getName(column) + displayTypeName);

      mySortLabel.setText("");
      mySortLabel.setIcon(null);
      if (myResultPanel.isSortViaOrderBy() && myResultPanel.getComparator(modelColumnIdx) != null) {
        int sortOrder = myResultPanel.getSortOrder(column);
        mySortLabel.setIcon(TableResultView.getSortOrderIcon(sortOrder));
        mySortLabel.setText(myResultPanel.countSortedColumns() > 1 ? TableResultView.getSortOrderText(sortOrder) : "");
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      drawEffects(g, TableResultView.getLabelTextRect(myNameLabel));
    }

    protected void drawEffects(Graphics g, Rectangle r) {
    }
  }

  private class MyRowHeaderCellRenderer implements RowHeaderCellRenderer {
    final RowHeaderCellComponentBase myTransposed;
    final RowHeaderCellComponentBase myRegular;

    MyRowHeaderCellRenderer(RowHeaderCellComponentBase transposed,
                                   RowHeaderCellComponentBase regular) {
      myTransposed = transposed;
      myRegular = regular;
    }

    @Override
    public Component getRendererComponent(int row, boolean forDisplay) {
      RowHeaderCellComponentBase c = isTransposed() ? myTransposed : myRegular;
      int li = isLeftSide() ? 1 : 0; //left indicator
      int ri = 1 - li; //right indicator
      c.setBorder(BorderFactory.createCompoundBorder(
        new CustomLineBorder(myTable.getGridColor(), 0, ri, 0, li),
        JBUI.Borders.empty(0, 8)));

      c.setRow(row, forDisplay);
      return c;
    }

    private boolean isLeftSide() {
      TableScrollPane parent = ComponentUtil.getParentOfType((Class<? extends TableScrollPane>)TableScrollPane.class, (Component)myTable);
      return parent == null || !parent.isFlipped();
    }
  }
}
