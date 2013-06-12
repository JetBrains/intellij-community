package org.hanuna.gitalk.ui.frame;

import com.intellij.ui.components.JBScrollPane;
import org.hanuna.gitalk.data.VcsLogDataHolder;
import org.hanuna.gitalk.ui.VcsLogController;
import org.hanuna.gitalk.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Graph + the panel with branch labels above it.
 *
 * @author Kirill Likhodedov
 */
public class ActiveSurface extends JPanel {

  @NotNull private final VcsLogGraphTable myGraphTable;
  @NotNull private final BranchesPanel myBranchesPanel;

  ActiveSurface(@NotNull VcsLogDataHolder logController, @NotNull VcsLogUI vcsLogUI) {
    myGraphTable = new VcsLogGraphTable(vcsLogUI);
    myBranchesPanel = new BranchesPanel(logController, vcsLogUI);
    packTables();
  }

  @NotNull
  public VcsLogGraphTable getGraphTable() {
    return myGraphTable;
  }

  private void packTables() {
    setLayout(new BorderLayout());
    add(myBranchesPanel, BorderLayout.NORTH);
    add(new JBScrollPane(myGraphTable), BorderLayout.CENTER);
  }

  @NotNull
  public BranchesPanel getBranchesPanel() {
    return myBranchesPanel;
  }
}
