// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class JdksDetector {
  private static final Logger LOG = Logger.getInstance(JdksDetector.class);

  private final AtomicBoolean myIsRunning = new AtomicBoolean(false);
  private final AtomicBoolean myIsCompleted = new AtomicBoolean(false);

  private final Queue<Consumer<DetectedSdksListener>> myDetectedResults = new LinkedBlockingQueue<>();

  @NotNull
  public static JdksDetector getInstance() {
    return ApplicationManager.getApplication().getService(JdksDetector.class);
  }

  public interface DetectedSdksListener {
    void onSdkDetected(@NotNull SdkType type, @Nullable String version, @NotNull String home);
    void setSearchIsRunning(boolean running);
    void onSearchRestarted();
  }

  // events are published in the background thread,
  // we use the dispatchToComponent adapter to deliver messages
  // in EDT
  private static final Topic<DetectedSdksListener> ON_SDK_RESOLVED
    = Topic.create("on-sdk-detected", DetectedSdksListener.class);

  private void onNewSdkDetected(@NotNull SdkType type,
                                @Nullable String version,
                                @NotNull String home) {

    Consumer<DetectedSdksListener> e = listener -> listener.onSdkDetected(type, version, home);
    myDetectedResults.add(e);
    e.accept(ApplicationManager.getApplication().getMessageBus().syncPublisher(ON_SDK_RESOLVED));
  }


  private void onSdkSearchCompleted() {
    myIsCompleted.set(true);
    ApplicationManager.getApplication().getMessageBus().syncPublisher(ON_SDK_RESOLVED).setSearchIsRunning(false);
  }

  /**
   * The {@param listener} is populated immediately with all know-by-now
   * detected SDK infos, next the listener will be called in the EDT
   * thread to deliver more detected SDKs. See {@link DetectedSdksListener}
   * for more info
   *
   * @param lifetime  the subscription lifetime
   * @param component the requestor component
   * @param listener  the callback interface
   */
  public void getDetectedSdksWithUpdate(@NotNull Disposable lifetime,
                                        @NotNull Component component,
                                        @NotNull DetectedSdksListener listener) {
    detectOrUpdateSdks();

    //TODO[jo]: add refresh action?

    listener.onSearchRestarted();

    //TODO[jo]: sync publication of new elements with that?
    for (Consumer<DetectedSdksListener> result : myDetectedResults) {
      result.accept(listener);
    }

    listener.setSearchIsRunning(myIsRunning.get() && !myIsCompleted.get());
    ApplicationManager.getApplication().getMessageBus()
      .connect(lifetime)
      .subscribe(
        ON_SDK_RESOLVED,
        dispatchToComponent(ModalityState.stateForComponent(component), listener));
  }

  private void detectOrUpdateSdks() {
    //TODO[jo]: recheck it once needed, do we need FS events for that?

    if (myIsCompleted.get()) return;
    if (!myIsRunning.compareAndSet(false, true)) return;

    Task.Backgroundable task = new Task.Backgroundable(
      /*TODO[jo]*/null,
                  "Detecting SDKs",
                  true,
                  PerformInBackgroundOption.ALWAYS_BACKGROUND) {

      private void detect(SdkType type, @NotNull ProgressIndicator indicator) {
        try {
          //TODO[jo]: add EP here to suggest more home paths (one from the JdkDownloader)
          for (String path : new HashSet<>(type.suggestHomePaths())) {
            indicator.checkCanceled();
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

            onNewSdkDetected(type, version, path);
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
        indicator.setIndeterminate(false);
        int item = 0;
        for (SdkType type : SdkType.getAllTypes()) {
          indicator.setFraction((float)item++ / SdkType.getAllTypes().length);
          indicator.checkCanceled();
          detect(type, indicator);
        }

        onSdkSearchCompleted();
      }
    };

    ProgressManager.getInstance().run(task);
  }

  @NotNull
  private static DetectedSdksListener dispatchToComponent(@NotNull ModalityState state,
                                                          @NotNull DetectedSdksListener target) {
    return new DetectedSdksListener() {
      private void dispatch(Runnable r) {
        ApplicationManager.getApplication().invokeLater(r, state);
      }

      @Override
      public void onSdkDetected(@NotNull SdkType type, @Nullable String version, @NotNull String home) {
        dispatch(() -> target.onSdkDetected(type, version, home));
      }

      @Override
      public void setSearchIsRunning(boolean running) {
        dispatch(() -> target.setSearchIsRunning(running));
      }

      @Override
      public void onSearchRestarted() {
        dispatch(() -> target.onSearchRestarted());
      }
    };
  }
}
