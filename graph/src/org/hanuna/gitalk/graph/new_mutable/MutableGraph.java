package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.NewGraph;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.graph.new_mutable.elements.MutableNodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableGraph implements NewGraph {
    private final EdgeController edgeController = new EdgeController();
    private final ElementVisibilityController visibilityController = new ElementVisibilityController();
    private final List<MutableNodeRow> allRows = new ArrayList<MutableNodeRow>();
    private final List<Integer> indexVisibleRows = new ArrayList<Integer>();

    private final List<Executor<Replace>> listenerList = new ArrayList<Executor<Replace>>();

    @NotNull
    public EdgeController getEdgeController() {
        return edgeController;
    }

    public void addRow(@NotNull MutableNodeRow row) {
        allRows.add(row);
    }

    @NotNull
    public ElementVisibilityController getVisibilityController() {
        return visibilityController;
    }

    @Override
    public int size() {
        return indexVisibleRows.size();
    }

    @NotNull
    @Override
    public NodeRow getNodeRow(int rowIndex) {
        return allRows.get(indexVisibleRows.get(rowIndex));
    }

    public void updateVisibleRows() {
        indexVisibleRows.clear();
        for (int  i = 0; i < allRows.size(); i++) {
            MutableNodeRow row = allRows.get(i);
            if (row.hasVisibleNodes()) {
                row.setRowIndex(indexVisibleRows.size());
                indexVisibleRows.add(i);
            }
        }
    }

    public void runUpdate(@NotNull Replace replace) {
        for (Executor<Replace> listener : listenerList) {
            listener.execute(replace);
        }
    }

    @Override
    public void addUpdateListener(@NotNull Executor<Replace> listener) {
        listenerList.add(listener);
    }

    @Override
    public void removeAllListeners() {
        listenerList.clear();
    }
}
