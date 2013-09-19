package org.hanuna.gitalk.printmodel;

import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author erokhins
 */
public class SelectController {
  private final Set<GraphElement> selectedElements = new HashSet<GraphElement>();

  public void select(@Nullable GraphFragment fragment) {
    deselectAll();
    if (fragment == null) {
      return;
    }
    selectedElements.add(fragment.getUpNode());
    selectedElements.add(fragment.getDownNode());
    for (Edge edge : fragment.getUpNode().getDownEdges()) {
      selectedElements.add(edge);
    }
    selectedElements.addAll(fragment.getIntermediateNodes());
    for (Node node : fragment.getIntermediateNodes()) {
      for (Edge edge : node.getDownEdges()) {
        selectedElements.add(edge);
      }
    }
  }

  public void select(Set<GraphElement> selectedElements) {
    this.selectedElements.addAll(selectedElements);
  }

  public void deselectAll() {
    selectedElements.clear();
  }

  public boolean isSelected(@NotNull GraphElement element) {
    return selectedElements.contains(element);
  }


}
