// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Storage for file types user selected in "Override File Type" action
 */
@State(name = "OverrideFileTypeManager", storages = @Storage("overrideFileTypes.xml"))
@Service
public final class OverrideFileTypeManager extends PersistentFileSetManager {
  public boolean isMarkedPlainText(@NotNull VirtualFile file) {
    return PlainTextFileType.INSTANCE.getName().equals(getFileValue(file));
  }

  public static OverrideFileTypeManager getInstance() {
    return ApplicationManager.getApplication().getService(OverrideFileTypeManager.class);
  }

  @TestOnly
  @ApiStatus.Internal
  public static void performTestWithMarkedAsPlainText(@NotNull VirtualFile file, @NotNull Runnable runnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    getInstance().addFile(file, PlainTextFileType.INSTANCE);
    UIUtil.dispatchAllInvocationEvents();
    try {
      runnable.run();
    }
    finally {
      getInstance().removeFile(file);
      UIUtil.dispatchAllInvocationEvents();
    }
  }
}
