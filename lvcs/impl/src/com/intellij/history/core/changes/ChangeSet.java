package com.intellij.history.core.changes;

import com.intellij.history.core.IdPath;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Stream;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.utils.Reversed;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeSet extends Change {
  private String myName;
  private long myTimestamp;
  private List<Change> myChanges;

  public ChangeSet(long timestamp, String name, List<Change> changes) {
    myTimestamp = timestamp;
    myChanges = changes;
    myName = name;
  }

  public ChangeSet(long timestamp) {
    myTimestamp = timestamp;
    myChanges = new ArrayList<Change>();
  }

  public ChangeSet(Stream s) throws IOException {
    // todo get rid of null here
    myName = s.readStringOrNull();
    myTimestamp = s.readLong();

    int count = s.readInteger();
    myChanges = new ArrayList<Change>(count);
    while (count-- > 0) {
      myChanges.add(s.readChange());
    }
  }

  public void write(Stream s) throws IOException {
    s.writeStringOrNull(myName);
    s.writeLong(myTimestamp);

    s.writeInteger(myChanges.size());
    for (Change c : myChanges) {
      s.writeChange(c);
    }
  }

  public void setName(String name) {
    myName = name;
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public long getTimestamp() {
    return myTimestamp;
  }

  public void addChange(Change c) {
    myChanges.add(c);
  }

  @Override
  public List<Change> getChanges() {
    return myChanges;
  }

  @Override
  public void applyTo(Entry r) {
    for (Change c : myChanges) {
      c.applyTo(r);
    }
  }

  @Override
  public void revertOn(Entry e) {
    for (Change c : Reversed.list(myChanges)) c.revertOn(e);
  }

  @Override
  protected boolean affects(IdPath... pp) {
    for (Change c : myChanges) {
      if (c.affects(pp)) return true;
    }
    return false;
  }

  @Override
  public boolean affectsSameAs(List<Change> cc) {
    for (Change c : myChanges) {
      if (c.affectsSameAs(cc)) return true;
    }
    return false;
  }

  @Override
  public boolean affectsOnlyInside(Entry e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isCreationalFor(Entry e) {
    for (Change c : myChanges) {
      if (c.isCreationalFor(e)) return true;
    }
    return false;
  }

  @Override
  public List<Content> getContentsToPurge() {
    List<Content> result = new ArrayList<Content>();
    for (Change c : myChanges) {
      result.addAll(c.getContentsToPurge());
    }
    return result;
  }

  @Override
  public boolean isFileContentChange() {
    return myChanges.size() == 1 && myChanges.get(0).isFileContentChange();
  }

  @Override
  public void accept(ChangeVisitor v) throws IOException, ChangeVisitor.StopVisitingException {
    v.begin(this);
    for (Change c : Reversed.list(myChanges)) {
      c.accept(v);
    }
    v.end(this);
  }
}
