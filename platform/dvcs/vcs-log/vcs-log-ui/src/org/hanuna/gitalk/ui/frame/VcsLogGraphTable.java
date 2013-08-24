package org.hanuna.gitalk.ui.frame;

import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.printmodel.SpecialPrintElement;
import org.hanuna.gitalk.ui.VcsLogUI;
import org.hanuna.gitalk.ui.render.GraphCommitCellRender;
import org.hanuna.gitalk.ui.render.PositionUtil;
import org.hanuna.gitalk.ui.render.painters.GraphCellPainter;
import org.hanuna.gitalk.ui.render.painters.SimpleGraphCellPainter;
import org.hanuna.gitalk.ui.tables.GraphCommitCell;
import org.hanuna.gitalk.ui.tables.GraphTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hanuna.gitalk.ui.render.PrintParameters.HEIGHT_CELL;

/**
 * @author erokhins
 */
public class VcsLogGraphTable extends JBTable {

  @NotNull private final VcsLogUI myUI;
  @NotNull private final GraphCellPainter myGraphPainter = new SimpleGraphCellPainter();

  public VcsLogGraphTable(@NotNull VcsLogUI UI) {
    super();
    myUI = UI;

    setTableHeader(null);
    setDefaultRenderer(GraphCommitCell.class, new GraphCommitCellRender(myGraphPainter));
    setDefaultRenderer(String.class, new StringCellRenderer());

    setRowHeight(HEIGHT_CELL);
    setShowHorizontalLines(false);
    setIntercellSpacing(new Dimension(0, 0));

    getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        int selectedRow = getSelectedRow();
        if (selectedRow >= 0) {
          myUI.click(selectedRow);
        }
      }
    });

    MouseAdapter mouseAdapter = new MyMouseAdapter();
    addMouseMotionListener(mouseAdapter);
    addMouseListener(mouseAdapter);
  }

  public void setPreferredColumnWidths() {
    getColumnModel().getColumn(0).setPreferredWidth(700);
    getColumnModel().getColumn(1).setMinWidth(90);
    getColumnModel().getColumn(2).setMinWidth(90);
  }

  public void jumpToRow(int rowIndex) {
    scrollRectToVisible(getCellRect(rowIndex, 0, false));
    setRowSelectionInterval(rowIndex, rowIndex);
    scrollRectToVisible(getCellRect(rowIndex, 0, false));
  }

  private class MyMouseAdapter extends MouseAdapter {
    private final Cursor DEFAULT_CURSOR = new Cursor(Cursor.DEFAULT_CURSOR);
    private final Cursor HAND_CURSOR = new Cursor(Cursor.HAND_CURSOR);

    @Nullable
    private GraphPrintCell getGraphPrintCell(MouseEvent e) {
      return PositionUtil.getGraphPrintCell(e, getModel());
    }

    @Nullable
    private GraphElement overCell(MouseEvent e) {
      int y = PositionUtil.getYInsideRow(e);
      int x = e.getX();
      GraphPrintCell row = getGraphPrintCell(e);
      return row != null ? myGraphPainter.mouseOver(row, x, y) : null;
    }

    @Nullable
    private Node arrowToNode(MouseEvent e) {
      int y = PositionUtil.getYInsideRow(e);
      int x = e.getX();
      GraphPrintCell row = getGraphPrintCell(e);
      if (row == null) {
        return null;
      }
      SpecialPrintElement printElement = myGraphPainter.mouseOverArrow(row, x, y);
      if (printElement == null) {
        return null;
      }
      Edge edge = printElement.getGraphElement().getEdge();
      if (edge == null) {
        return null;
      }
      return printElement.getType() == SpecialPrintElement.Type.DOWN_ARROW ? edge.getDownNode() : edge.getUpNode();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
      if (e.getClickCount() == 1) {
        Node jumpToNode = arrowToNode(e);
        if (jumpToNode != null) {
          jumpToRow(jumpToNode.getRowIndex());
        }
        GraphElement graphElement = overCell(e);
        myUI.click(graphElement);
        if (graphElement == null) {
          myUI.click(PositionUtil.getRowIndex(e));
        }
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      Node jumpToNode = arrowToNode(e);
      if (jumpToNode != null) {
        setCursor(HAND_CURSOR);
      }
      else {
        setCursor(DEFAULT_CURSOR);
      }
      myUI.over(overCell(e));
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // Do nothing
    }

    @Override
    public void mouseExited(MouseEvent e) {
      // Do nothing
    }
  }

  public List<Node> getSelectedNodes() {
    int[] selectedRows = getSelectedRows();
    return nodes(selectedRows);
  }

  private List<Node> nodes(int[] selectedRows) {
    List<Node> result = new ArrayList<Node>();
    Arrays.sort(selectedRows);
    for (int rowIndex : selectedRows) {
      Node node = PositionUtil.getNode(PositionUtil.getGraphPrintCell(getModel(), rowIndex));
      if (node != null) {
        result.add(node);
      }
    }
    return result;
  }

  private class StringCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component rendererComponent = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      Object commit = getValueAt(row, GraphTableModel.COMMIT_COLUMN);
      if (commit instanceof GraphCommitCell) {
        if (GraphCommitCellRender.isMarked(commit) && !isSelected) {
          rendererComponent.setBackground(GraphCommitCellRender.MARKED_BACKGROUND);
        }
        else {
          setBackground(isSelected ? table.getSelectionBackground() : JBColor.WHITE);
        }
      }
      return rendererComponent;
    }

  }
}
