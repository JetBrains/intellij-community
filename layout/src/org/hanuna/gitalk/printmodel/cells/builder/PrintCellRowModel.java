package org.hanuna.gitalk.printmodel.cells.builder;

import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.printmodel.PrintCellRow;
import org.hanuna.gitalk.printmodel.ShortEdge;
import org.hanuna.gitalk.printmodel.SpecialCell;
import org.hanuna.gitalk.printmodel.cells.LayoutModel;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class PrintCellRowModel {
    private final LayoutModel layoutModel;
    private final PreModelPrintCellRow prePrintCellRow;

    public PrintCellRowModel(LayoutModel layoutModel) {
        this.layoutModel = layoutModel;
        this.prePrintCellRow = new PreModelPrintCellRow(layoutModel);
    }

    private ShortEdge inverseEdge(ShortEdge edge) {
        return new ShortEdge(edge.getEdge(), edge.getDownPosition(), edge.getUpPosition());
    }

    private ReadOnlyList<ShortEdge> getUpEdges(int rowIndex) {
        PreModelPrintCellRow prevPreModel = new PreModelPrintCellRow(layoutModel);
        prevPreModel.prepare(rowIndex - 1);
        return prevPreModel.downShortEdges();
    }


    @NotNull
    public PrintCellRow getPrintCellRow(final int rowIndex) {
        prePrintCellRow.prepare(rowIndex);

        return new PrintCellRow() {
            @Override
            public int countCell() {
                return prePrintCellRow.getCountCells();
            }

            @NotNull
            @Override
            public ReadOnlyList<ShortEdge> getUpEdges() {
                return PrintCellRowModel.this.getUpEdges(rowIndex);
            }

            @NotNull
            @Override
            public ReadOnlyList<ShortEdge> getDownEdges() {
                return prePrintCellRow.downShortEdges();
            }

            @NotNull
            @Override
            public ReadOnlyList<SpecialCell> getSpecialCell() {
                return prePrintCellRow.specialCells();
            }
        };
    }
}
