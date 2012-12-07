package org.hanuna.gitalk.printmodel.cells.builder;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.hanuna.gitalk.printmodel.cells.Cell;
import org.hanuna.gitalk.printmodel.cells.CellRow;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableCellRow implements CellRow {
    private final List<Cell> cells;
    private NodeRow row;

    public MutableCellRow() {
        cells = new LinkedList<Cell>();
    }

    public MutableCellRow(CellRow cellRow) {
        this.cells = new LinkedList<Cell>(cellRow.getCells());
        this.row = cellRow.getGraphRow();
    }

    public List<Cell> getEditableCells() {
        return cells;
    }

    public void setRow(NodeRow row) {
        this.row = row;
    }

    @NotNull
    @Override
    public ReadOnlyList<Cell> getCells() {
        return ReadOnlyList.newReadOnlyList(cells);
    }

    @Override
    public NodeRow getGraphRow() {
        return row;
    }
}
