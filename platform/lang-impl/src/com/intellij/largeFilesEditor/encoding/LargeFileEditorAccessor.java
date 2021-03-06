// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.encoding;

import com.intellij.largeFilesEditor.editor.LargeFileEditor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import org.jetbrains.annotations.Nullable;

public class LargeFileEditorAccessor {

  /**
   * @return null - if no access
   */
  public static @Nullable LargeFileEditorAccess getAccess(@Nullable StatusBar statusBar) {
    if (statusBar == null || statusBar.getProject() == null || statusBar.getProject().isDisposed()) return null;

    FileEditor fileEditor = StatusBarUtil.getCurrentFileEditor(statusBar);
    return fileEditor instanceof LargeFileEditor ? ((LargeFileEditor)fileEditor).createAccessForEncodingWidget() : null;
  }
}
