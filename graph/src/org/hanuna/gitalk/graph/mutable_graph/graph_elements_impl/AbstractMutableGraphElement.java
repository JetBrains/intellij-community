package org.hanuna.gitalk.graph.mutable_graph.graph_elements_impl;

import org.hanuna.gitalk.graph.graph_elements.Branch;
import org.hanuna.gitalk.graph.graph_elements.GraphElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public abstract class AbstractMutableGraphElement implements GraphElement {
    protected boolean selected = false;
    protected Branch branch;

    public AbstractMutableGraphElement(Branch branch) {
        this.branch = branch;
    }

    @Override
    public boolean selected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @NotNull
    @Override
    public Branch getBranch() {
        return branch;
    }

}
