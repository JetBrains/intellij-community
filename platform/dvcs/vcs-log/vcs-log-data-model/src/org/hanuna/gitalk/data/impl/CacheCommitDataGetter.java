package org.hanuna.gitalk.data.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitData;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.data.CommitDataGetter;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.log.commit.parents.FakeCommitParents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class CacheCommitDataGetter implements CommitDataGetter {
  private static final int UP_PRELOAD_COUNT = 20;
  private static final int DOWN_PRELOAD_COUNT = 40;

  private final DataPack dataPack;

  private final CacheGet<Hash, CommitData> cache;
  private final VcsLogProvider myLogProvider;
  private Project myProject;
  private VirtualFile myRoot;

  public CacheCommitDataGetter(Project project, DataPack dataPack, CacheGet<Hash, CommitData> commitDataCache,
                               @NotNull VcsLogProvider logProvider, VirtualFile root) {
    myProject = project;
    this.dataPack = dataPack;
    cache = commitDataCache;
    myLogProvider = logProvider;
    myRoot = root;
  }

  @NotNull
  @Override
  public CommitData getCommitData(@NotNull Node node) {
    Hash hash = node.getCommitHash();
    if (FakeCommitParents.isFake(hash)) {
      Hash originalHash = FakeCommitParents.getOriginal(hash);
      CommitData originalData = getCommitData(originalHash);
      return new CommitData(originalData.getFullCommit(), hash);
    }
    if (!cache.containsKey(hash)) {
      runLoadAroundCommitData(node);
    }
    return cache.get(hash);
  }

  @NotNull
  @Override
  public CommitData getCommitData(@NotNull Hash commitHash) {
    return cache.get(commitHash);
  }

  @Nullable
  private Node getCommitNodeInRow(int rowIndex) {
    Graph graph = dataPack.getGraphModel().getGraph();
    if (rowIndex < 0 || rowIndex >= graph.getNodeRows().size()) {
      return null;
    }
    NodeRow row = graph.getNodeRows().get(rowIndex);
    for (Node node : row.getNodes()) {
      if (node.getType() == Node.NodeType.COMMIT_NODE) {
        return node;
      }
    }
    return null;
  }

  private void runLoadAroundCommitData(@NotNull Node node) {
    int rowIndex = node.getRowIndex();
    List<Node> nodes = new ArrayList<Node>();
    for (int i = rowIndex - UP_PRELOAD_COUNT; i < rowIndex + DOWN_PRELOAD_COUNT; i++) {
      Node commitNode = getCommitNodeInRow(i);
      if (commitNode != null) {
        nodes.add(commitNode);
      }
    }
    preLoadCommitData(nodes);
  }

  private void preLoadCommitData(@NotNull List<Node> nodes) {
    List<String> hashes = ContainerUtil.map(nodes, new Function<Node, String>() {
      @Override
      public String fun(Node node) {
        return FakeCommitParents.getOriginal(node.getCommitHash()).toStrHash();
      }
    });
    List<CommitData> commitDataList = myLogProvider.readCommitsData(myRoot, hashes);

    for (CommitData commitData : commitDataList) {
      cache.addToCache(commitData.getCommitHash(), commitData);
    }
  }


  public void initiallyPreloadCommitDetails() {
    List<Node> nodes = new ArrayList<Node>();
    for (int i = 0; i < VcsLogProvider.COMMIT_BLOCK_SIZE; i++) {
      Node commitNode = getCommitNodeInRow(i);
      if (commitNode != null) {
        nodes.add(commitNode);
      }
    }
    preLoadCommitData(nodes);
  }
}
