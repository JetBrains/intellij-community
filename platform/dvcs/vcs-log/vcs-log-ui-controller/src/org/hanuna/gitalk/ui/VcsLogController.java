package org.hanuna.gitalk.ui;

import com.intellij.openapi.project.Project;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.rebase.InteractiveRebaseBuilder;
import org.hanuna.gitalk.data.rebase.VcsLogActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public interface VcsLogController {

  void readNextPart();

  @NotNull
  InteractiveRebaseBuilder getInteractiveRebaseBuilder();

  @NotNull
  VcsLogActionHandler getVcsLogActionHandler();

  @Nullable
  DataPack getDataPack();

  Project getProject();

  void applyInteractiveRebase();

  VcsLogActionHandler.Callback getCallback();

  void cancelInteractiveRebase();

  boolean isInteractiveRebaseInProgress();

  void refresh();
}
