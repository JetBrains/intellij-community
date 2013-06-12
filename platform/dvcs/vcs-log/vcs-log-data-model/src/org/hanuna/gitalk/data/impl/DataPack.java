package org.hanuna.gitalk.data.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.vcs.log.*;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.data.CommitDataGetter;
import org.hanuna.gitalk.data.RefsModel;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graph.mutable.GraphBuilder;
import org.hanuna.gitalk.graph.mutable.MutableGraph;
import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.graphmodel.impl.GraphModelImpl;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.hanuna.gitalk.printmodel.impl.GraphPrintCellModelImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author erokhins
 */
public class DataPack {
  public static DataPack buildDataPack(@NotNull List<? extends VcsCommit> commits, @NotNull Collection<Ref> allRefs,
                                           @NotNull ProgressIndicator indicator, Project project,
                                           CacheGet<Hash, VcsCommit> commitDataCache, @NotNull VcsLogProvider logProvider,
                                           VirtualFile root) {
    for (VcsCommit commit : commits) {
      commitDataCache.put(commit.getHash(), commit);
    }
    indicator.setText("Building graph...");

    MutableGraph graph = GraphBuilder.build(commits, allRefs);

    GraphModel graphModel = new GraphModelImpl(graph, allRefs);

    final GraphPrintCellModel printCellModel = new GraphPrintCellModelImpl(graphModel.getGraph());
    graphModel.addUpdateListener(new Consumer<UpdateRequest>() {
      @Override
      public void consume(UpdateRequest key) {
        printCellModel.recalculate(key);
      }
    });

    final RefsModel refsModel = new RefsModel(allRefs);
    graphModel.getFragmentManager().setUnconcealedNodeFunction(new Function<Node, Boolean>() {
      @NotNull
      @Override
      public Boolean fun(@NotNull Node key) {
        if (key.getDownEdges().isEmpty() || key.getUpEdges().isEmpty() || refsModel.isBranchRef(key.getCommitHash())) {
          return true;
        }
        else {
          return false;
        }
      }
    });
    return new DataPack(graphModel, refsModel, printCellModel, project, commitDataCache, logProvider, root);
  }


  private final GraphModel graphModel;
  private final RefsModel refsModel;
  private final GraphPrintCellModel printCellModel;
  private final CommitDataGetter commitDataGetter;

  private DataPack(GraphModel graphModel,
                   RefsModel refsModel,
                   GraphPrintCellModel printCellModel,
                   Project project,
                   CacheGet<Hash, VcsCommit> commitDataCache,
                   @NotNull VcsLogProvider logProvider,
                   VirtualFile root) {
    this.graphModel = graphModel;
    this.refsModel = refsModel;
    this.printCellModel = printCellModel;
    commitDataGetter = new CacheCommitDataGetter(project, this, commitDataCache, logProvider, root);
  }

  public void appendCommits(@NotNull List<? extends CommitParents> commitParentsList) {
    MyTimer timer = new MyTimer("append commits");
    graphModel.appendCommitsToGraph(commitParentsList);
    timer.print();
  }

  public CommitDataGetter getCommitDataGetter() {
    return commitDataGetter;
  }

  @NotNull
  public RefsModel getRefsModel() {
    return refsModel;
  }

  @NotNull
  public GraphModel getGraphModel() {
    return graphModel;
  }

  @NotNull
  public GraphPrintCellModel getPrintCellModel() {
    return printCellModel;
  }
}
