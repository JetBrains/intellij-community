package org.hanuna.gitalk.ui.tables.refs.refs;

import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.Ref;
import org.hanuna.gitalk.data.RefsModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author erokhins
 */
public class RefTreeModelImpl implements RefTreeModel {
  private final RefsModel refsModel;
  private final CommitSelectManager selectManager;

  public RefTreeModelImpl(RefsModel refsModel) {
    this.refsModel = refsModel;
    this.selectManager = new CommitSelectManager(getHeadHash(refsModel));
    selectAll();
  }

  private static Hash getHeadHash(@NotNull RefsModel refsModel) {
    List<Ref> allRefs = refsModel.getAllRefs();
    Hash headHash = allRefs.get(0).getCommitHash();
    for (Ref ref : allRefs) {
      if (ref.getType() == Ref.RefType.HEAD) {
        headHash = ref.getCommitHash();
        break;
      }
    }
    return headHash;
  }

  private void selectAll() {
    for (Ref ref : refsModel.getAllRefs()) {
      if (ref.getType() != Ref.RefType.TAG) {
        selectManager.setSelectCommit(ref.getCommitHash(), true);
      }
    }
  }

  @Override
  public Set<Hash> getCheckedCommits() {
    return selectManager.getSelectCommits();
  }

  @Override
  public void inverseSelectCommit(Set<Hash> commits) {
    selectManager.inverseSelectCommit(commits);
  }

}
