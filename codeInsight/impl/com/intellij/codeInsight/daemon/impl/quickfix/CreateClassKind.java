package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;

/**
 * @author ven
*/
public enum CreateClassKind {
  CLASS     (QuickFixBundle.message("create.class")),
  INTERFACE (QuickFixBundle.message("create.interface")),
  ENUM      (QuickFixBundle.message("create.enum"));

  private String myDescription;

  CreateClassKind(final String description) {
    myDescription = description;
  }

  public String getDescription() {
    return myDescription;
  }
}
