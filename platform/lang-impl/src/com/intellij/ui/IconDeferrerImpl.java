// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.ide.ui.VirtualFileAppearanceListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.SystemProperties;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class IconDeferrerImpl extends IconDeferrer {
  private static final ThreadLocal<Boolean> evaluationIsInProgress = ThreadLocal.withInitial(() -> Boolean.FALSE);

  private final Cache<Object, Icon> iconCache = Caffeine.newBuilder()
    // registry should be not used as at this point of time user registry maybe not yet loaded
    .maximumSize(SystemProperties.getIntProperty("ide.icons.deferrerCacheSize", 1000))
    // some icon implementations, e.g. ElementBase$ElementIconRequest, requires read action, so, perform cleanup in the requester thread
    .executor(command -> {
      try {
        command.run();
      }
      catch (ProcessCanceledException ignore) {
        // otherwise Caffeine will log it as a warning
      }
    })
    .build();

  private final AtomicLong lastClearTimestamp = new AtomicLong();

  public IconDeferrerImpl() {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(PsiModificationTracker.TOPIC, this::clearCache);
    // update "locked" icon
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        clearCache();
      }
    });
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosed(@NotNull Project project) {
        clearCache();
      }
    });
    connection.subscribe(VirtualFileAppearanceListener.TOPIC, new VirtualFileAppearanceListener() {
      @Override
      public void virtualFileAppearanceChanged(@NotNull VirtualFile virtualFile) {
        clearCache();
      }
    });
    LowMemoryWatcher.register(this::clearCache, connection);
  }

  @Override
  public final void clearCache() {
    lastClearTimestamp.incrementAndGet();
    iconCache.invalidateAll();
  }

  @Override
  public <T> @NotNull Icon defer(@Nullable Icon base, T param, @NotNull Function<? super T, ? extends Icon> evaluator) {
    return deferImpl(base, param, evaluator, false);
  }

  @Override
  public <T> Icon deferAutoUpdatable(Icon base, T param, @NotNull Function<? super T, ? extends Icon> evaluator) {
    return deferImpl(base, param, evaluator, true);
  }

  private <T> @NotNull Icon deferImpl(Icon base, T param, @NotNull Function<? super T, ? extends Icon> evaluator, final boolean autoUpdatable) {
    if (evaluationIsInProgress.get().booleanValue()) {
      return evaluator.apply(param);
    }

    return Objects.requireNonNull(iconCache.get(param, param1 -> {
      long started = lastClearTimestamp.get();
      //noinspection unchecked
      T key = (T)param1;
      return new DeferredIconImpl<>(base, key, evaluator, (source, result) -> {
        // check if our results is not outdated yet
        if (started == lastClearTimestamp.get()) {
          iconCache.put(key, autoUpdatable ? source : result);
        }
      }, autoUpdatable);
    }));
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
