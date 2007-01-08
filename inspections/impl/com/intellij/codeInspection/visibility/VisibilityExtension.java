package com.intellij.codeInspection.visibility;

import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.reference.RefManager;

public interface VisibilityExtension {
  void fillIgnoreList(RefManager refManager, ProblemDescriptionsProcessor processor);
}