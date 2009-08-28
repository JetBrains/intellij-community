package com.intellij.history.integration.ui.models;

public interface RevisionProcessingProgress extends Progress {
  void processingLeftRevision();
  void processingRightRevision();
}
