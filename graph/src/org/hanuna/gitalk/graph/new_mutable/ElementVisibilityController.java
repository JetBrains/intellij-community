package org.hanuna.gitalk.graph.new_mutable;

import org.hanuna.gitalk.graph.elements.GraphElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class ElementVisibilityController {
    private final Set<GraphElement> hiddenElements = new HashSet<GraphElement>();

    public boolean isVisible(@NotNull GraphElement graphElement) {
        return !hiddenElements.contains(graphElement);
    }

    public void hide(@NotNull Collection<GraphElement> graphElements) {
        hiddenElements.addAll(graphElements);
    }

    public void show(@NotNull Collection<GraphElement> graphElements) {
        hiddenElements.removeAll(graphElements);
    }
}
