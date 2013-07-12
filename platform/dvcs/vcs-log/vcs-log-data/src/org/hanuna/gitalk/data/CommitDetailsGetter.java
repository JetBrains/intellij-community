package org.hanuna.gitalk.data;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsCommitDetails;
import com.intellij.vcs.log.VcsLogProvider;

import java.util.List;

/**
 * The CommitDetailsGetter is responsible for getting {@link VcsCommitDetails complete commit details} from the cache or from the VCS.
 *
 * @author Kirill Likhodedov
 */
public class CommitDetailsGetter extends DataGetter<VcsCommitDetails> {

  CommitDetailsGetter(VcsLogDataHolder dataHolder, VcsLogProvider logProvider, VirtualFile root) {
    super(dataHolder, logProvider, root, new VcsCommitCache<VcsCommitDetails>(logProvider, root));
  }

  @Override
  protected List<? extends VcsCommitDetails> readDetails(List<String> hashes) throws VcsException {
    return myLogProvider.readDetails(myRoot, hashes);
  }

}
