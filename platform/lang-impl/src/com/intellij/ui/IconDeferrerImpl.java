// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.VirtualFileAppearanceListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.FixedHashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class IconDeferrerImpl extends IconDeferrer {
  private static final ThreadLocal<Boolean> evaluationIsInProgress = ThreadLocal.withInitial(() -> Boolean.FALSE);
  private final Object LOCK = new Object();
  private final Map<Object, Icon> myIconsCache = new FixedHashMap<>(SystemProperties.getIntProperty("ide.icons.deferrerCacheSize", 1000)); // guarded by LOCK
  private long myLastClearTimestamp; // guarded by LOCK

  public IconDeferrerImpl() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(PsiModificationTracker.TOPIC, this::clearCache);
    // update "locked" icon
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        clearCache();
      }
    });
    connection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        clearCache();
      }
    });
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, __ -> clearCache());
    LowMemoryWatcher.register(this::clearCache, connection);
  }

  @Override
  public void clearCache() {
    synchronized (LOCK) {
      myIconsCache.clear();
      myLastClearTimestamp++;
    }
  }

  @Override
  public <T> @NotNull Icon defer(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> evaluator) {
    if (evaluationIsInProgress.get().booleanValue()) {
      return evaluator.apply(param);
    }

    synchronized (LOCK) {
      Icon cached = myIconsCache.get(param);
      if (cached != null) {
        return cached;
      }
      long started = myLastClearTimestamp;
      Icon result = new DeferredIconImpl<>(base, param, true, evaluator, (DeferredIcon source, Icon r) -> {
        synchronized (LOCK) {
          // check if our result is not outdated yet
          if (started == myLastClearTimestamp) {
            myIconsCache.put(((DeferredIconImpl<?>)source).myParam, r);
          }
        }
      });
      myIconsCache.put(param, result);
      return result;
    }
  }

  static void evaluateDeferred(@NotNull Runnable runnable) {
    try {
      evaluationIsInProgress.set(Boolean.TRUE);
      runnable.run();
    }
    finally {
      evaluationIsInProgress.set(Boolean.FALSE);
    }
  }

  @Override
  public boolean equalIcons(Icon icon1, Icon icon2) {
    return DeferredIconImpl.equalIcons(icon1, icon2);
  }
}
