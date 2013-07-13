package org.hanuna.gitalk.ui.frame;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.Ref;
import com.intellij.vcs.log.VcsCommitDetails;
import org.hanuna.gitalk.data.VcsLogDataHolder;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.ui.render.PrintParameters;
import org.hanuna.gitalk.ui.render.painters.RefPainter;
import org.jetbrains.annotations.NotNull;

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

  private final VcsLogDataHolder myLogDataHolder;
  private final VcsLogGraphTable myGraphTable;

  private final RefsPanel myRefsPanel;
  private final DataPanel myDataPanel;
  private final MessagePanel myMessagePanel;

  DetailsPanel(VcsLogDataHolder logDataHolder, VcsLogGraphTable graphTable) {
    super(new CardLayout());
    myLogDataHolder = logDataHolder;
    myGraphTable = graphTable;

    myRefsPanel = new RefsPanel();
    myDataPanel = new DataPanel();
    myMessagePanel = new MessagePanel();

    Box box = Box.createVerticalBox();
    box.add(myRefsPanel);
    box.add(myDataPanel);

    add(ScrollPaneFactory.createScrollPane(box), STANDARD_LAYER);
    add(myMessagePanel, MESSAGE_LAYER);
    add(new JLabel("Test"), "Test");

    setBackground(UIUtil.getTableBackground());
  }

  @Override
  public void valueChanged(ListSelectionEvent e) {
    int[] rows = myGraphTable.getSelectedRows();
    if (rows.length < 1) {
      ((CardLayout)getLayout()).show(this, MESSAGE_LAYER);
      myMessagePanel.setText("Nothing selected");
    }
    else if (rows.length > 1) {
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
      try {
        myDataPanel.setData(myLogDataHolder.getCommitDetailsGetter().getCommitData(node));
      }
      catch (VcsException e1) {
        throw new RuntimeException(e1); // TODO
      }
      myRefsPanel.setRefs(myLogDataHolder.getDataPack().getRefsModel().refsToCommit(hash));
    }
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

    void setData(VcsCommitDetails commit) {
      myHashLabel.setText(commit.getHash().toShortString());
      myCommitMessage.setText(commit.getFullMessage());

      String authorText = commit.getAuthorName();
      if (!commit.getAuthorName().equals(commit.getCommitterName()) || !commit.getAuthorEmail().equals(commit.getCommitterEmail())) {
        authorText += " (committed by " + commit.getCommitterName() + ")";
      }
      myAuthor.setText(authorText);
      repaint();
    }
  }

  private static class RefsPanel extends JPanel {

    @NotNull private final RefPainter myRefPainter;
    @NotNull private List<Ref> myRefs;

    RefsPanel() {
      myRefPainter = new RefPainter();
      myRefs = Collections.emptyList();
      setPreferredSize(new Dimension(-1, PrintParameters.HEIGHT_CELL + UIUtil.DEFAULT_VGAP));
      setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
      myRefPainter.draw((Graphics2D)g, myRefs, 0);
    }

    void setRefs(@NotNull List<Ref> refs) {
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
