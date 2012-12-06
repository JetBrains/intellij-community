package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.controller.branchvisibility.NodeInterval;
import org.hanuna.gitalk.graph.graph_elements.Edge;
import org.hanuna.gitalk.graph.graph_elements.Node;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public class SelectController {

    @Nullable
    private SelectRequest prevRequest;

    private void setSelectEdge(Edge edge, boolean select) {
        edge.setSelect(select);
        edge.getDownNode().setSelect(select);
        edge.getUpNode().setSelect(select);
    }

    private void setSelectNodeInterval(NodeInterval nodeInterval, boolean select) {
        Node node = nodeInterval.getUp();
        while (node != nodeInterval.getDown()) {
            node.setSelect(select);
            Edge edge = node.getDownEdges().get(0);
            edge.setSelect(select);
            node = edge.getDownNode();
        }
        node.setSelect(select);
    }



    public void selectEdge(Edge edge) {
        prevRequest = new SelectRequest(edge);
        setSelectEdge(edge, true);
    }

    public void selectNodeInterval(NodeInterval interval) {
        prevRequest = new SelectRequest(interval);
        setSelectNodeInterval(interval, true);
    }

    public void clearSelect() {
        if (prevRequest != null) {
            switch (prevRequest.type) {
                case Edge:
                    setSelectEdge(prevRequest.edge, false);
                    break;
                case Interval:
                    setSelectNodeInterval(prevRequest.nodeInterval, false);
                    break;
            }
            prevRequest = null;
        }
    }

    private static class SelectRequest {
        public static enum  Type {
            Edge,
            Interval
        }

        private final Type type;
        private final Edge edge;
        private final NodeInterval nodeInterval;

        public SelectRequest(Edge edge) {
            this.type = Type.Edge;
            this.edge = edge;
            this.nodeInterval = null;
        }

        public SelectRequest(NodeInterval nodeInterval) {
            this.type = Type.Interval;
            this.nodeInterval = nodeInterval;
            this.edge = null;
        }
    }
}
