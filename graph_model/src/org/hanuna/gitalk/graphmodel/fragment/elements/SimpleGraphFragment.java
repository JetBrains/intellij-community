package org.hanuna.gitalk.graphmodel.fragment.elements;

import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author erokhins
 */
public class SimpleGraphFragment implements GraphFragment {
    private final Node upNode;
    private final Node downNode;
    private final Collection<Node> intermediateNodes;
    private boolean visibility = true;

    public SimpleGraphFragment(Node upNode, Node downNode, Collection<Node> intermediateNodes) {
        this.upNode = upNode;
        this.downNode = downNode;
        this.intermediateNodes = intermediateNodes;
    }

    @Override
    public boolean isVisible() {
        return visibility;
    }

    public void setVisibility(boolean visibility) {
        this.visibility = visibility;
    }

    @Override
    @NotNull
    public Node getUpNode() {
        return upNode;
    }

    @Override
    @NotNull
    public Node getDownNode() {
        return downNode;
    }

    @Override
    @NotNull
    public Collection<Node> getIntermediateNodes() {
        return intermediateNodes;
    }
}
