// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorListener;
import com.intellij.openapi.progress.util.RelayUiToDelegateIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SdkDownloadTracker {
  private static final Logger LOG = Logger.getInstance(SdkDownloadTracker.class);

  public static @NotNull SdkDownloadTracker getInstance() {
    return ApplicationManager.getApplication().getService(SdkDownloadTracker.class);
  }

  private final List<PendingDownload> myPendingTasks = new CopyOnWriteArrayList<>();

  public SdkDownloadTracker() {
    ApplicationManager.getApplication().getMessageBus().simpleConnect()
      .subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, new ProjectJdkTable.Listener() {
        @Override
        public void jdkRemoved(@NotNull Sdk jdk) {
          onSdkRemoved(jdk);
        }
      });
  }

  public void onSdkRemoved(@NotNull Sdk sdk) {
    //can be executed in any thread too, JMM safe

    PendingDownload task = findTask(sdk);
    if (task == null) return;
    task.cancel();
  }

  @RequiresEdt
  private void removeTask(@NotNull PendingDownload task) {
    myPendingTasks.remove(task);
  }

  private @Nullable PendingDownload findTask(@NotNull Sdk sdk) {
    for (PendingDownload task : myPendingTasks) {
      if (task.containsSdk(sdk)) {
        return task;
      }
    }
    return null;
  }

  public void registerEditableSdk(@NotNull Sdk original,
                                  @NotNull Sdk editable) {
    // This may happen in the background thread on a project open (JMM safe)
    PendingDownload task = findTask(original);
    if (task == null) return;

    LOG.assertTrue(findTask(editable) == null, "Download is already running for the SDK " + editable);
    task.registerEditableSdk(editable);
  }

  @RequiresEdt
  public void registerSdkDownload(@NotNull Sdk originalSdk,
                                  @NotNull SdkDownloadTask item) {
    LOG.assertTrue(findTask(originalSdk) == null, "Download is already running for the SDK " + originalSdk);

    PendingDownload pd = new PendingDownload(originalSdk, item, new SmartPendingDownloadModalityTracker());
    configureSdk(originalSdk, item);
    myPendingTasks.add(pd);
  }

  /**
   * Allows to register a callback to cleanup the created SDK object, in a case it was failed to download
   */
  public void tryRegisterSdkDownloadFailureHandler(@NotNull Sdk originalSdk,
                                                   @NotNull Runnable onSdkFailed) {
    PendingDownload task = findTask(originalSdk);
    if (task == null) return;
    task.mySdkFailedHandlers.add(onSdkFailed);
  }

  @RequiresEdt
  public void startSdkDownloadIfNeeded(@NotNull Sdk sdkFromTable) {
    PendingDownload task = findTask(sdkFromTable);
    if (task == null) return;

    task.startDownloadIfNeeded(sdkFromTable);
  }

  /**
   * Looks into the currently downloading SDK instances
   * and returns one with matching name
   */
  public @NotNull List<Sdk> findDownloadingSdks(@Nullable String sdkName) {
    if (sdkName == null) return Collections.emptyList();

    List<Sdk> result = new ArrayList<>();
    for (PendingDownload task : myPendingTasks) {
      for (Sdk sdk : task.myEditableSdks.copy()) {
        if (Objects.equals(sdkName, sdk.getName())) {
          result.add(sdk);
        }
      }
    }

    return result;
  }

  /**
   * Checks if there is an activity for a given Sdk
   * @return true is there is an activity
   */
  public boolean isDownloading(@NotNull Sdk sdk) {
    return findTask(sdk) != null;
  }

  /**
   * Checks if there is an activity for a given Sdk and subscribes a listeners if there is an activity
   *
   * @param sdk                        the Sdk instance that to check (it could be it's #clone())
   * @param lifetime                   unsubscribe callback
   * @param indicator                  progress indicator to deliver progress
   * @param onDownloadCompleteCallback called once download is completed from EDT thread,
   *                                   with {@code true} to indicate success and {@code false} for a failure
   * @return true if the given Sdk is downloading right now
   */
  @RequiresEdt
  public boolean tryRegisterDownloadingListener(@NotNull Sdk sdk,
                                                @NotNull Disposable lifetime,
                                                @Nullable ProgressIndicator indicator,
                                                @NotNull Consumer<? super Boolean> onDownloadCompleteCallback) {
    PendingDownload pd = findTask(sdk);
    if (pd == null) return false;

    pd.registerListener(lifetime, indicator, onDownloadCompleteCallback);
    return true;
  }

  /**
   * Performs synchronous SDK download. Must not run on EDT thread.
   */
  @RequiresBackgroundThread
  public void downloadSdk(@NotNull SdkDownloadTask task,
                          @NotNull List<? extends Sdk> sdks,
                          @NotNull ProgressIndicator indicator) {
    if (sdks.isEmpty()) throw new IllegalArgumentException("There must be at least one SDK in the list for " + task);
    @NotNull Sdk sdk = Objects.requireNonNull(ContainerUtil.getFirstItem(sdks));

    var tracker = new PendingDownloadModalityTracker() {
      @Override
      public void updateModality() { }

      @Override
      public void invokeLater(@NotNull Runnable r) {
        //we need to make sure our tasks are executed in-place for the blocking mode
        ApplicationManager.getApplication().invokeAndWait(r);
      }
    };

    PendingDownload pd = new PendingDownload(sdk, task, tracker) {
      @Override
      protected void runTask(@NotNull @NlsContexts.ProgressTitle String title, @NotNull Progressive progressive) {
        indicator.pushState();
        try {
          progressive.run(indicator);
        } finally {
          indicator.popState();
        }
      }

      @Override
      protected void handleDownloadError(@NotNull SdkType type, @NotNull @Nls String title, @NotNull Throwable exception) {
        throw new RuntimeException("Failed to download and configure " + type.getPresentableName() + " for "
                         + myEditableSdks.copy() + ". " + exception.getMessage(), exception);
      }
    };
    myPendingTasks.add(pd);
    tracker.invokeLater(() -> sdks.forEach(pd::configureSdk));

    for (Sdk otherSdk : sdks) {
      if (otherSdk == sdk) continue;
      pd.registerEditableSdk(otherSdk);
    }

    pd.startDownloadIfNeeded(sdk);
  }

  // we need to track the "best" modality state to trigger SDK update on completion,
  // while the current Project Structure dialog is shown. The ModalityState from our
  // background task (ProgressManager.run) does not suite if the Project Structure dialog
  // is re-open once again.
  private interface PendingDownloadModalityTracker {
    void updateModality();
    void invokeLater(@NotNull Runnable r);
  }

  // We grab the modalityState from the {@link #tryRegisterDownloadingListener} call and
  // see if that {@link ModalityState#dominates} the current modality state. In fact,
  // it does call the method from the dialog setup, with NON_MODAL modality, which
  // we would like to ignore.
  private static final class SmartPendingDownloadModalityTracker implements PendingDownloadModalityTracker{
    static @NotNull ModalityState modality() {
      ModalityState state = ModalityState.current();
      TransactionGuard.getInstance().assertWriteSafeContext(state);
      return state;
    }

    ModalityState myModalityState = modality();

    @Override
    public synchronized void updateModality() {
      ModalityState newModality = modality();
      if (newModality != myModalityState && newModality.dominates(myModalityState)) {
        myModalityState = newModality;
      }
    }

    @Override
    public synchronized void invokeLater(@NotNull Runnable r) {
      ApplicationManager.getApplication().invokeLater(r, myModalityState);
    }
  }

  // synchronized newIdentityHashSet (Collections.synchronizedSet does not help the iterator)
  private static final class SynchronizedIdentityHashSet<T> {
    private final Set<T> myCollection = Sets.newIdentityHashSet();

    synchronized boolean add(@NotNull T sdk) {
      return myCollection.add(sdk);
    }

    synchronized void remove(@NotNull T sdk) {
      myCollection.remove(sdk);
    }

    synchronized boolean contains(@NotNull T sdk) {
      return myCollection.contains(sdk);
    }

    synchronized @NotNull List<T> copy() {
      return new ArrayList<>(myCollection);
    }
  }

  private static class PendingDownload {
    final SdkDownloadTask myTask;
    final ProgressIndicatorBase myProgressIndicator = new ProgressIndicatorBase();
    final PendingDownloadModalityTracker myModalityTracker;

    final SynchronizedIdentityHashSet<Sdk> myEditableSdks = new SynchronizedIdentityHashSet<>();
    final SynchronizedIdentityHashSet<Runnable> mySdkFailedHandlers = new SynchronizedIdentityHashSet<>();
    final SynchronizedIdentityHashSet<Consumer<? super Boolean>> myCompleteListeners = new SynchronizedIdentityHashSet<>();
    final SynchronizedIdentityHashSet<Disposable> myDisposables = new SynchronizedIdentityHashSet<>();

    final AtomicBoolean myIsDownloading = new AtomicBoolean(false);

    PendingDownload(@NotNull Sdk sdk, @NotNull SdkDownloadTask task, @NotNull PendingDownloadModalityTracker tracker) {
      myEditableSdks.add(sdk);
      myTask = task;
      myModalityTracker = tracker;
    }

    boolean containsSdk(@NotNull Sdk sdk) {
      // called from any thread
      return myEditableSdks.contains(sdk);
    }

    void registerEditableSdk(@NotNull Sdk editable) {
      // called from any thread
      //
      // there are many Sdk clones that are created
      // along with the Project Structure model.
      // Our goal is to keep track of all such objects to make
      // sure we update all and refresh the UI once download is completed
      //
      // there is a chance we have here several unneeded objects,
      // e.g. from the Project Structure dialog is shown several
      // times. It's cheaper to ignore then to track
      myEditableSdks.add(editable);
    }

    protected void runTask(@NotNull @NlsContexts.ProgressTitle String title, @NotNull Progressive progressive) {
      var task = new Task.Backgroundable(null,
                                         title,
                                         true,
                                         PerformInBackgroundOption.ALWAYS_BACKGROUND) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          progressive.run(indicator);
        }
      };
      ProgressManager.getInstance().run(task);
    }

    void startDownloadIfNeeded(@NotNull Sdk sdkFromTable) {
      if (!myIsDownloading.compareAndSet(false, true)) return;
      if (myProgressIndicator.isCanceled()) return;

      myModalityTracker.updateModality();
      SdkType type = (SdkType)sdkFromTable.getSdkType();
      String title = ProjectBundle.message("sdk.configure.downloading", type.getPresentableName());

      //noinspection Convert2Lambda
      Progressive taskAction = new Progressive() {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          boolean failed = false;
          try {
            new ProgressIndicatorListener() {
              @Override
              public void cancelled() {
                myProgressIndicator.cancel();
              }
            }.installToProgressIfPossible(indicator);

            ProgressIndicatorEx relayToVisibleIndicator = new RelayUiToDelegateIndicator(indicator);

            myProgressIndicator.addStateDelegate(relayToVisibleIndicator);
            try {
              myProgressIndicator.checkCanceled();
              myTask.doDownload(myProgressIndicator);
            }
            finally {
              myProgressIndicator.removeStateDelegate(relayToVisibleIndicator);
            }

            // make sure VFS has the right image of our SDK to avoid empty SDK from being created
            VfsUtil.markDirtyAndRefresh(false, true, true, new File(myTask.getPlannedHomeDir()));

            //update the pending SDKs
            onSdkDownloadCompletedSuccessfully();
          }
          catch (Throwable e) {
            failed = true;
            if (!myProgressIndicator.isCanceled() && !(e instanceof ControlFlowException)) {
              handleDownloadError(type, title, e);
            }
          }
          finally {
            // dispose our own state
            disposeNow(!failed);
          }
        }
      };

      runTask(title, taskAction);
    }

    protected void handleDownloadError(@NotNull SdkType type, @NotNull @Nls String title, @NotNull Throwable exception) {
      LOG.warn("SDK Download failed. " + exception.getMessage(), exception);
      if (ApplicationManager.getApplication().isUnitTestMode()) return;
      myModalityTracker.invokeLater(() -> {
        Messages.showErrorDialog(ProjectBundle.message("error.message.sdk.download.failed", type.getPresentableName()), title);
      });
    }

    void registerListener(@NotNull Disposable lifetime,
                          @Nullable ProgressIndicator uiIndicator,
                          @NotNull Consumer<? super Boolean> completedCallback) {
      myModalityTracker.updateModality();

      //there is no need to add yet another copy of the same component
      if (!myCompleteListeners.add(completedCallback)) return;

      // make the UI indicator receive events, when the background task runs
      if (uiIndicator instanceof ProgressIndicatorEx indicatorEx) {
        myProgressIndicator.addStateDelegate(indicatorEx);
      }

      Disposable unsubscribe = new Disposable() {
        @Override
        public void dispose() {
          if (uiIndicator instanceof ProgressIndicatorEx indicatorEx) {
            myProgressIndicator.removeStateDelegate(indicatorEx);
          }
          myCompleteListeners.remove(completedCallback);
          myDisposables.remove(this);
        }
      };
      Disposer.register(lifetime, unsubscribe);
      myDisposables.add(unsubscribe);
    }

    void onSdkDownloadCompletedSuccessfully() {
        // we handle ModalityState explicitly to handle the case,
        // when the next ProjectSettings dialog is shown, and we still want to
        // notify all current viewers to reflect our SDK changes, thus we need
        // it's newer ModalityState to invoke. Using ModalityState.any is not
        // an option as we do update Sdk instances in the call
        myModalityTracker.invokeLater(() -> WriteAction.run(() -> {
          for (Sdk sdk : myEditableSdks.copy()) {
            try {
              SdkType sdkType = (SdkType)sdk.getSdkType();
              configureSdk(sdk);

              String actualVersion = null;
              try {
                actualVersion = sdkType.getVersionString(sdk);
                if (actualVersion != null) {
                  SdkModificator modificator = sdk.getSdkModificator();
                  modificator.setVersionString(actualVersion);
                  modificator.commitChanges();
                }
              } catch (Exception e) {
                LOG.warn("Failed to configure a version " + actualVersion + " for downloaded SDK. " + e.getMessage(), e);
              }

              sdkType.setupSdkPaths(sdk);

              for (Project project: ProjectManager.getInstance().getOpenProjects()) {
                final var rootManager = ProjectRootManagerImpl.getInstanceImpl(project);
                final Sdk projectSdk = rootManager.getProjectSdk();
                if (projectSdk != null && projectSdk.getName().equals(sdk.getName())) {
                  rootManager.projectJdkChanged();
                }
              }
            }
            catch (Exception e) {
              LOG.warn("Failed to set up SDK " + sdk + ". " + e.getMessage(), e);
            }
          }
        }));
    }

    void disposeNow(boolean succeeded) {
      myModalityTracker.invokeLater(() -> {
        getInstance().removeTask(this);
        //collections may change from the callbacks
        myCompleteListeners.copy().forEach(it -> it.consume(succeeded));
        myDisposables.copy().forEach(it -> Disposer.dispose(it));
        if (!succeeded) {
          mySdkFailedHandlers.copy().forEach(Runnable::run);
        }
      });
    }

    void cancel() {
      myProgressIndicator.cancel();
      disposeNow(false);
    }

    void configureSdk(@NotNull Sdk sdk) {
      getInstance().configureSdk(sdk, myTask);
    }
  }

  /**
   * Applies configuration for the SDK from the expectations of
   * the given {@link SdkDownloadTask}
   */
  public void configureSdk(@NotNull Sdk sdk, @NotNull SdkDownloadTask task) {
    SdkModificator mod = sdk.getSdkModificator();
    mod.setHomePath(FileUtil.toSystemIndependentName(task.getPlannedHomeDir()));
    mod.setVersionString(task.getPlannedVersion());

    Application application = ApplicationManager.getApplication();
    Runnable runnable = () -> mod.commitChanges();
    if (application.isDispatchThread()) {
      application.runWriteAction(runnable);
    } else {
      application.invokeAndWait(() -> application.runWriteAction(runnable));
    }
  }
}
