package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.graph.GraphModel;
import org.hanuna.gitalk.graph.Node;
import org.hanuna.gitalk.printmodel.SpecialCell;
import org.hanuna.gitalk.printmodel.cells.CellModel;
import org.hanuna.gitalk.printmodel.cells.NodeCell;
import org.hanuna.gitalk.printmodel.cells.builder.CellModelBuilder;
import org.hanuna.gitalk.printmodel.cells.builder.PrintCellRowModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;

/**
 * @author erokhins
 */
public class Controller {
    private final GraphModel graphModel;
    private CellModel cellModel;
    private PrintCellRowModel printCellRowModel;

    public Controller(GraphModel graphModel) {
        this.graphModel = graphModel;
    }

    public void prepare() {
        CellModelBuilder cellModelBuilder = new CellModelBuilder(graphModel);
        cellModel = cellModelBuilder.build();
        printCellRowModel = new PrintCellRowModel(cellModel);
    }

    @Nullable
    public Commit getCommitInRow(int rowIndex) {
        ReadOnlyList<SpecialCell> cells = printCellRowModel.getPrintCellRow(rowIndex).getSpecialCell();
        for (SpecialCell cell : cells) {
            if (cell.getType() == SpecialCell.Type.commitNode) {
                assert NodeCell.class == cell.getCell().getClass();
                Node node =  ((NodeCell) cell.getCell()).getNode();
                return node.getCommit();
            }
        }
        return null;
    }

    private class GitAlkTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Subject", "Author", "Date"};

        @Override
        public int getRowCount() {
            return graphModel.getNodeRows().size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Commit commit = getCommitInRow(rowIndex);
            CommitData data;
            if (commit == null) {
                data = null;
            } else {
                data = commit.getData();
                assert data != null;
            }
            switch (columnIndex) {
                case 0:
                    String message = "";
                    if (data != null) {
                        message = data.getMessage();
                    }
                    return new GraphTableCell(printCellRowModel.getPrintCellRow(rowIndex), message);
                case 1:
                    if (data == null) {
                        return "";
                    } else {
                        return data.getAuthor();
                    }
                case 2:
                    if (data == null) {
                        return "";
                    } else {
                        return DateConverter.getStringOfDate(data.getTimeStamp());
                    }
                default:
                    throw new IllegalArgumentException("columnIndex > 2");
            }
        }

        @Override
        public Class<?> getColumnClass(int column) {
            switch (column) {
                case 0:
                    return GraphTableCell.class;
                case 1:
                    return String.class;
                case 2:
                    return String.class;
                default:
                    throw new IllegalArgumentException("column > 2");
            }
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }
    }
}
