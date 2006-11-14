package com.intellij.localvcs;

public class Difference {

  public enum Kind {
    CREATED, MODIFIED, DELETED
  }

  private Kind myKind;
  private Entry myOlderEntry;
  private Entry myCurrentEntry;

  public Difference(Kind k) {
    myKind = k;
  }

  public Difference(Kind k, Entry olderEntry, Entry currentEntry) {
    myKind = k;
    myOlderEntry = olderEntry;
    myCurrentEntry = currentEntry;
  }

  public boolean isCreated() {
    return myKind.equals(Kind.CREATED);
  }

  public boolean isModified() {
    return myKind.equals(Kind.MODIFIED);
  }

  public boolean isDeleted() {
    return myKind.equals(Kind.DELETED);
  }

  public Entry getOlderEntry() {
    return myOlderEntry;
  }

  public Entry getCurrentEntry() {
    return myCurrentEntry;
  }
}
