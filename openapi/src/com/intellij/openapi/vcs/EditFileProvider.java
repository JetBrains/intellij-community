/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * author: lesya
 */
public interface EditFileProvider {
  void editFiles(VirtualFile[] files);

  String getRequestText();
}
