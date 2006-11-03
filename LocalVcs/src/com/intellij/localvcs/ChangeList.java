package com.intellij.localvcs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChangeList {
  private List<ChangeSet> myChangeSets = new ArrayList<ChangeSet>();

  public ChangeList() {
    // todo a but of hack
    add(new ChangeSet(new ArrayList<Change>()));
  }

  public ChangeList(File sourceDir) {
    this();
  }

  public List<ChangeSet> getChangeSets() {
    return myChangeSets;
  }

  public void add(ChangeSet cs) {
    myChangeSets.add(cs);
  }

  public void removeLast() {
    myChangeSets.remove(getLast());
  }

  public Boolean hasOnlyOneChangeSet() {
    return myChangeSets.size() == 1;
  }

  public ChangeSet getLast() {
    return myChangeSets.get(myChangeSets.size() - 1);
  }

  public ChangeList copy() {
    ChangeList result = new ChangeList();

    result.myChangeSets.clear();
    result.myChangeSets.addAll(myChangeSets);

    return result;
  }

  public void save(File outputDir) throws IOException {
    File f = new File(outputDir, "changes");
    f.createNewFile();
  }
}
