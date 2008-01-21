package com.intellij.cyclicDependencies;

import com.intellij.util.graph.Graph;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * User: anna
 * Date: Feb 11, 2005
 */
//DijkstraAlgorithm
public class ShortestPathUtil <Node> {
  private HashSet<Node> myVisited;
  private Set<ProcessingNode> myProcessingNodes;

  private DefaultTreeModel myShortestPathTree;

  private Graph<Node> myGraph;

  public ShortestPathUtil(Graph<Node> graph) {
    myVisited = new HashSet<Node>();
    myProcessingNodes = new HashSet<ProcessingNode>();
    myGraph = graph;
  }

  public List<Node> getShortestPath(Node from, Node to) {
    ArrayList<Node> result = new ArrayList<Node>();
    if (myShortestPathTree == null || ((DefaultMutableTreeNode)myShortestPathTree.getRoot()).getUserObject() != from) {
      shortestPath(from);
    }
    final boolean flag = traverse(to, result);
    return flag ? result : null;
  }

  private boolean traverse(Node to, List<Node> path){
    DefaultMutableTreeNode treeNode = findNodeByUserObject(to);
    if (treeNode == null){
      return false;
    }
    while (treeNode != null){
      path.add((Node)treeNode.getUserObject());
      treeNode = (DefaultMutableTreeNode)treeNode.getParent();
    }
    return true;
  }


  private TreeModel shortestPath(Node from) {
    myShortestPathTree = new DefaultTreeModel(new DefaultMutableTreeNode(from));

    myProcessingNodes.add(new ProcessingNode(null, from, 0));

    while (!myProcessingNodes.isEmpty()) {
      moveToVisited();
    }

    myVisited.clear();
    myProcessingNodes.clear();

    return myShortestPathTree;
  }

  private DefaultMutableTreeNode findNodeByUserObject(final Node nodeToFind){
      final ArrayList<DefaultMutableTreeNode> parent = new ArrayList<DefaultMutableTreeNode>();
      TreeUtil.traverseDepth((TreeNode)myShortestPathTree.getRoot(), new TreeUtil.Traverse() {
        public boolean accept(Object node) {
          final DefaultMutableTreeNode treeNode = ((DefaultMutableTreeNode)node);
          if (treeNode.getUserObject() != null && treeNode.getUserObject().equals(nodeToFind)) {
            parent.add(treeNode);
            return false;
          }
          return true;
        }
      });
      return parent.size() > 0 ? parent.get(0) : null;
  }

  private void moveToVisited() {
    ProcessingNode priorityNode = null;
    for (Iterator<ProcessingNode> iterator = myProcessingNodes.iterator(); iterator.hasNext();) {
      ProcessingNode processingNode = iterator.next();
      if (priorityNode != null) {
        if (priorityNode.getPriority() > processingNode.getPriority()) {
          priorityNode = processingNode;
        }
      }
      else {
        priorityNode = processingNode;
      }
    }
    myProcessingNodes.remove(priorityNode);
    myVisited.add(priorityNode.myToNode);
    if (priorityNode.myFromNode != null) {
      final DefaultMutableTreeNode parentNode = findNodeByUserObject(priorityNode.myFromNode);
      if (parentNode != null){
        myShortestPathTree.insertNodeInto(new DefaultMutableTreeNode(priorityNode.myToNode), parentNode, 0);
      }
    }
    moveAdjacentVerticesToProcessing(priorityNode);
  }

  private void moveAdjacentVerticesToProcessing(ProcessingNode priorityNode) {
    Node fromNode = priorityNode.getToNode();
    int priority = priorityNode.getPriority();
    Iterator<Node> iterator = myGraph.getIn(fromNode);
    Node toNode;
    while (iterator.hasNext()) {
      toNode = iterator.next();
      if (myVisited.contains(toNode)){
        continue;
      }
      final ProcessingNode processingNode = getProcessingNodeByFromNode(toNode);
      if (processingNode == null) {
        myProcessingNodes.add(new ProcessingNode(fromNode, toNode, priority + 1));
      }
      else {
        if (processingNode.getPriority() > priority + 1) {
          processingNode.setPriority(priority + 1);
          processingNode.setFromNode(fromNode);
        }
      }
    }
  }

  private ProcessingNode getProcessingNodeByFromNode(Node fromNode) {
    for (Iterator<ProcessingNode> iterator = myProcessingNodes.iterator(); iterator.hasNext();) {
      ProcessingNode processingNode = iterator.next();
      if (processingNode.getFromNode() == fromNode) {
        return processingNode;
      }
    }
    return null;
  }



  private class ProcessingNode {
    private Node myFromNode;
    private Node myToNode;
    private int myPriority;

    public ProcessingNode(final Node fromNode, final Node toNode, final int priority) {
      myFromNode = fromNode;
      myToNode = toNode;
      myPriority = priority;
    }

    public Node getFromNode() {
      return myFromNode;
    }

    public Node getToNode() {
      return myToNode;
    }

    public int getPriority() {
      return myPriority;
    }

    public void setPriority(final int priority) {
      myPriority = priority;
    }

    public void setFromNode(final Node fromNode) {
      myFromNode = fromNode;
    }

    public void setToNode(final Node toNode) {
      myToNode = toNode;
    }
  }

}




