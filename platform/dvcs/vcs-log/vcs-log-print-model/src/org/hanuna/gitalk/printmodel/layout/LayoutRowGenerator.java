package org.hanuna.gitalk.printmodel.layout;

import org.hanuna.gitalk.common.compressedlist.generator.AbstractGenerator;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
class LayoutRowGenerator extends AbstractGenerator<LayoutRow, MutableLayoutRow> {
  private final Graph graph;

  public LayoutRowGenerator(@NotNull Graph graph) {
    this.graph = graph;
  }

  @NotNull
  @Override
  protected MutableLayoutRow createMutable(@NotNull LayoutRow cellRow) {
    return new MutableLayoutRow(cellRow);
  }

  @NotNull
  private List<Edge> orderAddEdges(@NotNull List<Edge> edges) {
    if (edges.size() <= 1) {
      return edges;
    }
    else {
      List<Edge> sortEdges = new ArrayList<Edge>(edges);
      Collections.sort(sortEdges, new Comparator<Edge>() {
        @Override
        public int compare(Edge o1, Edge o2) {
          if (o1.getDownNode().getRowIndex() > o2.getDownNode().getRowIndex()) {
            return -1;
          }
          else {
            return 1;
          }
        }
      });
      return sortEdges;
    }
  }

  @NotNull
  @Override
  protected MutableLayoutRow oneStep(@NotNull MutableLayoutRow row) {
    int newRowIndex = row.getGraphNodeRow().getRowIndex() + 1;
    if (newRowIndex == graph.getNodeRows().size()) {
      throw new NoSuchElementException();
    }
    List<GraphElement> layoutRow = row.getModifiableOrderedGraphElements();
    Set<Node> addedNodeInNextRow = new HashSet<Node>();
    for (ListIterator<GraphElement> iterator = layoutRow.listIterator(); iterator.hasNext(); ) {
      GraphElement element = iterator.next();
      Node node = element.getNode();
      if (node != null) {
        List<Edge> edges = node.getDownEdges();
        if (edges.size() == 0) {
          iterator.remove();
        }
        else {
          iterator.remove();
          for (Edge edge : orderAddEdges(edges)) {
            Node downNode = edge.getDownNode();
            if (downNode.getRowIndex() == newRowIndex) {
              if (!addedNodeInNextRow.contains(downNode)) {
                iterator.add(downNode);
                addedNodeInNextRow.add(downNode);
              }
            }
            else {
              iterator.add(edge);
            }
          }
        }
      }
      else {
        Edge edge = element.getEdge();
        if (edge == null) {
          throw new IllegalStateException("unexpected element class");
        }
        if (edge.getDownNode().getRowIndex() == newRowIndex) {
          if (!addedNodeInNextRow.contains(edge.getDownNode())) {
            iterator.set(edge.getDownNode());
            addedNodeInNextRow.add(edge.getDownNode());
          }
          else {
            iterator.remove();
          }
        }
      }
    }
    NodeRow nextGraphRow = graph.getNodeRows().get(newRowIndex);
    for (Node node : nextGraphRow.getNodes()) {
      if (node.getUpEdges().isEmpty()) {
        layoutRow.add(node);
      }
    }
    row.setNodeRow(nextGraphRow);
    return row;
  }

  @NotNull
  @Override
  public LayoutRow generateFirst() {
    List<NodeRow> rows = graph.getNodeRows();
    assert !rows.isEmpty();

    NodeRow firstRow = rows.get(0);
    MutableLayoutRow firstCellRow = new MutableLayoutRow();
    firstCellRow.setNodeRow(firstRow);
    List<GraphElement> editableLayoutRow = firstCellRow.getModifiableOrderedGraphElements();
    for (Node node : firstRow.getNodes()) {
      editableLayoutRow.add(node);
    }
    return firstCellRow;
  }
}
