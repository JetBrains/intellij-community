// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.EdtReplacementThread;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Intended to run tasks, both modal and non-modal (backgroundable)
 * Example of use:
 * <pre>
 * new Task.Backgroundable(project, "Synchronizing data", true) {
 *  public void run(ProgressIndicator indicator) {
 *    indicator.setText("Loading changes");
 *    indicator.setIndeterminate(false);
 *    indicator.setFraction(0.0);
 *    // some code
 *    indicator.setFraction(1.0);
 *  }
 * }.setCancelText("Stop loading").queue();
 * </pre>
 *
 * @see ProgressManager#run(Task)
 */
public abstract class Task implements TaskInfo, Progressive {
  private static final Logger LOG = Logger.getInstance(Task.class);

  protected final Project myProject;
  protected String myTitle;
  private final boolean myCanBeCancelled;

  private String myCancelText = CoreBundle.message("button.cancel");
  private String myCancelTooltipText = CoreBundle.message("button.cancel");

  private Task(@Nullable Project project, @NlsContexts.ProgressTitle @NotNull String title, boolean canBeCancelled) {
    myProject = project;
    myTitle = title;
    myCanBeCancelled = canBeCancelled;
  }

  /**
   * This callback will be invoked on AWT dispatch thread.
   *
   * Callback executed when run() throws {@link ProcessCanceledException} or if its {@link ProgressIndicator} was canceled.
   */
  public void onCancel() {
  }

  /**
   * This callback will be invoked on AWT dispatch thread.
   */
  public void onSuccess() {
  }

  /**
   * This callback will be invoked on AWT dispatch thread.
   * <p>
   * Callback executed when {@link #run(ProgressIndicator)} throws an exception (except {@link ProcessCanceledException}).
   *
   * @deprecated use {@link #onThrowable(Throwable)} instead
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  public void onError(@NotNull Exception error) {
    LOG.error(error);
  }

  /**
   * This callback will be invoked on AWT dispatch thread.
   * <p>
   * Callback executed when {@link #run(ProgressIndicator)} throws an exception (except {@link ProcessCanceledException}).
   */
  public void onThrowable(@NotNull Throwable error) {
    if (error instanceof Exception) {
      onError((Exception)error);
    }
    else {
      LOG.error(error);
    }
  }

  /**
   * This callback will be invoked on AWT dispatch thread, after other specific handlers.
   */
  public void onFinished() {
  }

  /**
   * Specifies the thread to run callbacks on. See {@link EdtReplacementThread} documentation for more info.
   */
  @NotNull
  public EdtReplacementThread whereToRunCallbacks() {
    return EdtReplacementThread.EDT_WITH_IW;
  }

  public final Project getProject() {
    return myProject;
  }

  public final void queue() {
    ProgressManager.getInstance().run(this);
  }

  @Override
  @NotNull
  public final String getTitle() {
    return myTitle;
  }

  @NotNull
  public final Task setTitle(@Nls@NlsContexts.ProgressTitle @NotNull String title) {
    myTitle = title;
    return this;
  }

  @Override
  public final String getCancelText() {
    return myCancelText;
  }

  @NotNull
  public final Task setCancelText(@NlsContexts.Button String cancelText) {
    myCancelText = cancelText;
    return this;
  }

  @Nullable
  public NotificationInfo getNotificationInfo() {
    return null;
  }

  @Nullable
  public NotificationInfo notifyFinished() {
    return getNotificationInfo();
  }

