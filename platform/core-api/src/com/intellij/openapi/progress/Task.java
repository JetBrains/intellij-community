/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.progress;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbModeAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Intended to run tasks, both modal and non-modal (backgroundable)
 * Example of use:
 * <pre>
 * new Task.Backgroundable(project, "Synchronizing data", true) {
 *  public void run(ProgressIndicator indicator) {
 *    indicator.setText("Loading changes");
 *    indicator.setFraction(0.0);
 * // some code
 *    indicator.setFraction(1.0);
 *  }
 * }.setCancelText("Stop loading").queue();
 * </pre>
 *
 * @see com.intellij.openapi.progress.ProgressManager#run(Task)
 */
public abstract class Task implements TaskInfo, Progressive {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.Task");
  protected final Project myProject;
  protected String myTitle;
  private final boolean myCanBeCancelled;

  private String myCancelText = CommonBundle.getCancelButtonText();
  private String myCancelTooltipText = CommonBundle.getCancelButtonText();

  public Task(@Nullable final Project project, @NotNull final String title, final boolean canBeCancelled) {
    myProject = project;
    myTitle = title;
    myCanBeCancelled = canBeCancelled;
  }

  /**
   * This callback will be invoked on AWT dispatch thread.
   */
  public void onCancel() {}

  /**
   * This callback will be invoked on AWT dispatch thread.
   */
  public void onSuccess() {}

  public final Project getProject() {
    return myProject;
  }

  public final void queue() {
    ProgressManager.getInstance().run(this);
  }

  @Override
  public String getProcessId() {
    return "<unknown>";
  }

  @Override
  @NotNull
  public final String getTitle() {
    return myTitle;
  }

  @NotNull
  public final Task setTitle(@NotNull String title) {
    myTitle = title;
    return this;
  }

  @Override
  public final String getCancelText() {
    return myCancelText;
  }

  @NotNull
  public final Task setCancelText(final String cancelText) {
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
  public final Task setCancelTooltipText(final String cancelTooltipText) {
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

    public Backgroundable(@Nullable final Project project, @NotNull final String title, final boolean canBeCancelled, @Nullable final PerformInBackgroundOption backgroundOption) {
      super(project, title, canBeCancelled);
      myBackgroundOption = backgroundOption;
      if (StringUtil.isEmptyOrSpaces(title)) {
        LOG.warn("Empty title for backgroundable task.", new Throwable());
      }
    }

    public Backgroundable(@Nullable final Project project, @NotNull final String title, final boolean canBeCancelled) {
      this(project, title, canBeCancelled, null);
    }

    public Backgroundable(@Nullable final Project project, @NotNull final String title) {
      this(project, title, true);
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

    public DumbModeAction getDumbModeAction() {
      return DumbModeAction.NOTHING;
    }
  }

  public abstract static class Modal extends Task {
    public Modal(@Nullable final Project project, @NotNull String title, boolean canBeCancelled) {
      super(project, title, canBeCancelled);
    }


    @Override
    public final boolean isModal() {
      return true;
    }
  }

  public abstract static class ConditionalModal extends Backgroundable {
    public ConditionalModal(@Nullable final Project project, @NotNull final String title, final boolean canBeCancelled,
                            @NotNull final PerformInBackgroundOption backgroundOption) {
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

    public NotificationInfo(@NotNull final String notificationName,
                            @NotNull final String notificationTitle,
                            @NotNull final String notificationText) {
      this(notificationName, notificationTitle, notificationText, false);
    }

    public NotificationInfo(@NotNull final String notificationName,
                            @NotNull final String notificationTitle,
                            @NotNull final String notificationText,
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
    public String getNotificationTitle() {
      return myNotificationTitle;
    }

    @NotNull
    public String getNotificationText() {
      return myNotificationText;
    }

    public boolean isShowWhenFocused() {
      return myShowWhenFocused;
    }
  }
}
