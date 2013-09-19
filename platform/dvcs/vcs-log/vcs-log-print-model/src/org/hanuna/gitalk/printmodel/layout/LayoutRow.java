package org.hanuna.gitalk.printmodel.layout;

import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface LayoutRow {
  @NotNull
  public List<GraphElement> getOrderedGraphElements();

  public NodeRow getGraphNodeRow();
}
