/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ui.ColumnInfo;


public interface VcsHistoryProvider {

  ColumnInfo[] getRevisionColumns();

  AnAction[] getAdditionalActions();

  String getHelpId();

  VcsHistorySession createSessionFor(FilePath filePath) throws VcsException;

  //return null if your revisions cannot be tree
  HistoryAsTreeProvider getTreeHistoryProvider();
}
