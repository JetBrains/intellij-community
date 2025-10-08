// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.util.concurrent.atomic.AtomicInteger;

public class DaemonCodeAnalyzerSettings {
  public static final int AUTOREPARSE_DELAY_DEFAULT = 300;

  private boolean myNextErrorActionGoesToErrorsFirst = true;
  private int myAutoReparseDelay = AUTOREPARSE_DELAY_DEFAULT;
  private int myErrorStripeMarkMinHeight = 2;

  private boolean mySuppressWarnings = true;

  @Transient
  private final AtomicInteger myForceZeroAutoReparseDelay = new AtomicInteger();

  public static DaemonCodeAnalyzerSettings getInstance() {
    return ApplicationManager.getApplication().getService(DaemonCodeAnalyzerSettings.class);
  }

  @OptionTag("NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST")
  public boolean isNextErrorActionGoesToErrorsFirst() {
    return myNextErrorActionGoesToErrorsFirst;
  }

  public void setNextErrorActionGoesToErrorsFirst(boolean value) {
    myNextErrorActionGoesToErrorsFirst = value;
  }

  @SuppressWarnings("SpellCheckingInspection")
  @OptionTag("AUTOREPARSE_DELAY")
  public int getAutoReparseDelay() {
    return myAutoReparseDelay;
  }

  public void setAutoReparseDelay(int millis) {
    myAutoReparseDelay = millis;
  }

  @ApiStatus.Internal
  public void forceUseZeroAutoReparseDelay(boolean useZeroAutoReparseDelay) {
    if (useZeroAutoReparseDelay) {
      myForceZeroAutoReparseDelay.incrementAndGet();
    }
    else {
      myForceZeroAutoReparseDelay.decrementAndGet();
    }
  }

  @Transient
  @ApiStatus.Internal
  public int getEffectiveAutoReparseDelay() {
    if (myForceZeroAutoReparseDelay.get() > 0) return 0;

    return myAutoReparseDelay;
  }

  @OptionTag("ERROR_STRIPE_MARK_MIN_HEIGHT")
  public int getErrorStripeMarkMinHeight() {
    return myErrorStripeMarkMinHeight;
  }

  public void setErrorStripeMarkMinHeight(int value) {
    myErrorStripeMarkMinHeight = value;
  }

  protected boolean myShowAddImportHints = true;
  public @NonNls String NO_AUTO_IMPORT_PATTERN = "[a-z].?";
  public boolean SHOW_METHOD_SEPARATORS;

  @Transient
  public boolean isCodeHighlightingChanged(DaemonCodeAnalyzerSettings oldSettings) {
    return false;
  }

  @OptionTag("SHOW_ADD_IMPORT_HINTS")
  public boolean isImportHintEnabled() {
    return myShowAddImportHints;
  }

  public void setImportHintEnabled(boolean isImportHintEnabled) {
    myShowAddImportHints = isImportHintEnabled;
  }

  @OptionTag("SUPPRESS_WARNINGS")
  public boolean isSuppressWarnings() {
    return mySuppressWarnings;
  }

  public void setSuppressWarnings(boolean suppressWarnings) {
    mySuppressWarnings = suppressWarnings;
  }
}
