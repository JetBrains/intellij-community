/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs.ui;

/**
 * author: lesya
 */
public interface Refreshable {
  String PANEL = "Panel";

  void refresh();

  void saveState();
  void restoreState();
}
