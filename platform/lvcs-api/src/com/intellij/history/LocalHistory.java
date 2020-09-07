// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LocalHistory {
  public static final Object VFS_EVENT_REQUESTOR = new Object();

  private static final class LocalHistoryHolder {
    static final LocalHistory ourInstance = ServiceManager.getService(LocalHistory.class);
  }

  @NotNull
  public static LocalHistory getInstance() {
    return LocalHistoryHolder.ourInstance;
  }

  public abstract LocalHistoryAction startAction(@Nullable @NlsContexts.Label String name);

  public abstract Label putSystemLabel(@NotNull Project p, @NotNull @NlsContexts.Label String name, int color);

  public Label putSystemLabel(@NotNull Project p, @NotNull @NlsContexts.Label String name) {
    return putSystemLabel(p, name, -1);
  }

  public abstract Label putUserLabel(@NotNull Project p, @NotNull @NlsContexts.Label String name);

  public abstract byte @Nullable [] getByteContent(@NotNull VirtualFile f, @NotNull FileRevisionTimestampComparator c);

  public abstract boolean isUnderControl(@NotNull VirtualFile f);
}
