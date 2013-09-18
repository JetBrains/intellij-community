package org.hanuna.gitalk.ui.tables;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsCommitMiniDetails;
import com.intellij.vcs.log.VcsRef;
import org.hanuna.gitalk.data.DataPack;
import org.hanuna.gitalk.data.VcsLogDataHolder;
import org.hanuna.gitalk.data.rebase.FakeCommitParents;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.ui.render.PositionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * @author erokhins
 */
public class GraphTableModel extends AbstractTableModel {

  public static final int ROOT_COLUMN = 0;
  public static final int COMMIT_COLUMN = 1;
  public static final int AUTHOR_COLUMN = 2;
  public static final int DATE_COLUMN = 3;
  private static final int COLUMN_COUNT = DATE_COLUMN + 1;

  private static final String[] COLUMN_NAMES = {"Root", "Subject", "Author", "Date"};
  private final DataPack dataPack;

  private final Map<Hash, String> reworded = new HashMap<Hash, String>();
  private final Set<Hash> fixedUp = new HashSet<Hash>();
  private final Set<Hash> applied = new HashSet<Hash>();
  @NotNull private final VcsLogDataHolder myDataHolder;

  public GraphTableModel(@NotNull VcsLogDataHolder dataHolder) {
    myDataHolder = dataHolder;
    this.dataPack = dataHolder.getDataPack();
  }

  @Override
  public int getRowCount() {
    return dataPack.getGraphModel().getGraph().getNodeRows().size();
  }

  @Override
  public int getColumnCount() {
    return COLUMN_COUNT;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    Node commitNode = dataPack.getGraphModel().getGraph().getCommitNodeInRow(rowIndex);
    VcsCommitMiniDetails data;
    if (commitNode == null) {
      data = null;
    }
    else {
      data = myDataHolder.getMiniDetailsGetter().getCommitData(commitNode);
    }
    switch (columnIndex) {
      case ROOT_COLUMN:
        if (commitNode != null) {
          return commitNode.getBranch().getRepositoryRoot();
        }
        else {
          return null;
        }
      case COMMIT_COLUMN:
        GraphPrintCell graphPrintCell = dataPack.getPrintCellModel().getGraphPrintCell(rowIndex);
        GraphCommitCell.Kind cellKind = getCellKind(PositionUtil.getNode(graphPrintCell));
        String message = "";
        List<VcsRef> refs = Collections.emptyList();
        if (data != null) {
          if (cellKind == GraphCommitCell.Kind.REWORD) {
            message = reworded.get(commitNode.getCommitHash());
          }
          else {
            message = data.getSubject();
            refs = dataPack.getRefsModel().refsToCommit(data.getHash());
          }
        }
        else {
          if (rowIndex == getRowCount() - 1) {
            message = "load more commits";
          }
        }
        return new GraphCommitCell(graphPrintCell, cellKind, message, refs);
      case AUTHOR_COLUMN:
        if (data == null) {
          return "";
        }
        else {
          return data.getAuthorName();
        }
      case DATE_COLUMN:
        if (data == null) {
          return "";
        }
        else {
          return DateFormatUtil.formatDateTime(data.getAuthorTime());
        }
      default:
        throw new IllegalArgumentException("columnIndex > 2");
    }
  }

  private GraphCommitCell.Kind getCellKind(Node node) {
    if (node == null) {
      return GraphCommitCell.Kind.NORMAL;
    }
    Hash hash = node.getCommitHash();
    if (applied.contains(hash)) return GraphCommitCell.Kind.APPLIED;
    if (fixedUp.contains(hash)) return GraphCommitCell.Kind.FIXUP;
    if (reworded.containsKey(hash)) return GraphCommitCell.Kind.REWORD;
    if (FakeCommitParents.isFake(hash)) return GraphCommitCell.Kind.PICK;
    return GraphCommitCell.Kind.NORMAL;
  }

  @Override
  public Class<?> getColumnClass(int column) {
    switch (column) {
      case ROOT_COLUMN:
        return VirtualFile.class;
      case COMMIT_COLUMN:
        return GraphCommitCell.class;
      case AUTHOR_COLUMN:
        return String.class;
      case DATE_COLUMN:
        return String.class;
      default:
        throw new IllegalArgumentException("column > 2");
    }
  }

  @Override
  public String getColumnName(int column) {
    return COLUMN_NAMES[column];
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex) {
    return false;
  }

  public void clearReworded() {
    reworded.clear();
    //fireTableDataChanged();
  }

  public void addReworded(Hash hash, String newMessage) {
    reworded.put(hash, newMessage);
    //fireTableDataChanged();
  }

  public void addReworded(Map<Hash, String> map) {
    reworded.putAll(map);
    //fireTableDataChanged();
  }

  public void addFixedUp(Collection<Hash> collection) {
    fixedUp.addAll(collection);
  }

  public void addApplied(Hash commit) {
    applied.add(commit);
    fireTableCellUpdated(dataPack.getRowByHash(commit), 0);
  }
}
