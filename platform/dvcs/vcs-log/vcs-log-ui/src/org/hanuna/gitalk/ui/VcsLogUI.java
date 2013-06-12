package org.hanuna.gitalk.ui;

import com.intellij.vcs.log.Hash;
import org.hanuna.gitalk.ui.frame.MainFrame;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author erokhins
 */
public class VcsLogUI {

  @NotNull private final VcsLogController myUiController;
  @NotNull private final MainFrame myMainFrame;

  public VcsLogUI(@NotNull VcsLogController uiController) {
    myUiController = uiController;
    myMainFrame = new MainFrame(myUiController);
  }

  @NotNull
  public MainFrame getMainFrame() {
    return myMainFrame;
  }

  public void loadingCompleted() {
    myMainFrame.initialLoadingCompleted();
  }

  public void jumpToRow(final int rowIndex) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        myMainFrame.getGraphTable().jumpToRow(rowIndex);
        myUiController.click(rowIndex);
      }
    });
  }

  public void updateUI() {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        myMainFrame.getGraphTable().setModel(myUiController.getGraphTableModel());
        myMainFrame.getGraphTable().setPreferredColumnWidths();
        myMainFrame.getGraphTable().repaint();
        myMainFrame.refresh();
      }
    });
  }

  public void addToSelection(final Hash hash) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        int row = myUiController.getDataPack().getRowByHash(hash);
        myMainFrame.getGraphTable().getSelectionModel().addSelectionInterval(row, row);
      }
    });
  }
}
