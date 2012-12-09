package org.hanuna.gitalk.printmodel.cells.builder;

import org.hanuna.gitalk.common.compressedlist.CompressedList;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.common.compressedlist.RuntimeGenerateCompressedList;
import org.hanuna.gitalk.common.compressedlist.generator.Generator;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.hanuna.gitalk.printmodel.cells.Cell;
import org.hanuna.gitalk.printmodel.cells.LayoutModel;
import org.hanuna.gitalk.printmodel.cells.CellRow;
import org.hanuna.gitalk.printmodel.cells.NodeCell;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class LayoutModelBuilder {
    private final Graph graph;
    private CompressedList<CellRow> generateModel;
    private final Generator<CellRow> generator;


    public LayoutModelBuilder(Graph graph) {
        this.graph = graph;
        this.generator = new CellRowGenerator(graph);
    }

    public LayoutModel build() {
        ReadOnlyList<NodeRow> rows = graph.getNodeRows();
        assert ! rows.isEmpty();

        NodeRow firstRow = rows.get(0);
        MutableCellRow firstCellRow = new MutableCellRow();
        firstCellRow.setRow(firstRow);
        List<Cell> cells = firstCellRow.getEditableCells();
        for (Node node : firstRow.getVisibleNodes()) {
            cells.add(new NodeCell(node));
        }

        generateModel = new RuntimeGenerateCompressedList<CellRow>(generator, firstCellRow, rows.size());
        return new LayoutModelImpl();
    }

    private class LayoutModelImpl implements LayoutModel {

        @NotNull
        @Override
        public ReadOnlyList<CellRow> getCellRows() {
            return generateModel.getList();
        }

        @Override
        public void update(@NotNull Replace replace) {
            generateModel.recalculate(replace);
        }
    }

}
