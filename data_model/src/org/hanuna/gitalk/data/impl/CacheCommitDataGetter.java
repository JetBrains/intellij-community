package org.hanuna.gitalk.data.impl;

import org.hanuna.gitalk.commit.Hash;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.Get;
import org.hanuna.gitalk.data.CommitDataGetter;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.git.reader.CommitDataReader;
import org.hanuna.gitalk.graph.Graph;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.elements.NodeRow;
import org.hanuna.gitalk.log.commit.CommitData;
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


    private final CacheGet<Hash, CommitData> cache = new CacheGet<Hash, CommitData>(new Get<Hash, CommitData>() {
        @NotNull
        @Override
        public CommitData get(@NotNull Hash key) {
            return readCommitData(key);
        }
    }, 5000);
    private final CommitDataReader commitDataReader = new CommitDataReader();
    private final DataPack dataPack;

    public CacheCommitDataGetter(DataPack dataPack) {
        this.dataPack = dataPack;
    }

    @NotNull
    @Override
    public CommitData getCommitData(@NotNull Node node) {
        if (!cache.containsKey(node.getCommitHash())) {
            runLoadAroundCommitData(node);
        }
        return cache.get(node.getCommitHash());
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
        StringBuilder s = new StringBuilder();
        for (Node node : nodes) {
            s.append(node.getCommitHash().toStrHash()).append(" ");
        }
        List<CommitData> commitDataList = commitDataReader.readCommitsData(s.toString());

        for (CommitData commitData : commitDataList) {
            cache.addToCache(commitData.getCommitHash(), commitData);
        }
    }


    @NotNull
    private CommitData readCommitData(@NotNull Hash hash) {
        return commitDataReader.readCommitData(hash.toStrHash());
    }
}