  public boolean isHeadless() {
    return ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  @NotNull
  public final Task setCancelTooltipText(@NlsContexts.Tooltip String cancelTooltipText) {
    myCancelTooltipText = cancelTooltipText;
    return this;
  }

  @Override
  public final String getCancelTooltipText() {
    return myCancelTooltipText;
  }

  @Override
  public final boolean isCancellable() {
    return myCanBeCancelled;
  }

  public abstract boolean isModal();

  @NotNull
  public final Modal asModal() {
    if (isModal()) {
      return (Modal)this;
    }
    throw new IllegalStateException("Not a modal task");
  }

  @NotNull
  public final Backgroundable asBackgroundable() {
    if (!isModal()) {
      return (Backgroundable)this;
    }
    throw new IllegalStateException("Not a backgroundable task");
  }

  public abstract static class Backgroundable extends Task implements PerformInBackgroundOption {
    protected final PerformInBackgroundOption myBackgroundOption;

    public Backgroundable(@Nullable Project project, @NlsContexts.ProgressTitle @NotNull String title) {
      this(project, title, true);
    }

    public Backgroundable(@Nullable Project project, @NlsContexts.ProgressTitle @NotNull String title, boolean canBeCancelled) {
      this(project, title, canBeCancelled, null);
    }

    public Backgroundable(@Nullable Project project,
                          @NlsContexts.ProgressTitle @NotNull String title,
                          boolean canBeCancelled,
                          @Nullable PerformInBackgroundOption backgroundOption) {
      super(project, title, canBeCancelled);
      myBackgroundOption = backgroundOption;
      if (StringUtil.isEmptyOrSpaces(title)) {
        LOG.warn("Empty title for backgroundable task.", new Throwable());
      }
    }

    @Override
    public boolean shouldStartInBackground() {
      return myBackgroundOption == null || myBackgroundOption.shouldStartInBackground();
    }

    @Override
    public void processSentToBackground() {
      if (myBackgroundOption != null) {
        myBackgroundOption.processSentToBackground();
      }
    }

    @Override
    public final boolean isModal() {
      return false;
    }

    public boolean isConditionalModal() {
      return false;
    }

 }

  public abstract static class Modal extends Task {
    public Modal(@Nullable Project project, @NlsContexts.DialogTitle @NotNull String title, boolean canBeCancelled) {
      super(project, title, canBeCancelled);
    }

    @Override
    public final boolean isModal() {
      return true;
    }
  }

  public abstract static class ConditionalModal extends Backgroundable {
    public ConditionalModal(@Nullable Project project,
                            @NlsContexts.ProgressTitle @NotNull String title,
                            boolean canBeCancelled,
                            @NotNull PerformInBackgroundOption backgroundOption) {
      super(project, title, canBeCancelled, backgroundOption);
    }

    @Override
    public final boolean isConditionalModal() {
      return true;
    }
  }

  public static class NotificationInfo {
    private final String myNotificationName;
    private final String myNotificationTitle;
    private final String myNotificationText;
    private final boolean myShowWhenFocused;

    public NotificationInfo(@NotNull String notificationName,
                            @NotNull @NlsContexts.SystemNotificationTitle String notificationTitle,
                            @NotNull @NlsContexts.SystemNotificationText String notificationText) {
      this(notificationName, notificationTitle, notificationText, false);
    }

    public NotificationInfo(@NotNull String notificationName,
                            @NotNull @NlsContexts.SystemNotificationTitle String notificationTitle,
                            @NotNull @NlsContexts.SystemNotificationText String notificationText,
                            final boolean showWhenFocused) {
      myNotificationName = notificationName;
      myNotificationTitle = notificationTitle;
      myNotificationText = notificationText;
      myShowWhenFocused = showWhenFocused;
    }

    @NotNull
    public String getNotificationName() {
      return myNotificationName;
    }

    @NotNull
    @NlsContexts.SystemNotificationTitle
    public String getNotificationTitle() {
      return myNotificationTitle;
    }

    @NotNull
    @NlsContexts.SystemNotificationText
    public String getNotificationText() {
      return myNotificationText;
    }

    public boolean isShowWhenFocused() {
      return myShowWhenFocused;
    }
  }

  public abstract static class WithResult<T, E extends Exception> extends Task.Modal {
    private final Ref<T> myResult = Ref.create();
    private final Ref<Throwable> myError = Ref.create();

    public WithResult(@Nullable Project project, @NlsContexts.ProgressTitle @NotNull String title, boolean canBeCancelled) {
      super(project, title, canBeCancelled);
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      try {
        myResult.set(compute(indicator));
      }
      catch (Throwable t) {
        myError.set(t);
      }
    }

    protected abstract T compute(@NotNull ProgressIndicator indicator) throws E;

    @SuppressWarnings("unchecked")
    public T getResult() throws E {
      Throwable t = myError.get();
      ExceptionUtil.rethrowUnchecked(t);
      if (t != null) throw (E)t;
      return myResult.get();
    }
  }
}