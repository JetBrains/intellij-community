package org.hanuna.gitalk.ui.tables;

import org.hanuna.gitalk.printmodel.GraphPrintCell;
import org.hanuna.gitalk.refs.Ref;

import java.util.List;


/**
 * @author erokhins
 */
public class GraphCommitCell extends CommitCell {

  public enum Kind {
    NORMAL,
    PICK,
    FIXUP,
    REWORD,
    APPLIED
  }

  private final GraphPrintCell row;
  private final Kind kind;

  public GraphCommitCell(GraphPrintCell row, Kind kind, String text, List<Ref> refsToThisCommit) {
    super(text, refsToThisCommit);
    this.kind = kind;
    this.row = row;
  }

  public GraphPrintCell getPrintCell() {
    return row;
  }


  public Kind getKind() {
    return kind;
  }
}
