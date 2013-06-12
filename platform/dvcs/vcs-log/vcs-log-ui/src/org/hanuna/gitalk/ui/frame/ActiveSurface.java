package org.hanuna.gitalk.ui.frame;

import org.hanuna.gitalk.ui.VcsLogController;
import org.hanuna.gitalk.ui.VcsLogUI;

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

  ActiveSurface(VcsLogController vcsLog_controller, VcsLogUI vcsLogUI) {
    this.graphTable = new VcsLogGraphTable(vcsLog_controller, vcsLogUI);
    myBranchesPanel = new BranchesPanel(vcsLog_controller, vcsLogUI);
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
