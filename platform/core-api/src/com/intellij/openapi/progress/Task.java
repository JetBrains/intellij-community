// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.core.CoreBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.EdtReplacementThread;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.DialogTitle;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.openapi.util.NlsContexts.SystemNotificationText;
import com.intellij.openapi.util.NlsContexts.SystemNotificationTitle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Intended to run tasks, both modal and non-modal (backgroundable).
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

  protected final @Nullable Project myProject;
  protected final @Nullable JComponent myParentComponent;

  /** @deprecated please use {@link #getTitle} / {@link #setTitle} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @SuppressWarnings("DeprecatedIsStillUsed")
  protected @ProgressTitle @NotNull String myTitle;

  private final boolean myCanBeCancelled;
  private @NlsContexts.Button String myCancelText = CoreBundle.message("button.cancel");
  private @NlsContexts.Tooltip String myCancelTooltipText = CoreBundle.message("button.cancel");

  private Task(@Nullable Project project, @Nullable JComponent parentComponent, @ProgressTitle @NotNull String title, boolean canBeCancelled) {
    myProject = project;
    myParentComponent = parentComponent;
    myTitle = title;
    myCanBeCancelled = canBeCancelled;
  }

  /**
   * This callback will be invoked on AWT dispatch thread.
   * <p>
   * Callback executed when run() throws {@link ProcessCanceledException} or if its {@link ProgressIndicator} was canceled.
   */
  public void onCancel() { }

  /**
   * This callback will be invoked on AWT dispatch thread.
   */
  public void onSuccess() { }

  /** @deprecated please override {@link #onThrowable(Throwable)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @SuppressWarnings({"DeprecatedIsStillUsed", "RedundantSuppression"})
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
  public void onFinished() { }

  /**
   * Specifies the thread to run callbacks on. See {@link EdtReplacementThread} documentation for more info.
   */
  public @NotNull EdtReplacementThread whereToRunCallbacks() {
    return EdtReplacementThread.EDT_WITH_IW;
  }

  @SuppressWarnings("NullableProblems")
  public final Project getProject() {
    return myProject;
  }

  public final @Nullable JComponent getParentComponent() {
    return myParentComponent;
  }

  public final void queue() {
    ProgressManager.getInstance().run(this);
  }

  @Override
  public final @ProgressTitle @NotNull String getTitle() {
    return myTitle;
  }

  public final @NotNull Task setTitle(@ProgressTitle @NotNull String title) {
    myTitle = title;
    return this;
  }

  @Override
  public final String getCancelText() {
    return myCancelText;
  }

  public final @NotNull Task setCancelText(@NlsContexts.Button String cancelText) {
    myCancelText = cancelText;
    return this;
  }

  /**
   * Optional; may be used to distinguish this task among others.
   */
  @ApiStatus.Experimental
  public @Nullable Object getId() {
    return null;
  }

  public @Nullable NotificationInfo getNotificationInfo() {
    return null;
  }

  public @Nullable NotificationInfo notifyFinished() {
    return getNotificationInfo();
  }

  public boolean isHeadless() {
    return ApplicationManager.getApplication().isUnitTestMode() || ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  public final @NotNull Task setCancelTooltipText(@NlsContexts.Tooltip String cancelTooltipText) {
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

  public final @NotNull Modal asModal() {
    if (isModal()) {
      return (Modal)this;
    }
    throw new IllegalStateException("Not a modal task");
  }

  public final @NotNull Backgroundable asBackgroundable() {
    if (!isModal()) {
      return (Backgroundable)this;
    }
    throw new IllegalStateException("Not a backgroundable task");
  }

  public abstract static class Backgroundable extends Task implements PerformInBackgroundOption {
    private final @NotNull PerformInBackgroundOption myBackgroundOption;

    public Backgroundable(@Nullable Project project, @ProgressTitle @NotNull String title) {
      this(project, title, true);
    }

    public Backgroundable(@Nullable Project project, @ProgressTitle @NotNull String title, boolean canBeCancelled) {
      this(project, title, canBeCancelled, ALWAYS_BACKGROUND);
    }

    public Backgroundable(@Nullable Project project,
                          @ProgressTitle @NotNull String title,
                          boolean canBeCancelled,
                          @Nullable PerformInBackgroundOption backgroundOption) {
      this(project, null, title, canBeCancelled, backgroundOption);
    }

    public Backgroundable(@Nullable Project project,
                          @Nullable JComponent parentComponent,
                          @ProgressTitle @NotNull String title,
                          boolean canBeCancelled,
                          @Nullable PerformInBackgroundOption backgroundOption) {
      super(project, parentComponent, title, canBeCancelled);
      myBackgroundOption = ObjectUtils.notNull(backgroundOption, ALWAYS_BACKGROUND);
      if (StringUtil.isEmptyOrSpaces(title)) {
        LOG.warn("Empty title for backgroundable task.", new Throwable());
      }
    }

    @Override
    public boolean shouldStartInBackground() {
      return myBackgroundOption.shouldStartInBackground();
    }

    @Override
    public void processSentToBackground() {
      myBackgroundOption.processSentToBackground();
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
    public Modal(@Nullable Project project, @DialogTitle @NotNull String title, boolean canBeCancelled) {
      this(project, null, title, canBeCancelled);
    }

    public Modal(@Nullable Project project, @Nullable JComponent parentComponent, @DialogTitle @NotNull String title, boolean canBeCancelled) {
      //noinspection DialogTitleCapitalization
      super(project, parentComponent, title, canBeCancelled);
    }

    @Override
    public final boolean isModal() {
      return true;
    }
  }

  public abstract static class ConditionalModal extends Backgroundable {
    public ConditionalModal(@Nullable Project project,
                            @ProgressTitle @NotNull String title,
                            boolean canBeCancelled,
                            @NotNull PerformInBackgroundOption backgroundOption) {
      this(project, null, title, canBeCancelled, backgroundOption);
    }

    public ConditionalModal(@Nullable Project project,
                            @Nullable JComponent parentComponent,
                            @ProgressTitle @NotNull String title,
                            boolean canBeCancelled,
                            @NotNull PerformInBackgroundOption backgroundOption) {
      super(project, parentComponent, title, canBeCancelled, backgroundOption);
    }

    @Override
    public final boolean isConditionalModal() {
      return true;
    }
  }

  public static class NotificationInfo {
    private final String myNotificationName;
    private final @SystemNotificationTitle String myNotificationTitle;
    private final @SystemNotificationText String myNotificationText;
    private final boolean myShowWhenFocused;

    public NotificationInfo(@NotNull String notificationName,
                            @NotNull @SystemNotificationTitle String notificationTitle,
                            @NotNull @SystemNotificationText String notificationText) {
      this(notificationName, notificationTitle, notificationText, false);
    }

    public NotificationInfo(@NotNull String notificationName,
                            @NotNull @SystemNotificationTitle String notificationTitle,
                            @NotNull @SystemNotificationText String notificationText,
                            final boolean showWhenFocused) {
      myNotificationName = notificationName;
      myNotificationTitle = notificationTitle;
      myNotificationText = notificationText;
      myShowWhenFocused = showWhenFocused;
    }

    public @NotNull String getNotificationName() {
      return myNotificationName;
    }

    public @NotNull @SystemNotificationTitle String getNotificationTitle() {
      return myNotificationTitle;
    }

    public @NotNull @SystemNotificationText String getNotificationText() {
      return myNotificationText;
    }

    public boolean isShowWhenFocused() {
      return myShowWhenFocused;
    }
  }

  public abstract static class WithResult<T, E extends Exception> extends Task.Modal {
    private volatile T myResult;
    private volatile Throwable myError;

    public WithResult(@Nullable Project project, @DialogTitle @NotNull String title, boolean canBeCancelled) {
      this(project, null, title, canBeCancelled);
    }

    public WithResult(@Nullable Project project, @Nullable JComponent component, @DialogTitle @NotNull String title, boolean canBeCancelled) {
      super(project, component, title, canBeCancelled);
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      try {
        myResult = compute(indicator);
      }
      catch (Throwable t) {
        myError = t;
      }
    }

    protected abstract T compute(@NotNull ProgressIndicator indicator) throws E;

    @SuppressWarnings("unchecked")
    public T getResult() throws E {
      Throwable t = myError;
      if (t != null) {
        ExceptionUtil.rethrowUnchecked(t);
        throw (E)t;
      }
      else {
        return myResult;
      }
    }
  }
}
