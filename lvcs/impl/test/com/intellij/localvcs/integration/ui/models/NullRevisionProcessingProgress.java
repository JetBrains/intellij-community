package com.intellij.localvcs.integration.ui.models;

public class NullRevisionProcessingProgress implements RevisionProcessingProgress {
  public void processingLeftRevision() {
  }

  public void processingRightRevision() {
  }

  public void processed(int percentage) {
  }
}
