package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.Clock;
import com.intellij.localvcs.utils.Reversed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class ChangeList {
  private List<Change> myChanges = new ArrayList<Change>();
  private ChangeSet myCurrentChangeSet;
  private int myChangeSetDepth;

  public ChangeList() {
  }

  public ChangeList(Stream s) throws IOException {
    int count = s.readInteger();
    while (count-- > 0) {
      myChanges.add(s.readChange());
    }
  }

  public void write(Stream s) throws IOException {
    s.writeInteger(myChanges.size());
    for (Change c : myChanges) {
      s.writeChange(c);
    }
  }

  public void addChange(Change c) {
    if (myChangeSetDepth == 0) {
      myChanges.add(c);
    }
    else {
      myCurrentChangeSet.addChange(c);
    }
  }

  public void beginChangeSet() {
    myChangeSetDepth++;
    if (myChangeSetDepth == 1) {
      myCurrentChangeSet = new ChangeSet(Clock.getCurrentTimestamp());
      myChanges.add(myCurrentChangeSet);
    }
  }

  public void endChangeSet(String name) {
    myChangeSetDepth--;
    if (myChangeSetDepth == 0) {
      if (myCurrentChangeSet.getChanges().isEmpty()) {
        myChanges.remove(myChanges.size() - 1);
        return;
      }
      myCurrentChangeSet.setName(name);
      myCurrentChangeSet = null;
    }
  }

  public List<Change> getChanges() {
    List<Change> result = new ArrayList<Change>(myChanges);
    Collections.reverse(result);
    return result;
  }

  public boolean isBefore(Change before, Change after, boolean canBeEqual) {
    int beforeIndex = myChanges.indexOf(before);
    int afterIndex = myChanges.indexOf(after);

    return beforeIndex < afterIndex || (canBeEqual && beforeIndex == afterIndex);
  }

  public List<Change> getChain(Change initialChange) {
    List<Change> result = new ArrayList<Change>();
    for (Change c : myChanges) {
      if (c == initialChange) {
        result.add(c);
        continue;
      }
      if (c.affectsSameAs(result)) result.add(c);
    }
    return result;
  }

  public void accept(ChangeVisitor v) throws IOException {
    try {
      for (Change change : Reversed.list(myChanges)) {
        change.accept(v);
      }
    }
    catch (ChangeVisitor.StopVisitingException e) {
    }
    v.finished();
  }

  public List<Change> getChangesFor(Entry r, String path) {
    try {
      ChangeCollectingVisitor v = new ChangeCollectingVisitor(r, path);
      accept(v);
      return v.getResult();
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public void revertUpTo(Entry r, Change target, boolean revertTargetChange) {
    for (Change c : Reversed.list(myChanges)) {
      if (!revertTargetChange && c == target) return;
      c.revertOn(r);
      if (c == target) return;
    }
  }

  public List<Content> purgeObsolete(long period) {
    List<Change> newChanges = new ArrayList<Change>();
    List<Content> contentsToPurge = new ArrayList<Content>();

    int index = getIndexOfLastObsoleteChange(period);

    for (int i = index + 1; i < myChanges.size(); i++) {
      newChanges.add(myChanges.get(i));
    }

    for (int i = 0; i <= index; i++) {
      contentsToPurge.addAll(myChanges.get(i).getContentsToPurge());
    }

    myChanges = newChanges;
    return contentsToPurge;
  }

  private int getIndexOfLastObsoleteChange(long period) {
    long prevTimestamp = 0;
    long length = 0;

    for (int i = myChanges.size() - 1; i >= 0; i--) {
      Change c = myChanges.get(i);
      if (prevTimestamp == 0) prevTimestamp = c.getTimestamp();

      long delta = prevTimestamp - c.getTimestamp();
      prevTimestamp = c.getTimestamp();

      length += delta < getIntervalBetweenActivities() ? delta : 1;

      if (length >= period) return i;
    }

    return -1;
  }

  protected long getIntervalBetweenActivities() {
    return 12 * 60 * 60 * 1000;
  }

  private static class ChangeCollectingVisitor extends ChangeVisitor {
    private List<Change> myResult = new ArrayList<Change>();
    private Entry myRootCopy;
    private Change myChangeToAdd;
    private Entry myEntry;
    private IdPath myIdPath;
    private boolean myExists = true;
    private boolean myDoNotAddAnythingAlseFromCurrentChangeSet = false;

    public ChangeCollectingVisitor(Entry r, String path) {
      myRootCopy = r.copy();
      myEntry = myRootCopy.getEntry(path);
      myIdPath = myEntry.getIdPath();
    }

    public List<Change> getResult() {
      return new ArrayList<Change>(new LinkedHashSet<Change>(myResult));
    }

    @Override
    public void begin(ChangeSet c) {
      myChangeToAdd = c;
    }

    @Override
    public void end(ChangeSet c) {
      myChangeToAdd = null;
      myDoNotAddAnythingAlseFromCurrentChangeSet = false;
    }

    @Override
    public void visit(PutLabelChange c) {
      if (myChangeToAdd == null) {
        myChangeToAdd = c;
        doVisit(c);
        myChangeToAdd = null;
      }
      else {
        doVisit(c);
      }
    }

    @Override
    public void visit(StructuralChange c) {
      doVisit(c);
    }

    private void doVisit(Change c) {
      if (skippedDueToNonexistence(c)) return;
      addIfAffectsAndRevert(c);

      myIdPath = myEntry.getIdPath();
    }

    @Override
    public void visit(CreateEntryChange c) {
      if (skippedDueToNonexistence(c)) return;
      addIfAffectsAndRevert(c);
      if (c.isCreationalFor(myIdPath)) myExists = false;
    }

    @Override
    public void visit(DeleteChange c) {
      if (skippedDueToNonexistence(c)) {
        if (c.isDeletionOf(myIdPath)) myExists = true;
        myDoNotAddAnythingAlseFromCurrentChangeSet = true;
        if (myExists) myEntry = myRootCopy.getEntry(myIdPath);
        return;
      }

      addIfAffectsAndRevert(c);
      myIdPath = myEntry.getIdPath();
    }

    private void addIfAffectsAndRevert(Change c) {
      if (!myDoNotAddAnythingAlseFromCurrentChangeSet && c.affects(myIdPath)) {
        myResult.add(myChangeToAdd);
      }
      c.revertOn(myRootCopy);
    }

    private boolean skippedDueToNonexistence(Change c) {
      if (myExists) return false;

      c.revertOn(myRootCopy);
      return true;
    }
  }
}
