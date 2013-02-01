package org.hanuna.gitalk.graph.new_mutable.fragments;

import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

/**
* @author erokhins
*/
public interface UnhiddenNodeFunction {
    public boolean isUnhiddenNode(@NotNull Node node);
}
