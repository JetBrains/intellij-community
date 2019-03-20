// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.changes;

import com.intellij.openapi.editor.ex.DocumentEx;
import org.jetbrains.annotations.CalledInAwt;

public interface Change {

  long getTimeStamp();

  void performUndo(StringBuilder text);

  @CalledInAwt
  void performUndo(DocumentEx document);

  @CalledInAwt
  void performRedo(StringBuilder text);

  @CalledInAwt
  void performRedo(DocumentEx document);
}
