// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

public class ForceUndo {
  public static boolean IgnoreVFContentChanges = false;

  public static void ignoreVirtualFileContentChanges(Runnable runnable) {
    try {
      IgnoreVFContentChanges = true;
      runnable.run();
    } finally {
      IgnoreVFContentChanges = false;
    }
  }
}
