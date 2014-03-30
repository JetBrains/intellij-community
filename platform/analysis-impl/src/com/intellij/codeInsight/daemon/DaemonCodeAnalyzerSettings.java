/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.NonNls;

public class DaemonCodeAnalyzerSettings {
  public static DaemonCodeAnalyzerSettings getInstance() {
    return ServiceManager.getService(DaemonCodeAnalyzerSettings.class);
  }

  public boolean NEXT_ERROR_ACTION_GOES_TO_ERRORS_FIRST = true;
  public int AUTOREPARSE_DELAY = 300;
  public boolean SHOW_ADD_IMPORT_HINTS = true;
  @NonNls public String NO_AUTO_IMPORT_PATTERN = "[a-z].?";
  public boolean SUPPRESS_WARNINGS = true;
  public boolean SHOW_METHOD_SEPARATORS = false;
  public int ERROR_STRIPE_MARK_MIN_HEIGHT = 3;
  public boolean SHOW_SMALL_ICONS_IN_GUTTER = true;

  public boolean isCodeHighlightingChanged(DaemonCodeAnalyzerSettings oldSettings) {
    return false;
  }

  public boolean isImportHintEnabled() {
    return SHOW_ADD_IMPORT_HINTS;
  }

  public void setImportHintEnabled(boolean isImportHintEnabled) {
    SHOW_ADD_IMPORT_HINTS = isImportHintEnabled;
  }

  public boolean isSuppressWarnings() {
    return SUPPRESS_WARNINGS;
  }

  public void setSuppressWarnings(boolean suppressWarnings) {
    SUPPRESS_WARNINGS = suppressWarnings;
  }
}
