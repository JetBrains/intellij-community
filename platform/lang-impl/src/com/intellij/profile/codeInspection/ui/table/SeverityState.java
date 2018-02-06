// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.lang.annotation.HighlightSeverity;

/**
 * @author Dmitry Batkovich
 */
public class SeverityState {

  private final HighlightSeverity mySeverity;
  private final boolean myDisabled;

  public SeverityState(HighlightSeverity severity, boolean disabled) {
    mySeverity = severity;
    myDisabled = disabled;
  }

  public HighlightSeverity getSeverity() {
    return mySeverity;
  }

  public boolean isDisabled() {
    return myDisabled;
  }
}
