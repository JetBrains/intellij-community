package org.hanuna.gitalk.swing_ui.frame;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.UIUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.hanuna.gitalk.swing_ui.GitLogIcons;
import org.hanuna.gitalk.ui.UI_Controller;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author erokhins
 */
public class MainFrame {
  private final UI_Controller ui_controller;

  private final JPanel myToolbar = new JPanel();
  private final JPanel mainPanel = new JPanel();

  private final JButton myAbortButton = new JButton("Abort Rebase");
  private final JButton myContinueButton = new JButton("Continue Rebase");

  private final ActiveSurface myActiveSurface;


  public MainFrame(final UI_Controller ui_controller) {
    this.ui_controller = ui_controller;
    myActiveSurface = new ActiveSurface(ui_controller);
    packMainPanel();

    ui_controller.getProject().getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
      @Override
      public void repositoryChanged(@NotNull final GitRepository repository) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            updateToolbar(repository);
          }
        });

      }
    });
  }

  private void updateToolbar(GitRepository repository) {
    boolean rebasing = repository.getState() == GitRepository.State.REBASING;
    myAbortButton.setVisible(rebasing);
    myContinueButton.setVisible(rebasing);
  }

  public UI_GraphTable getGraphTable() {
    return myActiveSurface.getGraphTable();
  }

  private void packToolbar() {
    myToolbar.setLayout(new BoxLayout(myToolbar, BoxLayout.LINE_AXIS));
    myToolbar.setMaximumSize(new Dimension(10000, 10));

    Action hide = new AbstractAction("", GitLogIcons.SPIDER) {
      {
        putValue(SHORT_DESCRIPTION, "Collapse linear branches");
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.hideAll();
      }
    };
    myToolbar.add(new JButton(hide));

    Action show = new AbstractAction("", GitLogIcons.WEB) {
      {
        putValue(SHORT_DESCRIPTION, "Expand all branches");
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.showAll();
      }
    };
    myToolbar.add(new JButton(show));

    Action refresh = new AbstractAction("", AllIcons.Actions.Refresh) {
      {
        putValue(SHORT_DESCRIPTION, "Refresh");
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.refresh(false);
      }
    };
    myToolbar.add(new JButton(refresh));

    Action apply = new AbstractAction("", GitLogIcons.APPLY) {
      {
        putValue(SHORT_DESCRIPTION, "Apply interactive rebase");
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.applyInteractiveRebase();
      }
    };
    myToolbar.add(new JButton(apply));

    Action cancel = new AbstractAction("", GitLogIcons.CANCEL) {
      {
        putValue(SHORT_DESCRIPTION, "Cancel interactive rebase");
      }

      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.cancelInteractiveRebase();
      }
    };
    myToolbar.add(new JButton(cancel));

    final JCheckBox visibleLongEdges = new JCheckBox("Show full patch", false);
    visibleLongEdges.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        ui_controller.setLongEdgeVisibility(visibleLongEdges.isSelected());
      }
    });
    myToolbar.add(visibleLongEdges);

    myAbortButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.getGitActionHandler().abortRebase();
      }
    });
    myToolbar.add(myAbortButton);

    myContinueButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ui_controller.getGitActionHandler().continueRebase();
      }
    });
    myToolbar.add(myContinueButton);

    myToolbar.add(Box.createHorizontalGlue());
  }

  private void packMainPanel() {
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

    packToolbar();
    mainPanel.add(myToolbar);
    mainPanel.add(myActiveSurface);
  }

  public JPanel getMainComponent() {
    return mainPanel;
  }

  public void refresh() {
    myActiveSurface.getBranchesPanel().rebuild();
    updateToolbar(ui_controller.getRepository());
  }
}
