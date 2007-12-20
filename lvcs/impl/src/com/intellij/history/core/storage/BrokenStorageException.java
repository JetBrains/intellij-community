package com.intellij.history.core.storage;

// todo this class is needed only becase we cannot create IOException with cause - it will be possible in java 1.6
public class BrokenStorageException extends Exception {
  public BrokenStorageException() {
  }

  public BrokenStorageException(Throwable e) {
    super(e);
  }
}
