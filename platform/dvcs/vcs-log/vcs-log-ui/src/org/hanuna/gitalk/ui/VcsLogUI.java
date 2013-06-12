package org.hanuna.gitalk.ui;

import com.intellij.vcs.log.Hash;
import org.hanuna.gitalk.common.compressedlist.UpdateRequest;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.graphmodel.FragmentManager;
import org.hanuna.gitalk.graphmodel.GraphFragment;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.ui.frame.MainFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author erokhins
 */
public class VcsLogUI {

  @NotNull private final VcsLogController myUiController;
  @NotNull private final MainFrame myMainFrame;

  @Nullable private GraphElement prevGraphElement;

  public VcsLogUI(@NotNull VcsLogController uiController) {
    myUiController = uiController;
    myMainFrame = new MainFrame(myUiController, this);
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
        click(rowIndex);
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

  public void showAll() {
    myUiController.getDataPack().getGraphModel().getFragmentManager().showAll();
    updateUI();
    jumpToRow(0);
  }

  public void hideAll() {
    myUiController.getDataPack().getGraphModel().getFragmentManager().hideAll();
    updateUI();
    jumpToRow(0);
  }

  public void over(@Nullable GraphElement graphElement) {
    SelectController selectController = myUiController.getDataPack().getPrintCellModel().getSelectController();
    FragmentManager fragmentManager = myUiController.getDataPack().getGraphModel().getFragmentManager();
    if (graphElement == prevGraphElement) {
      return;
    }
    else {
      prevGraphElement = graphElement;
    }
    selectController.deselectAll();
    if (graphElement == null) {
      updateUI();
    }
    else {
      GraphFragment graphFragment = fragmentManager.relateFragment(graphElement);
      selectController.select(graphFragment);
      updateUI();
    }
  }

  public void click(@Nullable GraphElement graphElement) {
    SelectController selectController = myUiController.getDataPack().getPrintCellModel().getSelectController();
    FragmentManager fragmentController = myUiController.getDataPack().getGraphModel().getFragmentManager();
    selectController.deselectAll();
    if (graphElement == null) {
      return;
    }
    GraphFragment fragment = fragmentController.relateFragment(graphElement);
    if (fragment == null) {
      return;
    }
    UpdateRequest updateRequest = fragmentController.changeVisibility(fragment);
    updateUI();
    jumpToRow(updateRequest.from());
  }

  public void click(int rowIndex) {
    DataPack dataPack = myUiController.getDataPack();
    dataPack.getPrintCellModel().getCommitSelectController().deselectAll();
    Node node = dataPack.getNode(rowIndex);
    if (node != null) {
      FragmentManager fragmentController = dataPack.getGraphModel().getFragmentManager();
      dataPack.getPrintCellModel().getCommitSelectController().select(fragmentController.allCommitsCurrentBranch(node));
    }
    updateUI();
  }

}
