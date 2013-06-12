package org.hanuna.gitalk.ui;

import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.Ref;
import org.hanuna.gitalk.data.DataPackUtils;
import org.hanuna.gitalk.data.impl.DataPack;
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

  public TableModel getGraphTableModel();

  public void click(@Nullable GraphElement graphElement);

  public void click(int rowIndex);

  public void over(@Nullable GraphElement graphElement);

  public void hideAll();

  public void showAll();

  public void setLongEdgeVisibility(boolean visibility);

  public void doubleClick(int rowIndex);

  public void readNextPart();

  public void jumpToCommit(Hash commitHash);

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
