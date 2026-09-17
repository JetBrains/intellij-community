// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.diagnostic.LoadingState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public abstract class RemoteDesktopService {
  private static final Supplier<RemoteDesktopService> ourInstance = CachedSingletonsRegistry.lazy(() -> {
    return ApplicationManager.getApplication().getService(RemoteDesktopService.class);
  });

  public static RemoteDesktopService getInstance() {
    return ourInstance.get();
  }

  public static boolean isRemoteSession() {
    if (!SystemInfoRt.isWindows) {
      return false;
    }
    if (!LoadingState.COMPONENTS_REGISTERED.isOccurred() || ApplicationManager.getApplication() == null) {
      return false;
    }
    RemoteDesktopService instance = getInstance();
    return instance != null && instance.isRemoteDesktopConnected();
  }

  /** An implementation calls {@link #fireRemoteSessionChanged} after this answer changes. */
  public abstract boolean isRemoteDesktopConnected();

  @ApiStatus.Experimental
  public interface Listener {
    /**
     * Read {@link RemoteDesktopService#isRemoteSession()} to get the new value.
     * The platform serializes the calls, but does not specify the thread.
     */
    void remoteSessionChanged();
  }

  /**
   * Calls {@code listener} once, and again after each change of {@link #isRemoteSession()}.
   * The subscription ends when {@code parent} is disposed.
   * <p>
   * The first call can run before this method returns, or later.
   * Read {@link #isRemoteSession()} when you need the value at once.
   * <p>
   * Call this method after the application registers its components.
   */
  @ApiStatus.Experimental
  public static void subscribe(@NotNull Disposable parent, @NotNull Listener listener) {
    LoadingState.COMPONENTS_REGISTERED.checkOccurred();
    Application app = ApplicationManager.getApplication();
    CheckedDisposable subscription = Disposer.newCheckedDisposable(parent);
    app.getMessageBus().connect(subscription).subscribe(TOPIC, listener);
    if (app.isDispatchThread()) {
      // every other call is posted to this thread, so nothing runs between the subscription and this call
      listener.remoteSessionChanged();
      return;
    }
    app.invokeLater(() -> {
      if (!subscription.isDisposed()) {
        listener.remoteSessionChanged();
      }
    }, ModalityState.any());
  }

  /**
   * An implementation of {@link #isRemoteDesktopConnected} calls this after its answer changes.
   * A redundant call is harmless. A subscriber reads {@link #isRemoteSession()} again.
   * <p>
   * The call posts to the same queue as {@link #subscribe}, so a subscriber sees one order.
   */
  @ApiStatus.Experimental
  public static void fireRemoteSessionChanged() {
    Application app = ApplicationManager.getApplication();
    if (app == null) {
      return;
    }
    app.invokeLater(() -> {
      if (!app.isDisposed()) {
        app.getMessageBus().syncPublisher(TOPIC).remoteSessionChanged();
      }
    }, ModalityState.any());
  }

  @Topic.AppLevel
  private static final Topic<Listener> TOPIC = new Topic<>(
    "RemoteDesktopService.Listener",
    Listener.class,
    Topic.BroadcastDirection.TO_DIRECT_CHILDREN
  );
}
