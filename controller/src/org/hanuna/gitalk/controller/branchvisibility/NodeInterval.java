package org.hanuna.gitalk.controller.branchvisibility;

import org.hanuna.gitalk.graph.Node;

/**
 * @author erokhins
 */
public class NodeInterval {
    private final Node up;
    private final Node down;

    public NodeInterval(Node up, Node down) {
        this.up = up;
        this.down = down;
    }

    public Node getUp() {
        return up;
    }

    public Node getDown() {
        return down;
    }
}
