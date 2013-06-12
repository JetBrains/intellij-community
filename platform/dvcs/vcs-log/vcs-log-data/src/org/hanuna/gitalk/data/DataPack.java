package org.hanuna.gitalk.data;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Ref;
import com.intellij.vcs.log.VcsCommit;
import com.intellij.vcs.log.VcsLogProvider;
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
  @NotNull private final CacheCommitDataGetter myCommitDataGetter;

  @NotNull
  public static DataPack build(@NotNull List<? extends VcsCommit> commits, @NotNull Collection<Ref> allRefs,
                               @NotNull ProgressIndicator indicator, @NotNull VcsCommitCache commitDataCache,
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
    return new DataPack(graphModel, refsModel, printCellModel, commitDataCache, logProvider, root);
  }

  private DataPack(@NotNull GraphModel graphModel, @NotNull RefsModel refsModel, @NotNull GraphPrintCellModel printCellModel,
                   @NotNull VcsCommitCache commitDataCache, @NotNull VcsLogProvider logProvider, @NotNull VirtualFile root) {
    myGraphModel = graphModel;
    myRefsModel = refsModel;
    myPrintCellModel = printCellModel;
    myCommitDataGetter = new CacheCommitDataGetter(this, commitDataCache, logProvider, root);
  }

  public void appendCommits(@NotNull List<? extends CommitParents> commitParentsList) {
    MyTimer timer = new MyTimer("append commits");
    myGraphModel.appendCommitsToGraph(commitParentsList);
    timer.print();
  }

  @NotNull
  public CacheCommitDataGetter getCommitDataGetter() {
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
