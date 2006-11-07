package com.intellij.localvcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private ChangeList myChangeList = new ChangeList();
  private Snapshot mySnapshot = new Snapshot(myChangeList);
  private List<Change> myPendingChanges = new ArrayList<Change>();

  public LocalVcs() {}

  public LocalVcs(File dir) throws IOException {
    File f = new File(dir, "content");
    FileInputStream fs = new FileInputStream(f);
    try {
      Stream s = new Stream(fs);

      mySnapshot = new Snapshot(s.readChangeList(),
                                s.readEntry());
    } finally {
      // todo dont forget to test stream closing...
      fs.close();
    }
  }

  public void store(File dir) throws IOException {
    File f = new File(dir, "content");
    f.createNewFile();

    FileOutputStream fs = new FileOutputStream(f);
    try {
      Stream s = new Stream(fs);

      s.writeChangeList(myChangeList);
      s.writeEntry(mySnapshot.getRoot());
    } finally {
      // todo dont forget to test stream closing...
      fs.close();
    }
  }

  public boolean hasEntry(Path path) {
    return mySnapshot.getRoot().hasEntry(path);
  }

  public Entry getEntry(Path path) {
    return mySnapshot.getRoot().getEntry(path);
  }

  public List<Entry> getEntryHistory(Path path) {
    //todo optimize it
    List<Entry> result = new ArrayList<Entry>();

    // todo clean up this mess
    // todo should we raise exception?
    if (!mySnapshot.getRoot().hasEntry(path)) return result;

    Integer id = mySnapshot.getRoot().getEntry(path).getObjectId();

    for (Snapshot snapshot : getSnapshotHistory()) {
      if (!snapshot.getRoot().hasEntry(id)) break;
      result.add(snapshot.getRoot().getEntry(id));
    }

    return result;
  }

  public void createFile(Path path, String content) {
    myPendingChanges.add(new CreateFileChange(path, content));
  }

  public void changeFileContent(Path path, String content) {
    myPendingChanges.add(new ChangeFileContentChange(path, content));
  }

  public void rename(Path path, String newName) {
    myPendingChanges.add(new RenameChange(path, newName));
  }

  public void delete(Path path) {
    myPendingChanges.add(new DeleteChange(path));
  }

  public void commit() {
    mySnapshot = mySnapshot.apply(new ChangeSet(myPendingChanges));
    clearPendingChanges();
  }

  public void revert() {
    clearPendingChanges();

    Snapshot reverted = mySnapshot.getChangeList().revertOn(mySnapshot);
    if (reverted == null) return;
    mySnapshot = reverted;
  }

  private void clearPendingChanges() {
    myPendingChanges = new ArrayList<Change>();
  }

  public Boolean isClean() {
    return myPendingChanges.isEmpty();
  }

  public void putLabel(String label) {
    mySnapshot.getChangeList().setLabel(mySnapshot.getRoot(), label);
  }

  public Snapshot getSnapshot(String label) {
    for (Snapshot s : getSnapshotHistory()) {
      if (label.equals(s.getChangeList().getLabel(s.getRoot()))) return s;
    }
    return null;
  }

  public List<Snapshot> getSnapshotHistory() {
    List<Snapshot> result = new ArrayList<Snapshot>();

    Snapshot s = mySnapshot;
    while (s != null) {
      result.add(s);
      s = s.getChangeList().revertOn(s);
    }

    // todo bad hack, maybe replace with EmptySnapshot class
    result.remove(result.size() - 1);

    return result;
  }
}
