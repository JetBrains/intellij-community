package org.hanuna.gitalk.printmodel.cells.builder;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.compressedlist.generator.AbstractGenerator;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.graph.NodeRow;
import org.hanuna.gitalk.printmodel.cells.Cell;
import org.hanuna.gitalk.printmodel.cells.CellRow;
import org.hanuna.gitalk.printmodel.cells.EdgeCell;
import org.hanuna.gitalk.printmodel.cells.NodeCell;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * @author erokhins
 */
public class CellRowGenerator extends AbstractGenerator<MutableCellRow, CellRow> {
    private final Graph graph;

    public CellRowGenerator(@NotNull Graph graph) {
        this.graph = graph;
    }

    @NotNull
    @Override
    protected MutableCellRow createMutable(@NotNull CellRow cellRow) {
        return new MutableCellRow(cellRow);
    }

    @NotNull
    @Override
    protected MutableCellRow oneStep(@NotNull MutableCellRow row) {
        int newRowIndex = row.getGraphRow().getRowIndex() + 1;
        if (newRowIndex == graph.getNodeRows().size()) {
            throw new NoSuchElementException();
        }
        List<Cell> cells = row.getEditableCells();
        for (ListIterator<Cell> iterator = cells.listIterator(); iterator.hasNext(); ) {
            Cell cell = iterator.next();
            if (cell.getClass() == NodeCell.class) {
                Node node = ((NodeCell) cell).getNode();
                ReadOnlyList<Edge> edges = node.getDownEdges();
                if (edges.size() == 0) {
                    iterator.remove();
                } else {
                    iterator.remove();
                    for (Edge edge : edges) {
                        Node downNode = edge.getDownNode();
                        if (downNode.getRowIndex() == newRowIndex) {
                            if (downNode.getBranch() == edge.getBranch()) {
                                iterator.add(new NodeCell(downNode));
                            }
                        } else {
                            iterator.add(new EdgeCell(edge));
                        }
                    }
                }
            } else {
                if (cell.getClass() != EdgeCell.class) {
                    throw new IllegalStateException("unexpected cell class");
                }
                Edge edge = ((EdgeCell) cell).getEdge();
                if (edge.getDownNode().getRowIndex() == newRowIndex) {
                    if (edge.getBranch() == edge.getDownNode().getBranch()) {
                        iterator.set(new NodeCell(edge.getDownNode()));
                    } else {
                        iterator.remove();
                    }
                }
            }
        }
        final NodeRow nextGraphRow = graph.getNodeRows().get(newRowIndex);
        for (Node node : nextGraphRow.getNodes()) {
            if (node.getUpEdges().isEmpty()) {
                cells.add(new NodeCell(node));
            }
        }
        row.setRow(nextGraphRow);
        return row;
    }
}
