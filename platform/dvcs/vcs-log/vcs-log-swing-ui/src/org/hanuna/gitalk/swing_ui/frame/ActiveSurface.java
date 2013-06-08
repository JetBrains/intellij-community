package org.hanuna.gitalk.swing_ui.frame;

import org.hanuna.gitalk.ui.UI_Controller;

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

  ActiveSurface(UI_Controller ui_controller) {
    this.graphTable = new VcsLogGraphTable(ui_controller);
    myBranchesPanel = new BranchesPanel(ui_controller);
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
