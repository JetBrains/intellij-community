// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.accessGettingPageTokens;

public enum Reason {
  NAVIGATION_BY_USER,
  SHOWING_SEARCH_RESULT,
  SAVING,
  CHANGING_ENCODING,
  REOPENING,
  UNDO,
  REDO,
}
