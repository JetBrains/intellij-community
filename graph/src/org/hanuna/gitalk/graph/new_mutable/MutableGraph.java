package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.compressedlist.Replace;
import org.hanuna.gitalk.graph.NewGraph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.graph.new_mutable.elements.MutableNodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class MutableGraph implements NewGraph {
    private final EdgeController edgeController = new EdgeController();
    private final ElementVisibilityController visibilityController = new ElementVisibilityController();
    private final List<MutableNodeRow> allRows = new ArrayList<MutableNodeRow>();
    private final List<MutableNodeRow> visibleRows = new ArrayList<MutableNodeRow>();

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
    @NotNull
    public List<NodeRow> getNodeRows() {
        return Collections.<NodeRow>unmodifiableList(visibleRows);
    }

    public void updateVisibleRows() {
        visibleRows.clear();
        for (int  i = 0; i < allRows.size(); i++) {
            MutableNodeRow row = allRows.get(i);
            if (row.hasVisibleNodes()) {
                row.setRowIndex(visibleRows.size());
                visibleRows.add(row);
            }
        }
    }

    public void runUpdate(@NotNull Replace replace) {
        for (Executor<Replace> listener : listenerList) {
            listener.execute(replace);
        }
    }

    public Replace intermediateUpdate(@NotNull Node upNode, @NotNull Node downNode) {
        int prevUpIndex = upNode.getRowIndex();
        int prevDownIndex = downNode.getRowIndex();
        updateVisibleRows();
        if (upNode.getRowIndex() != prevUpIndex) {
            throw new IllegalStateException("bad intermediateUpdate: up:" + upNode + " down:" + downNode);
        }
        int newDownIndex = downNode.getRowIndex();
        Replace replace = Replace.buildFromToInterval(prevUpIndex, prevDownIndex, prevUpIndex, newDownIndex);
        runUpdate(replace);
        return replace;
    }

    public void addUpdateListener(@NotNull Executor<Replace> listener) {
        listenerList.add(listener);
    }

    public void removeAllListeners() {
        listenerList.clear();
    }
}
