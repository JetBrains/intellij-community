package org.hanuna.gitalk.data;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import org.hanuna.gitalk.common.compressedlist.VcsLogLogger;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The DataGetter realizes the following pattern of getting some data (parametrized by {@code T}) from the VCS:
 * <ul>
 *   <li>it tries to get it from the cache;</li>
 *   <li>if it fails, it tries to get it from the VCS, and additionally loads several commits around the requested one,
 *       to avoid querying the VCS if user investigates details of nearby commits.</li>
 * </ul>
 *
 * @author Kirill Likhodedov
 */
public abstract class DataGetter<T extends CommitParents> {

  private static final Logger LOG = VcsLogLogger.LOG;
  private static final int UP_PRELOAD_COUNT = 20;
  private static final int DOWN_PRELOAD_COUNT = 40;

  @NotNull protected final VcsLogDataHolder myDataHolder;
  @NotNull protected final VcsLogProvider myLogProvider;
  @NotNull protected final VirtualFile myRoot;
  @NotNull private final VcsCommitCache<T> myCache;

  DataGetter(@NotNull VcsLogDataHolder dataHolder, @NotNull VcsLogProvider logProvider, @NotNull VirtualFile root,
             @NotNull VcsCommitCache<T> cache) {
    myDataHolder = dataHolder;
    myLogProvider = logProvider;
    myRoot = root;
    myCache = cache;
  }

  @NotNull
  public T getCommitData(@NotNull Hash commitHash) throws VcsException {
    Node node = myDataHolder.getDataPack().getNodeByHash(commitHash);
    if (node != null) {
      return getCommitData(node);
    }
    // TODO this can happen if an old hash was requested before the whole log was loaded
    LOG.error("Node for hash " + commitHash + " was not found");
    return myCache.get(commitHash);
  }

  @NotNull
  public T getCommitData(@NotNull Node node) throws VcsException {
    Hash hash = node.getCommitHash();
    if (!myCache.isKeyCached(hash)) {
      runLoadAroundCommitData(node);
    }
    return myCache.get(hash);
  }

  @Nullable
  private Node getCommitNodeInRow(int rowIndex) {
    Graph graph = myDataHolder.getDataPack().getGraphModel().getGraph();
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

  private void runLoadAroundCommitData(@NotNull Node node) throws VcsException {
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

  private void preLoadCommitData(@NotNull List<Node> nodes) throws VcsException {
    List<String> hashes = ContainerUtil.map(nodes, new Function<Node, String>() {
      @Override
      public String fun(Node node) {
        return node.getCommitHash().toStrHash();
      }
    });

    List<? extends T> details = readDetails(hashes);
    saveInCache(details);
  }

  public void saveInCache(List<? extends T> details) {
    for (T data : details) {
      myCache.put(data.getHash(), data);
    }
  }

  protected abstract List<? extends T> readDetails(List<String> hashes) throws VcsException;

}
