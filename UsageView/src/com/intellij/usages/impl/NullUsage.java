package com.intellij.usages.impl;

import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.usages.Usage;
import com.intellij.usages.UsagePresentation;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
class NullUsage implements Usage {
  @NotNull
  public UsagePresentation getPresentation() {
    throw new IllegalAccessError();
  }

  public boolean isValid() {
    return false;
  }

  public boolean isReadOnly() {
    return false;
  }

  public FileEditorLocation getLocation() {
    return null;
  }

  public void selectInEditor() {

  }

  public void highlightInEditor() {

  }

  public void navigate(final boolean requestFocus) {

  }

  public boolean canNavigate() {
    return false;
  }

  public boolean canNavigateToSource() {
    return false;
  }
}
