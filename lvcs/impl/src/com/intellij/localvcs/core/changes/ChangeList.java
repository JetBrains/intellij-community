package com.intellij.localvcs.core.changes;

import com.intellij.localvcs.core.IdPath;
import com.intellij.localvcs.core.storage.Content;
import com.intellij.localvcs.core.storage.Stream;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.utils.Reversed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class ChangeList {
  private List<Change> myChanges = new ArrayList<Change>();

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

  // todo test support
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

  public boolean isInTheChain(Change before, Change after) {
    List<Change> cc = new ArrayList<Change>();
    for (Change c : myChanges) {
      if (c == before) {
        cc.add(c);
        continue;
      }
      if (c.affectsSameAs(cc)) cc.add(c);
    }
    return after.affectsSameAs(cc);
  }

  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    for (Change change : Reversed.list(myChanges)) {
      change.accept(v);
    }
  }

  public List<Change> getChangesFor(Entry r, String path) {
    final Entry rootCopy = r.copy();
    final Entry[] e = new Entry[]{rootCopy.getEntry(path)};

    final List<Change> result = new ArrayList<Change>();
    final IdPath[] idPath = new IdPath[]{e[0].getIdPath()};
    final boolean[] exists = new boolean[]{true};

    for (final Change cs : Reversed.list(myChanges)) {
      try {
        cs.accept(new ChangeVisitor() {
          private Change myChangeToAdd;

          @Override
          public void visit(ChangeSet c) {
            myChangeToAdd = c;
          }

          @Override
          public void visit(PutLabelChange c) {
            myChangeToAdd = c;
            visit((Change)c);
          }

          @Override
          public void visit(Change c) {
            if (!exists[0]) {
              c.revertOn(rootCopy);
            }
            else {
              if (c.affects(idPath[0])) result.add(cs);
              c.revertOn(rootCopy);
              idPath[0] = e[0].getIdPath();
            }
          }

          @Override
          public void visit(CreateEntryChange c) {
            if (exists[0]) {
              if (c.affects(idPath[0])) result.add(myChangeToAdd);
              if (c.getAffectedIdPaths()[0].equals(idPath[0])) {
                exists[0] = false;
              }
            }
            c.revertOn(rootCopy);
          }

          @Override
          public void visit(DeleteChange c) {
            if (!exists[0]) {
              if (idPath[0].startsWith(c.getAffectedIdPaths()[0])) {
                exists[0] = true;
              }
              c.revertOn(rootCopy);
              if (exists[0]) e[0] = rootCopy.getEntry(idPath[0]);
            }
            else {
              if (c.affects(idPath[0])) result.add(myChangeToAdd);
              c.revertOn(rootCopy);
              idPath[0] = e[0].getIdPath();
            }
          }
        });
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      catch (ChangeVisitor.StopVisitingException ex) {
      }
    }

    return new ArrayList<Change>(new LinkedHashSet<Change>(result));
  }

  public void addChange(Change c) {
    myChanges.add(c);
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
}
