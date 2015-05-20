/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Transient;

public class DaemonCodeAnalyzerSettings {
  public static DaemonCodeAnalyzerSettings getInstance() {
    return ServiceManager.getService(DaemonCodeAnalyzerSettings.class);
  }

  public boolean NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = true;
  public int AUTOREPARSE_DELAY = 300;
  protected boolean myShowAddImportHints = true;
  public String NO_AUTO_IMPORT_PATTERN = "[a-z].?";
  protected boolean mySuppressWarnings = true;
  public boolean SHOW_METHOD_SEPARATORS = false;
  public int ERROR_STRIPE_MARK_MIN_HEIGHT = 2;
  public boolean SHOW_SMALL_ICONS_IN_GUTTER = true;

  @Transient
  public boolean isCodeHighlightingChanged(DaemonCodeAnalyzerSettings oldSettings) {
    return false;
  }

  @OptionTag(value = "SHOW_ADD_IMPORT_HINTS")
  public boolean isImportHintEnabled() {
    return myShowAddImportHints;
  }

  public void setImportHintEnabled(boolean isImportHintEnabled) {
    myShowAddImportHints = isImportHintEnabled;
  }

  @OptionTag(value = "SUPPRESS_WARNINGS")
  public boolean isSuppressWarnings() {
    return mySuppressWarnings;
  }

  public void setSuppressWarnings(boolean suppressWarnings) {
    mySuppressWarnings = suppressWarnings;
  }
}
