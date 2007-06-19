package com.intellij.localvcs.integration.ui.models;

public interface RevisionProcessingProgress extends Progress {
  void processingLeftRevision();
  void processingRightRevision();
}
