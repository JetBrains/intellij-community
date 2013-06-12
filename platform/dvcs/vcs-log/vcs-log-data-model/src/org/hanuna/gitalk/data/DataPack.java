package org.hanuna.gitalk.data;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.vcs.log.*;
import org.hanuna.gitalk.common.CacheGet;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
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

  @NotNull private final GraphModel myGraphModel;
  @NotNull private final RefsModel myRefsModel;
  @NotNull private final GraphPrintCellModel myPrintCellModel;
  @NotNull private final CommitDataGetter myCommitDataGetter;

  @NotNull
  public static DataPack build(@NotNull List<? extends VcsCommit> commits, @NotNull Collection<Ref> allRefs,
                               @NotNull ProgressIndicator indicator, @NotNull Project project, CacheGet<Hash, VcsCommit> commitDataCache,
                               @NotNull VcsLogProvider logProvider, @NotNull VirtualFile root) {
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

  private DataPack(@NotNull GraphModel graphModel, @NotNull RefsModel refsModel, @NotNull GraphPrintCellModel printCellModel,
                   @NotNull Project project, @NotNull CacheGet<Hash, VcsCommit> commitDataCache, @NotNull VcsLogProvider logProvider,
                   @NotNull VirtualFile root) {
    myGraphModel = graphModel;
    myRefsModel = refsModel;
    myPrintCellModel = printCellModel;
    myCommitDataGetter = new CacheCommitDataGetter(project, this, commitDataCache, logProvider, root);
  }

  public void appendCommits(@NotNull List<? extends CommitParents> commitParentsList) {
    MyTimer timer = new MyTimer("append commits");
    myGraphModel.appendCommitsToGraph(commitParentsList);
    timer.print();
  }

  @NotNull
  public CommitDataGetter getCommitDataGetter() {
    return myCommitDataGetter;
  }

  @NotNull
  public RefsModel getRefsModel() {
    return myRefsModel;
  }

  @NotNull
  public GraphModel getGraphModel() {
    return myGraphModel;
  }

  @NotNull
  public GraphPrintCellModel getPrintCellModel() {
    return myPrintCellModel;
  }
}
