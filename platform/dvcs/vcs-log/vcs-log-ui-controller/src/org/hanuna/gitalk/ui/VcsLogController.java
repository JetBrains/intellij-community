package org.hanuna.gitalk.ui;

import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.Ref;
import org.hanuna.gitalk.data.DataPackUtils;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.rebase.InteractiveRebaseBuilder;
import org.hanuna.gitalk.data.rebase.VcsLogActionHandler;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.util.Collection;

/**
 * @author erokhins
 */
public interface VcsLogController {

  TableModel getGraphTableModel();

  void click(@Nullable GraphElement graphElement);

  void click(int rowIndex);

  void over(@Nullable GraphElement graphElement);

  void hideAll();

  void showAll();

  void setLongEdgeVisibility(boolean visibility);

  void doubleClick(int rowIndex);

  void readNextPart();

  void jumpToCommit(Hash commitHash);

  Collection<Ref> getRefs();

  @NotNull
  DragDropListener getDragDropListener();

  @NotNull
  InteractiveRebaseBuilder getInteractiveRebaseBuilder();

  @NotNull
  VcsLogActionHandler getVcsLogActionHandler();

  DataPack getDataPack();

  DataPackUtils getDataPackUtils();

  Project getProject();

  void applyInteractiveRebase();

  VcsLogActionHandler.Callback getCallback();

  void cancelInteractiveRebase();

  boolean isInteractiveRebaseInProgress();

  boolean areLongEdgesHidden();

  @NotNull
  JComponent getMainComponent();

  void refresh();
}
