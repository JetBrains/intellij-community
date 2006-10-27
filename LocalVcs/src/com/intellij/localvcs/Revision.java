package com.intellij.localvcs;

public abstract class Revision {
  public abstract Integer getObjectId();

  public abstract String getName();

  public abstract String getContent();
}
