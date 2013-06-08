package org.hanuna.gitalk.ui.frame;

import org.hanuna.gitalk.ui.VcsLogController;

import javax.swing.*;
import java.awt.*;

/**
 * Graph + the panel with branch labels above it.
 *
 * @author Kirill Likhodedov
 */
public class ActiveSurface extends JPanel {

  private final VcsLogGraphTable graphTable;
  private final BranchesPanel myBranchesPanel;

  ActiveSurface(VcsLogController vcsLog_controller) {
    this.graphTable = new VcsLogGraphTable(vcsLog_controller);
    myBranchesPanel = new BranchesPanel(vcsLog_controller);
    packTables();
  }

  public VcsLogGraphTable getGraphTable() {
    return graphTable;
  }

  private void packTables() {
    setLayout(new BorderLayout());
    add(myBranchesPanel, BorderLayout.NORTH);
    add(new JScrollPane(graphTable), BorderLayout.CENTER);
  }

  public BranchesPanel getBranchesPanel() {
    return myBranchesPanel;
  }
}
