package org.hanuna.gitalk.data;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.VcsCommit;
import com.intellij.vcs.log.VcsLogProvider;

import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class MiniDetailsGetter extends DataGetter<VcsCommit> {

  MiniDetailsGetter(VcsLogDataHolder dataHolder, VcsLogProvider logProvider, VirtualFile root) {
    super(dataHolder, logProvider, root, new VcsCommitCache<VcsCommit>());
  }

  @Override
  protected List<? extends VcsCommit> readDetails(List<String> hashes) throws VcsException {
    // TODO use the intermediate file storage
    return myLogProvider.readMiniDetails(myRoot, hashes);
  }

}
