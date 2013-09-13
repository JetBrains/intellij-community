package org.hanuna.gitalk.graph.mutable;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.NullVirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsLogLogger;
import org.hanuna.gitalk.graph.elements.Branch;
import org.hanuna.gitalk.graph.mutable.elements.MutableNode;
import org.hanuna.gitalk.graph.mutable.elements.MutableNodeRow;
import org.hanuna.gitalk.graph.mutable.elements.UsualEdge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.hanuna.gitalk.graph.elements.Node.NodeType.*;

/**
 * @author erokhins
 */
public class GraphBuilder {

  public static MutableGraph build(@NotNull List<? extends CommitParents> commitParentses, Collection<VcsRef> allRefs) {
    Map<Hash, Integer> commitLogIndexes = new HashMap<Hash, Integer>(commitParentses.size());
    for (int i = 0; i < commitParentses.size(); i++) {
      commitLogIndexes.put(commitParentses.get(i).getHash(), i);
    }
    GraphBuilder builder = new GraphBuilder(commitParentses.size() - 1, commitLogIndexes, allRefs);
    return builder.runBuild(commitParentses);
  }

  public static void addCommitsToGraph(@NotNull MutableGraph graph, @NotNull List<? extends CommitParents> commitParentses,
                                       @NotNull Collection<VcsRef> allRefs) {
    new GraphAppendBuilder(graph, allRefs).appendToGraph(commitParentses);
  }

  // local package
  static void createUsualEdge(@NotNull MutableNode up, @NotNull MutableNode down, @NotNull Branch branch) {
    UsualEdge edge = new UsualEdge(up, down, branch);
    up.getInnerDownEdges().add(edge);
    down.getInnerUpEdges().add(edge);
  }

  private final int lastLogIndex;
  private final MutableGraph graph;
  private final Map<Hash, MutableNode> underdoneNodes;
  private Map<Hash, Integer> commitHashLogIndexes;
  private Collection<VcsRef> myRefs;

  private MutableNodeRow nextRow;

  public GraphBuilder(int lastLogIndex,
                      Map<Hash, Integer> commitHashLogIndexes,
                      MutableGraph graph,
                      Map<Hash, MutableNode> underdoneNodes,
                      MutableNodeRow nextRow, Collection<VcsRef> refs) {
    this.lastLogIndex = lastLogIndex;
    this.commitHashLogIndexes = commitHashLogIndexes;
    this.graph = graph;
    this.underdoneNodes = underdoneNodes;
    this.nextRow = nextRow;
    myRefs = refs;
  }

  public GraphBuilder(int lastLogIndex, Map<Hash, Integer> commitHashLogIndexes, MutableGraph graph, Collection<VcsRef> refs) {
    this(lastLogIndex, commitHashLogIndexes, graph, new HashMap<Hash, MutableNode>(), new MutableNodeRow(graph, 0), refs);
  }

  public GraphBuilder(int lastLogIndex, Map<Hash, Integer> commitHashLogIndexes, Collection<VcsRef> refs) {
    this(lastLogIndex, commitHashLogIndexes, new MutableGraph(), refs);
  }


  private int getLogIndexOfCommit(@NotNull Hash commitHash) {
    Integer index = commitHashLogIndexes.get(commitHash);
    if (index == null) {
      return lastLogIndex + 1;
    }
    else {
      return index;
    }
  }

  @Nullable
  public VcsRef findRefForHash(@NotNull final Hash hash) {
    return ContainerUtil.find(myRefs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return ref.getCommitHash().equals(hash);
      }
    });
  }

  private MutableNode addCurrentCommitAndFinishRow(@NotNull Hash commitHash) {
    MutableNode node = underdoneNodes.remove(commitHash);
    if (node == null) {
      VcsRef ref = findRefForHash(commitHash);
      VirtualFile repositoryRoot;
      if (ref == null) {
        // should never happen, but fallback gently.
        VcsLogLogger.LOG.error("Ref should exist for this node. Hash: " + commitHash);
        repositoryRoot = NullVirtualFile.INSTANCE;
      }
      else {
        repositoryRoot = ref.getRoot();
      }
      node = createNode(commitHash, new Branch(commitHash, ref, repositoryRoot));
    }
    node.setType(COMMIT_NODE);
    node.setNodeRow(nextRow);

    nextRow.getInnerNodeList().add(node);
    graph.getAllRows().add(nextRow);
    nextRow = new MutableNodeRow(graph, nextRow.getRowIndex() + 1);
    return node;
  }

  private void addParent(MutableNode node, Hash parentHash, Branch branch) {
    MutableNode parentNode = underdoneNodes.remove(parentHash);
    if (parentNode == null) {
      parentNode = createNode(parentHash, branch);
      createUsualEdge(node, parentNode, branch);
      underdoneNodes.put(parentHash, parentNode);
    }
    else {
      createUsualEdge(node, parentNode, branch);
      int parentRowIndex = getLogIndexOfCommit(parentHash);

      // i.e. we need of create EDGE_NODE node
      if (nextRow.getRowIndex() != parentRowIndex) {
        parentNode.setNodeRow(nextRow);
        parentNode.setType(EDGE_NODE);
        nextRow.getInnerNodeList().add(parentNode);

        MutableNode newParentNode = createNode(parentHash, parentNode.getBranch());
        createUsualEdge(parentNode, newParentNode, parentNode.getBranch());
        underdoneNodes.put(parentHash, newParentNode);
      }
      else {
        // i.e. node must be added in nextRow, when addCurrentCommitAndFinishRow() will called in next time
        underdoneNodes.put(parentHash, parentNode);
      }
    }
  }

  private MutableNode createNode(Hash hash, Branch branch) {
    return new MutableNode(branch, hash);
  }

  private void append(@NotNull CommitParents commitParents) {
    MutableNode node = addCurrentCommitAndFinishRow(commitParents.getHash());

    List<Hash> parents = commitParents.getParents();
    Branch branch = node.getBranch();
    if (parents.size() == 1) {
      addParent(node, parents.get(0), branch);
    }
    else {
      for (Hash parentHash : parents) {
        VcsRef ref = findRefForHash(node.getCommitHash());
        addParent(node, parentHash, new Branch(node.getCommitHash(), parentHash, ref, branch.getRepositoryRoot()));
      }
    }
  }


  private void lastActions() {
    Set<Hash> notReadiedCommitHashes = underdoneNodes.keySet();
    for (Hash hash : notReadiedCommitHashes) {
      MutableNode underdoneNode = underdoneNodes.get(hash);
      underdoneNode.setNodeRow(nextRow);
      underdoneNode.setType(END_COMMIT_NODE);
      nextRow.getInnerNodeList().add(underdoneNode);
    }
    if (!nextRow.getInnerNodeList().isEmpty()) {
      graph.getAllRows().add(nextRow);
    }
  }

  // local package
  @NotNull
  MutableGraph runBuild(@NotNull List<? extends CommitParents> commitParentses) {
    if (commitParentses.size() == 0) {
      throw new IllegalArgumentException("Empty list commitParentses");
    }
    for (CommitParents commitParents : commitParentses) {
      append(commitParents);
    }
    lastActions();
    graph.updateVisibleRows();
    return graph;
  }

}
