package com.intellij.codeInspection.visibility;

import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.util.IgnorableRefFilter;

public interface VisibilityExtension {
  void fillIgnoreList(RefManager refManager, IgnorableRefFilter filter);
}