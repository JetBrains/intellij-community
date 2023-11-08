// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LocalHistory {
  public static final Object VFS_EVENT_REQUESTOR = new Object();

  @NotNull
  public static LocalHistory getInstance() {
    LocalHistory service = ApplicationManager.getApplication().getService(LocalHistory.class);
    return service != null ? service : new Dummy();
  }

  public abstract LocalHistoryAction startAction(@Nullable @NlsContexts.Label String name);

  public abstract Label putSystemLabel(@NotNull Project p, @NotNull @NlsContexts.Label String name, int color);

  public Label putSystemLabel(@NotNull Project p, @NotNull @NlsContexts.Label String name) {
    return putSystemLabel(p, name, -1);
  }

  public abstract Label putUserLabel(@NotNull Project p, @NotNull @NlsContexts.Label String name);

  public abstract byte @Nullable [] getByteContent(@NotNull VirtualFile f, @NotNull FileRevisionTimestampComparator c);

  public abstract boolean isUnderControl(@NotNull VirtualFile f);

  private static class Dummy extends LocalHistory {

    @Override
    public LocalHistoryAction startAction(@Nullable @NlsContexts.Label String name) {
      return LocalHistoryAction.NULL;
    }

    @Override
    public Label putSystemLabel(@NotNull Project p,
                                @NotNull @NlsContexts.Label String name, int color) {
      return Label.NULL_INSTANCE;
    }

    @Override
    public Label putUserLabel(@NotNull Project p,
                              @NotNull @NlsContexts.Label String name) {
      return Label.NULL_INSTANCE;
    }

    @Override
    public byte @Nullable [] getByteContent(@NotNull VirtualFile f,
                                            @NotNull FileRevisionTimestampComparator c) {
      return null;
    }

    @Override
    public boolean isUnderControl(@NotNull VirtualFile f) {
      return false;
    }
  }
}
