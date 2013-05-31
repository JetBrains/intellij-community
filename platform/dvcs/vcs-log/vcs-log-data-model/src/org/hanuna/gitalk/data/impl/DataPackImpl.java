package org.hanuna.gitalk.data.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.CommitData;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLogProvider;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.Executor;
import org.hanuna.gitalk.common.Function;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.data.CommitDataGetter;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphBuilder;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.graphmodel.impl.GraphModelImpl;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.printmodel.impl.GraphPrintCellModelImpl;
import com.intellij.vcs.log.Ref;
import org.hanuna.gitalk.data.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class DataPackImpl implements DataPack {
  public static DataPackImpl buildDataPack(@NotNull List<CommitParents> commitParentsList,
                                           @NotNull List<Ref> allRefs,
                                           Consumer<String> statusUpdater,
                                           Project project,
                                           CacheGet<Hash, CommitData> commitDataCache,
                                           @NotNull VcsLogProvider logProvider, VirtualFile root) {
    statusUpdater.consume("Build graph");

    MyTimer timer = new MyTimer("graph build");
    MutableGraph graph = GraphBuilder.build(commitParentsList, allRefs);
    timer.print();

    timer.clear("graphModel build");
    GraphModel graphModel = new GraphModelImpl(graph, allRefs);
    timer.print();

    statusUpdater.consume("Build print model");
    timer.clear("build printModel");
    final GraphPrintCellModel printCellModel = new GraphPrintCellModelImpl(graphModel.getGraph());
    timer.print();
    graphModel.addUpdateListener(new Executor<UpdateRequest>() {
      @Override
      public void execute(UpdateRequest key) {
        printCellModel.recalculate(key);
      }
    });

    final RefsModel refsModel = new RefsModel(allRefs);
    graphModel.getFragmentManager().setUnconcealedNodeFunction(new Function<Node, Boolean>() {
      @NotNull
      @Override
      public Boolean get(@NotNull Node key) {
        if (key.getDownEdges().isEmpty() || key.getUpEdges().isEmpty() || refsModel.isBranchRef(key.getCommitHash())) {
          return true;
        }
        else {
          return false;
        }
      }
    });
    return new DataPackImpl(graphModel, refsModel, printCellModel, project, commitDataCache, logProvider, root);
  }


  private final GraphModel graphModel;
  private final RefsModel refsModel;
  private final GraphPrintCellModel printCellModel;
  private final CommitDataGetter commitDataGetter;

  private DataPackImpl(GraphModel graphModel, RefsModel refsModel, GraphPrintCellModel printCellModel, Project project,
                       CacheGet<Hash, CommitData> commitDataCache, @NotNull VcsLogProvider logProvider, VirtualFile root) {
    this.graphModel = graphModel;
    this.refsModel = refsModel;
    this.printCellModel = printCellModel;
    commitDataGetter = new CacheCommitDataGetter(project, this, commitDataCache, logProvider, root);
  }

  public void appendCommits(@NotNull List<CommitParents> commitParentsList, @NotNull Consumer<String> statusUpdater) {
    statusUpdater.consume("Rebuild graph and print model");
    MyTimer timer = new MyTimer("append commits");
    graphModel.appendCommitsToGraph(commitParentsList);
    timer.print();
  }

  @Override
  public CommitDataGetter getCommitDataGetter() {
    return commitDataGetter;
  }

  @NotNull
  @Override
  public RefsModel getRefsModel() {
    return refsModel;
  }

  @NotNull
  @Override
  public GraphModel getGraphModel() {
    return graphModel;
  }

  @NotNull
  @Override
  public GraphPrintCellModel getPrintCellModel() {
    return printCellModel;
  }
}
