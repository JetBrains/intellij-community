package org.hanuna.gitalk.graphmodel;

import com.intellij.util.Function;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author erokhins
 */
public interface FragmentManager {

  @NotNull
  public Set<Node> allCommitsCurrentBranch(@NotNull GraphElement graphElement);

  public Set<Node> getUpNodes(@NotNull GraphElement graphElement);

  @Nullable
  public GraphFragment relateFragment(@NotNull GraphElement graphElement);

  @NotNull
  public UpdateRequest changeVisibility(@NotNull GraphFragment fragment);

  //true, if node is unconcealedNode
  public void setUnconcealedNodeFunction(@NotNull Function<Node, Boolean> isUnconcealedNode);

  void hideAll();

  void showAll();

  @NotNull
  public GraphPreDecorator getGraphPreDecorator();

  public interface GraphPreDecorator {
    public boolean isVisibleNode(@NotNull Node node);

    @Nullable
    public Edge getHideFragmentUpEdge(@NotNull Node node);

    @Nullable
    public Edge getHideFragmentDownEdge(@NotNull Node node);
  }
}
