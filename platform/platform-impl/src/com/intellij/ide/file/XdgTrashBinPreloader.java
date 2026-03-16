// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.file;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.TrashBin;
import com.intellij.util.system.OS;

final class XdgTrashBinPreloader implements AppLifecycleListener {
  @Override
  public void appStarted() {
    if (OS.isGenericUnix()) {
      Logger.getInstance(XdgTrashBinPreloader.class).info("supported: " + TrashBin.isSupported());
    }
  }
}
