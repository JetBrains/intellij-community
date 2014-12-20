package com.intellij.openapi.util.diff.tools.util.base;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.diff.api.FrameDiffTool;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffContext;
import com.intellij.openapi.util.diff.api.FrameDiffTool.DiffViewer;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.tools.util.DiffDataKeys;
import com.intellij.openapi.util.diff.util.CalledInAwt;
import com.intellij.openapi.util.diff.util.CalledInBackground;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public abstract class DiffViewerBase implements DiffViewer, DataProvider {
  protected static final Logger LOG = Logger.getInstance(DiffViewerBase.class);
  private static final Runnable TOO_SLOW_OPERATION = new EmptyRunnable();

  private static final int REDIFF_SCHEDULE_DELAY = ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;
  private static final int ASYNC_REDIFF_POSTPONE_DELAY = ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;

  @Nullable protected final Project myProject;
  @NotNull protected final DiffContext myContext;
  @NotNull protected final ContentDiffRequest myRequest;

  private int myModificationStamp = 0;
  @NotNull private final Alarm myAlarm = new Alarm();

  @Nullable private ProgressIndicator myProgressIndicator;

  private volatile boolean myDisposed = false;

  public DiffViewerBase(@NotNull DiffContext context, @NotNull ContentDiffRequest request) {
    myProject = context.getProject();
    myContext = context;
    myRequest = request;
  }

  @NotNull
  public final FrameDiffTool.ToolbarComponents init() {
    onInit();
    rediff();

    FrameDiffTool.ToolbarComponents components = new FrameDiffTool.ToolbarComponents();
    components.toolbarActions = createToolbarActions();
    components.popupActions = createPopupActions();
    components.statusPanel = getStatusPanel();
    return components;
  }

  @Override
  public final void dispose() {
    myDisposed = true;

    Disposer.dispose(myAlarm);
    abortRediff();
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        abortRediff();
        onDisposeAwt();
      }
    });

    onDispose();
  }

  @CalledInAwt
  public final void scheduleRediff() {
    final int modificationStamp = myModificationStamp;

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (modificationStamp != myModificationStamp) return;
        rediff();
      }
    }, REDIFF_SCHEDULE_DELAY);
  }

  @CalledInAwt
  public final void abortRediff() {
    if (myProgressIndicator != null) {
      myProgressIndicator.cancel();
      myProgressIndicator = null;
      myModificationStamp++;
    }
  }

  @CalledInAwt
  public final void rediff() {
    if (myDisposed) return;

    abortRediff();

    myModificationStamp++;
    final int modificationStamp = myModificationStamp;

    myProgressIndicator = new EmptyProgressIndicator();

    final ProgressIndicator indicator = myProgressIndicator;

    final Semaphore semaphore = new Semaphore(0);
    final AtomicReference<Runnable> resultRef = new AtomicReference<Runnable>();

    onBeforeRediff();

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final Runnable result = performRediff(indicator);

        if (indicator.isCanceled()) {
          semaphore.release();
          return;
        }

        if (!resultRef.compareAndSet(null, result)) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              finishRediff(result, modificationStamp, indicator);
            }
          }, ModalityState.any());
        }
        semaphore.release();
      }
    });

    try {
      if (tryRediffSynchronously()) {
        // TODO: we should vary delay: 400 ms is OK for initial loading, but too slow for active typing
        semaphore.tryAcquire(ASYNC_REDIFF_POSTPONE_DELAY, TimeUnit.MILLISECONDS);
      }
    }
    catch (InterruptedException ignore) {
    }
    if (!resultRef.compareAndSet(null, TOO_SLOW_OPERATION)) {
      // update presentation in the same thread to reduce blinking, caused by 'invokeLater' and fast performRediff()
      finishRediff(resultRef.get(), modificationStamp, indicator);
    }
    else {
      onSlowRediff();
    }
  }

  @CalledInAwt
  private void finishRediff(@NotNull final Runnable result, int modificationStamp, @NotNull final ProgressIndicator indicator) {
    if (indicator.isCanceled()) return;
    if (myModificationStamp != modificationStamp) return;

    result.run();
  }

  //
  // Getters
  //

  @Nullable
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public ContentDiffRequest getRequest() {
    return myRequest;
  }

  //
  // Abstract
  //

  @CalledInAwt
  protected boolean tryRediffSynchronously() {
    return true;
  }

  @Nullable
  protected List<AnAction> createToolbarActions() {
    return null;
  }

  @Nullable
  protected List<AnAction> createPopupActions() {
    return null;
  }

  @Nullable
  protected JComponent getStatusPanel() {
    return null;
  }

  @CalledInAwt
  protected void onInit() {
  }

  @CalledInAwt
  protected void onSlowRediff() {
  }

  @CalledInAwt
  protected void onBeforeRediff() {
  }

  @CalledInBackground
  @NotNull
  protected abstract Runnable performRediff(@NotNull ProgressIndicator indicator);

  protected void onDispose() {
  }

  @CalledInAwt
  protected void onDisposeAwt() {
  }

  @Nullable
  protected OpenFileDescriptor getOpenFileDescriptor() {
    return null;
  }


  //
  // Helpers
  //

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE.is(dataId)) {
      return getOpenFileDescriptor();
    }
    else if (DiffDataKeys.OPEN_FILE_DESCRIPTOR.is(dataId)) {
      return getOpenFileDescriptor();
    }
    else {
      return null;
    }
  }
}
