package com.intellij.codeInspection.util;

import com.intellij.codeInspection.reference.RefElement;

public abstract class IgnorableRefFilter extends RefFilter {
  public abstract void addIgnoreList(RefElement refElement);
}