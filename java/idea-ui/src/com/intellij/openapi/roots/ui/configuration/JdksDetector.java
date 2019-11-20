// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class JdksDetector {
  private static final Logger LOG = Logger.getInstance(JdksDetector.class);

  @NotNull
  public static JdksDetector getInstance() {
    return ApplicationManager.getApplication().getService(JdksDetector.class);
  }

  private final AtomicBoolean myIsRunning = new AtomicBoolean(false);
  private final Object myPublicationLock = new Object();
  private final Set<DetectedSdksListener> myListeners = new HashSet<>();
  private final List<Consumer<DetectedSdksListener>> myDetectedResults = new ArrayList<>();

  /**
   * The callback interface to deliver Sdk search results
   * back to the callee in EDT thread
   */
  public interface DetectedSdksListener {
    void onSdkDetected(@NotNull SdkType type, @Nullable String version, @NotNull String home);
    void onSearchStarted();
    void onSearchCompleted();
  }

  /**
   * Checks and registers the {@param listener} of only is not
   * yet registered. It is assumed the {@param component} is
   * included in the {@link DialogWrapper} so we could implement
   * the correct disposal logic (no SDKs are detected otherwise)
   *
   * The {@param listener} is populated immediately with all know-by-now
   * detected Sdk infos, the listener will be called in the EDT
   * thread to deliver more detected SDKs
   *
   * @param component the requestor component
   * @param listener  the callback interface
   */
  public void getDetectedSdksWithUpdate(@Nullable Project project,
                                        @NotNull Component component,
                                        @NotNull DetectedSdksListener listener) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    DialogWrapper dialogWrapper = DialogWrapper.findInstance(component);
    if (dialogWrapper == null) {
      LOG.warn("Cannot find disposable component parent for the subscription for " + component, new RuntimeException());
      return;
    }

    EdtDetectedSdksListener actualListener = new EdtDetectedSdksListener(ModalityState.stateForComponent(component), listener);
    synchronized (myPublicationLock) {
      //skip multiple registrations
      if (!myListeners.add(actualListener)) return;

      //it there is no other listeners, let's refresh
      if (myListeners.size() <= 1 && myIsRunning.compareAndSet(false, true)) {
        startSdkDetection(project, myMulticaster);
        myDetectedResults.clear();
      }

      //deliver everything we have
      myDetectedResults.forEach(result -> result.accept(actualListener));
    }

    Disposer.register(dialogWrapper.getDisposable(), () -> {
      myListeners.remove(actualListener);
    });
  }

  private final DetectedSdksListener myMulticaster = new DetectedSdksListener() {
    void logEvent(@NotNull Consumer<DetectedSdksListener> e) {
      myDetectedResults.add(e);
      for (DetectedSdksListener listener : myListeners) {
        e.accept(listener);
      }
    }

    @Override
    public void onSearchStarted() {
      synchronized (myPublicationLock) {
        myDetectedResults.clear();
        logEvent(listener -> listener.onSearchStarted());
      }
    }

    @Override
    public void onSdkDetected(@NotNull SdkType type, @Nullable String version, @NotNull String home) {
      synchronized (myPublicationLock) {
        logEvent(listener -> listener.onSdkDetected(type, version, home));
      }
    }

    @Override
    public void onSearchCompleted() {
      synchronized (myPublicationLock) {
        myIsRunning.set(false);
        logEvent(e -> e.onSearchCompleted());
      }
    }
  };

  private static void startSdkDetection(@Nullable Project project, @NotNull DetectedSdksListener callback) {
    Task.Backgroundable task = new Task.Backgroundable(
      project,
      "Detecting SDKs",
      true,
      PerformInBackgroundOption.ALWAYS_BACKGROUND) {

      private void detect(SdkType type, @NotNull ProgressIndicator indicator) {
        try {
          for (String path : new HashSet<>(type.suggestHomePaths())) {
            indicator.checkCanceled();
            if (project != null && project.isDisposed()) indicator.cancel();

            if (path == null) continue;

            try {
              if (!type.isValidSdkHome(path)) continue;
            }
            catch (Exception e) {
              LOG.warn("Failed to process detected SDK for " + type + " at " + path + ". " + e.getMessage(), e);
              continue;
            }

            String version;
            try {
              version = type.getVersionString(path);
            }
            catch (Exception e) {
              LOG.warn("Failed to get the detected SDK version for " + type + " at " + path + ". " + e.getMessage(), e);
              continue;
            }

            callback.onSdkDetected(type, version, path);
          }
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.warn("Failed to detect SDK: " + e.getMessage(), e);
        }
      }

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          callback.onSearchStarted();
          indicator.setIndeterminate(false);
          int item = 0;
          for (SdkType type : SdkType.getAllTypes()) {
            indicator.setFraction((float)item++ / SdkType.getAllTypes().length);
            indicator.checkCanceled();
            detect(type, indicator);
          }
        } finally {
          callback.onSearchCompleted();
        }
      }
    };

    ProgressManager.getInstance().run(task);
  }

  private static class EdtDetectedSdksListener implements DetectedSdksListener {
    private final ModalityState myState;
    private final DetectedSdksListener myTarget;

    EdtDetectedSdksListener(@NotNull ModalityState state,
                            @NotNull DetectedSdksListener target) {
      myState = state;
      myTarget = target;
    }

    void dispatch(@NotNull Runnable r) {
      ApplicationManager.getApplication().invokeLater(r, myState);
    }

    @Override
    public void onSdkDetected(@NotNull SdkType type, @Nullable String version, @NotNull String home) {
      dispatch(() -> myTarget.onSdkDetected(type, version, home));
    }

    @Override
    public void onSearchStarted() {
      dispatch(() -> myTarget.onSearchStarted());
    }

    @Override
    public void onSearchCompleted() {
      dispatch(() -> myTarget.onSearchCompleted());
    }

    @Override
    public int hashCode() {
      return myTarget.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof EdtDetectedSdksListener && Objects.equals(((EdtDetectedSdksListener)obj).myTarget, myTarget);
    }
  }
}
