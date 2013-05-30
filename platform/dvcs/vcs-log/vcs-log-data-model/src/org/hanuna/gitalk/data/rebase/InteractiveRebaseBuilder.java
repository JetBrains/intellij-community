package org.hanuna.gitalk.data.rebase;

import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.log.commit.parents.RebaseCommand;
import org.hanuna.gitalk.refs.Ref;

import java.util.Collections;
import java.util.List;

public class InteractiveRebaseBuilder {
  public static final InteractiveRebaseBuilder EMPTY = new InteractiveRebaseBuilder();

  public void startRebase(Ref subjectRef, Node onto) {

  }

  public void startRebaseOnto(Ref subjectRef, Node base, List<Node> nodesToRebase) {

  }

  public void moveCommits(Ref subjectRef, Node base, InsertPosition position, List<Node> nodesToInsert) {

  }

  public void fixUp(Ref subjectRef, Node target, List<Node> nodesToFixUp) {

  }

  public void reword(Ref subjectRef, Node commitToReword, String newMessage) {

  }

  public List<RebaseCommand> getRebaseCommands() {
    return Collections.emptyList();
  }

  public enum InsertPosition {
    ABOVE,
    BELOW
  }
}
