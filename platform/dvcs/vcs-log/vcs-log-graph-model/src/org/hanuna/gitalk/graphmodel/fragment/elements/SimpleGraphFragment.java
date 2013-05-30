package org.hanuna.gitalk.graphmodel.fragment.elements;

import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author erokhins
 *         <p/>
 *         Conventions:
 *         if IntermediateNodes.isEmpty() then this fragment is hde freagment and
 *         upNode and downNode connected with HIDE_FRAGMENT_EDGE
 */
public class SimpleGraphFragment implements GraphFragment {
  private final Node upNode;
  private final Node downNode;
  private final Collection<Node> intermediateNodes;

  public SimpleGraphFragment(Node upNode, Node downNode, Collection<Node> intermediateNodes) {
    this.upNode = upNode;
    this.downNode = downNode;
    this.intermediateNodes = intermediateNodes;
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
