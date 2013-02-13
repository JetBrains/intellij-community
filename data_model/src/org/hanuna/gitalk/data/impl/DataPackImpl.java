package org.hanuna.gitalk.data.impl;

import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.log.commit.CommitParents;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.refs.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class DataPackImpl implements DataPack {

    public DataPackImpl(@NotNull List<CommitParents> commitParentsList, @NotNull Executor<String> statusUpdater) {

    }

    public void appendCommits(@NotNull List<CommitParents> commitParentsList, @NotNull Executor<String> statusUpdater) {

    }

    @NotNull
    @Override
    public RefsModel getRefModel() {
        return null;
    }

    @NotNull
    @Override
    public GraphModel getGraphModel() {
        return null;
    }

    @NotNull
    @Override
    public GraphPrintCellModel getPrintCellModel() {
        return null;
    }
}
