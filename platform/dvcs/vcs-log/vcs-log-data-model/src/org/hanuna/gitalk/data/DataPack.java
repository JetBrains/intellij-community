package org.hanuna.gitalk.data;

import com.intellij.vcs.log.CommitParents;
import org.hanuna.gitalk.graphmodel.GraphModel;
import org.hanuna.gitalk.printmodel.GraphPrintCellModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface DataPack {

  void appendCommits(@NotNull List<? extends CommitParents> commitParentsList);

  public CommitDataGetter getCommitDataGetter();

  @NotNull
  public RefsModel getRefsModel();

  @NotNull
  public GraphModel getGraphModel();

  @NotNull
  public GraphPrintCellModel getPrintCellModel();

}
