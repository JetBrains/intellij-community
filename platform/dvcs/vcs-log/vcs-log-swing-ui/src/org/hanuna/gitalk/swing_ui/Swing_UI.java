package org.hanuna.gitalk.swing_ui;

import com.intellij.vcs.log.Hash;
import org.hanuna.gitalk.swing_ui.frame.MainFrame;
import org.hanuna.gitalk.ui.UI_Controller;

import javax.swing.*;

/**
 * @author erokhins
 */
public class Swing_UI {

  private final UI_Controller ui_controller;
  private MainFrame mainFrame = null;
  private final VcsLogDragDropSupport myDragDropSupport;

  public MainFrame getMainFrame() {
    return mainFrame;
  }

  public Swing_UI(UI_Controller ui_controller) {
    this.ui_controller = ui_controller;
    this.mainFrame = new MainFrame(ui_controller);
    myDragDropSupport = new VcsLogDragDropSupport(ui_controller, mainFrame);
  }

  public void loadingCompleted() {
    mainFrame.initialLoadingCompleted();
  }

  public void jumpToRow(final int rowIndex) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        mainFrame.getGraphTable().jumpToRow(rowIndex);
        ui_controller.click(rowIndex);
      }
    });
  }

  public void updateUI() {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        mainFrame.getGraphTable().setModel(ui_controller.getGraphTableModel());
        mainFrame.getGraphTable().setPreferredColumnWidths();
        mainFrame.getGraphTable().repaint();
        mainFrame.refresh();
      }
    });
  }

  public void addToSelection(final Hash hash) {
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        int row = ui_controller.getDataPackUtils().getRowByHash(hash);
        mainFrame.getGraphTable().getSelectionModel().addSelectionInterval(row, row);
      }
    });
  }
}
