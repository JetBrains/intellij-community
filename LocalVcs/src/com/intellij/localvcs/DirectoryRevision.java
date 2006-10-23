package com.intellij.localvcs;

public interface DirectoryRevision extends Revision {
  Integer size();

  Revision get(Integer index);
}
