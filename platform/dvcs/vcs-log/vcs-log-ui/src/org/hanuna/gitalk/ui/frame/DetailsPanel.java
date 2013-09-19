package org.hanuna.gitalk.ui.frame;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsCommitDetails;
import org.hanuna.gitalk.data.LoadingDetails;
import org.hanuna.gitalk.data.VcsLogDataHolder;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.ui.VcsLogColorManager;
import org.hanuna.gitalk.ui.render.PrintParameters;
import org.hanuna.gitalk.ui.render.painters.RefPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Collections;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
class DetailsPanel extends JPanel implements ListSelectionListener {

  private static final Logger LOG = Logger.getInstance("Vcs.Log");

  private static final String STANDARD_LAYER = "Standard";
  private static final String MESSAGE_LAYER = "Message";

  @NotNull private final VcsLogDataHolder myLogDataHolder;
  @NotNull private final VcsLogGraphTable myGraphTable;

  @NotNull private final RefsPanel myRefsPanel;
  @NotNull private final DataPanel myDataPanel;
  @NotNull private final MessagePanel myMessagePanel;
  @NotNull private final JBLoadingPanel myLoadingPanel;

  DetailsPanel(@NotNull VcsLogDataHolder logDataHolder, @NotNull VcsLogGraphTable graphTable, @NotNull VcsLogColorManager colorManager) {
    super(new CardLayout());
    myLogDataHolder = logDataHolder;
    myGraphTable = graphTable;

    myRefsPanel = new RefsPanel(colorManager);
    myDataPanel = new DataPanel();
    myMessagePanel = new MessagePanel();

    Box box = Box.createVerticalBox();
    box.add(myRefsPanel);
    box.add(myDataPanel);

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), logDataHolder);
    myLoadingPanel.add(ScrollPaneFactory.createScrollPane(box));

    add(myLoadingPanel, STANDARD_LAYER);
    add(myMessagePanel, MESSAGE_LAYER);

    setBackground(UIUtil.getTableBackground());
  }

  @Override
  public void valueChanged(@Nullable ListSelectionEvent notUsed) {
    int[] rows = myGraphTable.getSelectedRows();
    if (rows.length < 1) {
      myLoadingPanel.stopLoading();
      ((CardLayout)getLayout()).show(this, MESSAGE_LAYER);
      myMessagePanel.setText("Nothing selected");
    }
    else if (rows.length > 1) {
      myLoadingPanel.stopLoading();
      ((CardLayout)getLayout()).show(this, MESSAGE_LAYER);
      myMessagePanel.setText("Several commits selected");
    }
    else {
      ((CardLayout)getLayout()).show(this, STANDARD_LAYER);
      Node node = myLogDataHolder.getDataPack().getNode(rows[0]);
      if (node == null) {
        LOG.info("Couldn't find node for row " + rows[0] +
                 ". All nodes: " + myLogDataHolder.getDataPack().getGraphModel().getGraph().getNodeRows());
        return;
      }
      Hash hash = node.getCommitHash();
      VcsCommitDetails commitData = myLogDataHolder.getCommitDetailsGetter().getCommitData(node);
      if (commitData instanceof LoadingDetails) {
        myLoadingPanel.startLoading();
        myDataPanel.setData(null);
        myRefsPanel.setRefs(Collections.<VcsRef>emptyList());
      }
      else {
        myLoadingPanel.stopLoading();
        myDataPanel.setData(commitData);
        myRefsPanel.setRefs(sortRefs(hash, node.getBranch().getRepositoryRoot()));
      }
    }
  }

  @NotNull
  private List<VcsRef> sortRefs(@NotNull Hash hash, @NotNull VirtualFile root) {
    List<VcsRef> refs = myLogDataHolder.getDataPack().getRefsModel().refsToCommit(hash);
    return myLogDataHolder.getLogProvider(root).getRefSorter().sort(refs);
  }

  private static class DataPanel extends JPanel {

    @NotNull private final JLabel myHashLabel;
    @NotNull private final JTextField myAuthor;
    @NotNull private final JTextArea myCommitMessage;

    DataPanel() {
      super();
      myHashLabel = new LinkLabel("", null, new LinkListener() {
        @Override
        public void linkSelected(LinkLabel aSource, Object aLinkData) {
          CopyPasteManager.getInstance().setContents(new StringSelection(myHashLabel.getText()));
        }
      });

      myAuthor = new JBTextField();
      myAuthor.setEditable(false);
      myAuthor.setBorder(null);

      myCommitMessage = new JTextArea();
      myCommitMessage.setEditable(false);
      myCommitMessage.setBorder(null);

      setLayout(new GridBagLayout());
      GridBag g = new GridBag()
        .setDefaultAnchor(GridBagConstraints.NORTHWEST)
        .setDefaultFill(GridBagConstraints.HORIZONTAL)
        .setDefaultWeightX(1.0);
      add(myHashLabel, g.nextLine().next());
      add(myAuthor, g.nextLine().next());
      add(myCommitMessage, g.nextLine().next());
      add(Box.createVerticalGlue(), g.nextLine().next().weighty(1.0).fillCell());

      setOpaque(false);
    }

    void setData(@Nullable VcsCommitDetails commit) {
      if (commit == null) {
        myHashLabel.setText("");
        myCommitMessage.setText("");
        myAuthor.setText("");
      }
      else {
        myHashLabel.setText(commit.getHash().toShortString());
        myCommitMessage.setText(commit.getFullMessage());

        String authorText = commit.getAuthorName();
        if (!commit.getAuthorName().equals(commit.getCommitterName()) || !commit.getAuthorEmail().equals(commit.getCommitterEmail())) {
          authorText += " (committed by " + commit.getCommitterName() + ")";
        }
        myAuthor.setText(authorText);
      }
      repaint();
    }
  }

  private static class RefsPanel extends JPanel {

    @NotNull private final RefPainter myRefPainter;
    @NotNull private List<VcsRef> myRefs;

    RefsPanel(@NotNull VcsLogColorManager colorManager) {
      myRefPainter = new RefPainter(colorManager, false);
      myRefs = Collections.emptyList();
      setPreferredSize(new Dimension(-1, PrintParameters.HEIGHT_CELL + UIUtil.DEFAULT_VGAP));
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      myRefPainter.draw((Graphics2D)g, myRefs, 0);
    }

    void setRefs(@NotNull List<VcsRef> refs) {
      myRefs = refs;
      repaint();
    }
  }

  private static class MessagePanel extends JPanel {

    private final JLabel myLabel;

    MessagePanel() {
      super(new BorderLayout());
      myLabel = new JLabel();
      myLabel.setForeground(UIUtil.getInactiveTextColor());
      add(myLabel);
    }

    void setText(String text) {
      myLabel.setText(text);
    }
  }
}
