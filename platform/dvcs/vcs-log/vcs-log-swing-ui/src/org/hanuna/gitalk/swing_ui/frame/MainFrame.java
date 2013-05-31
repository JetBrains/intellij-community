package org.hanuna.gitalk.swing_ui.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import org.hanuna.gitalk.swing_ui.GitLogIcons;
import org.hanuna.gitalk.ui.UI_Controller;

import javax.swing.*;

/**
 * @author erokhins
 */
public class MainFrame {

  private final UI_Controller ui_controller;
  private final JPanel mainPanel = new JPanel();
  private final ActiveSurface myActiveSurface;


  public MainFrame(final UI_Controller ui_controller) {
    this.ui_controller = ui_controller;
    myActiveSurface = new ActiveSurface(ui_controller);

    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
    mainPanel.add(createToolbar());
    mainPanel.add(myActiveSurface);
  }

  public UI_GraphTable getGraphTable() {
    return myActiveSurface.getGraphTable();
  }

  private JComponent createToolbar() {
    AnAction hideBranchesAction = new DumbAwareAction("Collapse linear branches", "Collapse linear branches", GitLogIcons.SPIDER) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ui_controller.hideAll();
      }
    };

    AnAction showBranchesAction = new DumbAwareAction("Expand all branches", "Expand all branches", GitLogIcons.WEB) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ui_controller.showAll();
      }
    };

    AnAction refreshAction = new RefreshAction("Refresh", "Refresh", AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ui_controller.refresh(false);
      }

      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(true);
      }
    };

    AnAction showFullPatchAction = new ToggleAction("Show full patch", "Expand all branches even if they occupy a lot of space",
                                                    AllIcons.Actions.Expandall) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return !ui_controller.areLongEdgesHidden();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        ui_controller.setLongEdgeVisibility(state);
      }
    };

    DefaultActionGroup toolbarGroup = new DefaultActionGroup(hideBranchesAction, showBranchesAction, showFullPatchAction, refreshAction);
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true).getComponent();
  }

  public JPanel getMainComponent() {
    return mainPanel;
  }

  public void refresh() {
    myActiveSurface.getBranchesPanel().rebuild();
  }
}
