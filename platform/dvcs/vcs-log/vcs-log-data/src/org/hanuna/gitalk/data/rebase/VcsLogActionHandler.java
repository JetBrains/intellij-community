package org.hanuna.gitalk.data.rebase;

import org.hanuna.gitalk.graph.elements.Node;
import com.intellij.vcs.log.RebaseCommand;
import com.intellij.vcs.log.Ref;

import java.util.List;

public interface VcsLogActionHandler {
  VcsLogActionHandler DO_NOTHING = new VcsLogActionHandler() {
    @Override
    public void abortRebase() {
    }

    @Override
    public void continueRebase() {
    }

    @Override
    public void cherryPick(Ref targetRef, List<Node> nodesToPick, Callback callback) {
    }

    @Override
    public void rebase(Node onto, Ref subjectRef, Callback callback) {
    }

    @Override
    public void rebaseOnto(Node onto, Ref subjectRef, List<Node> nodesToRebase, Callback callback) {
    }

    @Override
    public void interactiveRebase(Ref subjectRef, Node onto, Callback callback, List<RebaseCommand> commands) {
    }
  };

  void abortRebase();

  void continueRebase();

  interface Callback {
    void disableModifications();
    void enableModifications();

    void interactiveCommandApplied(RebaseCommand command);
  }

  void cherryPick(Ref targetRef, List<Node> nodesToPick, Callback callback);

  void rebase(Node onto, Ref subjectRef, Callback callback);
  void rebaseOnto(Node onto, Ref subjectRef, List<Node> nodesToRebase, Callback callback);

  void interactiveRebase(Ref subjectRef, Node onto, Callback callback, List<RebaseCommand> commands);
}
