package org.hanuna.gitalk.graph.mutable_graph;

import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.GraphFragmentController;
import org.hanuna.gitalk.graph.graph_elements.NodeRow;
import org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl.MutableNodeRow;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableGraph implements Graph {
    private final List<MutableNodeRow> allRows;
    private final List<MutableNodeRow> visibleRows;
    private final GraphFragmentController fragmentController;
    private final UpdateListenerController listenerController = new UpdateListenerController();

    MutableGraph(List<MutableNodeRow> allRows, @NotNull RefsModel refsModel) {
        if (allRows.size() == 0) {
            throw new IllegalArgumentException("Empty list Rows");
        }
        this.allRows = allRows;
        this.visibleRows = new ArrayList<MutableNodeRow>(allRows);
        recalculateRowIndex();
        fragmentController = new SimpleGraphFragmentController(this, refsModel);
    }

    @NotNull
    public Replace fixRowVisibility(int fromRowIndex, int toRowIndex) {
        if (fromRowIndex < 0 || toRowIndex >= allRows.size() || fromRowIndex > toRowIndex) {
            throw new IllegalArgumentException("fromRowIndex: " + fromRowIndex + "toRowIndex: " + toRowIndex);
        }
        MutableNodeRow upRow = visibleRows.get(fromRowIndex);
        MutableNodeRow downRow = visibleRows.get(toRowIndex);
        for (int i = upRow.getLogIndex(); i <= downRow.getLogIndex(); i++) {
            allRows.get(i).updateVisibleNodes();
        }
        recalculateRowIndex();
        Replace replace = Replace.buildFromToInterval(fromRowIndex, toRowIndex, upRow.getRowIndex(), downRow.getRowIndex());
        listenerController.runDoReplace(replace);
        return replace;
    }

    private void recalculateRowIndex() {
        visibleRows.clear();
        int rowIndex = 0;
        for (MutableNodeRow row : allRows) {
            if (row.hasVisibleNodes()) {
                row.setRowIndex(rowIndex);
                rowIndex++;
                visibleRows.add(row);
            }
        }
    }

    @NotNull
    @Override
    public List<NodeRow> getNodeRows() {
        return Collections.<NodeRow>unmodifiableList(visibleRows);
    }

    @NotNull
    @Override
    public GraphFragmentController getFragmentController() {
        return fragmentController;
    }

    @Override
    public void addUpdateListener(@NotNull GraphUpdateListener updateListener) {
        listenerController.addUpdateListener(updateListener);
    }

    @Override
    public void removeAllListeners() {
        listenerController.removeAllListeners();
    }

    private static class UpdateListenerController {
        private final List<GraphUpdateListener> listeners = new ArrayList<GraphUpdateListener>();

        public void addUpdateListener(@NotNull GraphUpdateListener updateListener) {
            listeners.add(updateListener);
        }

        public void runDoReplace(@NotNull Replace replace) {
            for (GraphUpdateListener updateListener : listeners) {
                updateListener.doReplace(replace);
            }
        }

        public void removeAllListeners() {
            listeners.clear();
        }
    }

}
