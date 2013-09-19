package org.hanuna.gitalk.ui.frame;

import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.RefreshAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.VcsLogSettings;
import org.hanuna.gitalk.data.VcsLogDataHolder;
import org.hanuna.gitalk.ui.GitLogIcons;
import org.hanuna.gitalk.ui.VcsLogUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author erokhins
 */
public class MainFrame {

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final VcsLogUI myUI;
  @NotNull private final Project myProject;
  @NotNull private final JPanel myMainPanel;
  @NotNull private final ActiveSurface myActiveSurface;
  @NotNull private final VcsLogSettings mySettings;

  public MainFrame(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogUI vcsLogUI, @NotNull Project project) {
    myLogDataHolder = logDataHolder;
    myUI = vcsLogUI;
    myProject = project;
    mySettings = ServiceManager.getService(myProject, VcsLogSettings.class);

    myActiveSurface = new ActiveSurface(logDataHolder, vcsLogUI, project);
    myActiveSurface.setupDetailsSplitter(mySettings.isShowDetails());

    myMainPanel = new JPanel();
    myMainPanel.setLayout(new BorderLayout());
    myMainPanel.add(createToolbar(), BorderLayout.NORTH);
    myMainPanel.add(myActiveSurface, BorderLayout.CENTER);
  }

  public VcsLogGraphTable getGraphTable() {
    return myActiveSurface.getGraphTable();
  }

  private JComponent createToolbar() {
    AnAction hideBranchesAction = new DumbAwareAction("Collapse linear branches", "Collapse linear branches", GitLogIcons.SPIDER) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myUI.hideAll();
      }
    };

    AnAction showBranchesAction = new DumbAwareAction("Expand all branches", "Expand all branches", GitLogIcons.WEB) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myUI.showAll();
      }
    };

    RefreshAction refreshAction = new RefreshAction("Refresh", "Refresh", AllIcons.Actions.Refresh) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        myLogDataHolder.refreshCompletely();
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
        return !myUI.areLongEdgesHidden();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myUI.setLongEdgeVisibility(state);
      }
    };

    ToggleAction showDetailsAction = new ToggleAction("Show Details", "Display details panel", AllIcons.Actions.Preview) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return !myProject.isDisposed() && mySettings.isShowDetails();
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myActiveSurface.setupDetailsSplitter(state);
        if (!myProject.isDisposed()) {
          mySettings.setShowDetails(state);
        }
      }
    };

    refreshAction.registerShortcutOn(myMainPanel);

    DefaultActionGroup toolbarGroup = new DefaultActionGroup(hideBranchesAction, showBranchesAction, showFullPatchAction, refreshAction,
                                                             showDetailsAction);
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true).getComponent();
  }

  public JComponent getMainComponent() {
    return myMainPanel;
  }

  public void refresh() {
    myActiveSurface.getBranchesPanel().rebuild();
  }

}
