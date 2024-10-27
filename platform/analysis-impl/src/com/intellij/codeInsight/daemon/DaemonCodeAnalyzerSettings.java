// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

public class DaemonCodeAnalyzerSettings {
  private static final int SAFE_AUTO_REPARSE_DELAY_MS = 100;

  private boolean myNextErrorActionGoesToErrorsFirst = true;
  private int myAutoReparseDelay = 300;
  private int myErrorStripeMarkMinHeight = 2;

  private boolean mySuppressWarnings = true;

  private boolean myUseZeroAutoReparseDelay = false;

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

  @ApiStatus.Internal
  public int chooseSafeAutoReparseDelay() {
    if (myUseZeroAutoReparseDelay) return 0;
    if (ApplicationManager.getApplication().isUnitTestMode()) return myAutoReparseDelay;

    return Math.max(myAutoReparseDelay, SAFE_AUTO_REPARSE_DELAY_MS);
  }

  public void setAutoReparseDelay(int millis) {
    myAutoReparseDelay = millis;
  }

  @ApiStatus.Internal
  public void forceUseZeroAutoReparseDelay(boolean useZeroAutoReparseDelay) {
    myUseZeroAutoReparseDelay = useZeroAutoReparseDelay;
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
