package org.hanuna.gitalk.data;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.hanuna.gitalk.data.impl.FakeCommitsInfo;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author erokhins
 */
public interface DataLoader {

  void readNextPart(@NotNull ProgressIndicator indicator, VirtualFile root)
    throws IOException, VcsException;

  @NotNull
  DataPack getDataPack();
}
