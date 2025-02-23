// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.codeWithMe.ClientId;
import com.intellij.icons.AllIcons;
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.ClickListener;
import com.intellij.util.LazyInitializer;
import com.intellij.util.LazyInitializer.LazyValue;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Internal API. See a note in {@link MessagePool}. */
@ApiStatus.Internal
public final class IdeMessagePanel implements MessagePoolListener, IconLikeCustomStatusBarWidget {
  public static final String FATAL_ERROR = "FatalError";

  private static final String GROUP_ID = "IDE-errors";

  private final LazyValue<JPanel> component;
  private final @Nullable IdeFrame frame;
  private final @Nullable Project project;
  private final MessagePool messagePool;
  private final AtomicBoolean ijProject = new AtomicBoolean(false);

  private IdeErrorsIcon icon;
  private Balloon balloon;
  private IdeErrorsDialog dialog;
  private boolean isOpeningInProgress;

  private final IdeMessageAction action = new IdeMessageAction();

  private final ClickListener onClick = new ClickListener() {
    @Override
    public boolean onClick(@NotNull MouseEvent event, int clickCount) {
      openErrorsDialog(null);
      return true;
    }
  };

  public IdeMessagePanel(@Nullable IdeFrame frame, @NotNull MessagePool messagePool) {
    component = LazyInitializer.create(() -> {
      var result = new JPanel(new BorderLayout());
      result.setOpaque(false);
      onClick.installOn(result);
      return result;
    });

    this.frame = frame;
    this.project = frame == null ? null : frame.getProject();
    this.messagePool = messagePool;

    if (project != null) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        ijProject.set(IntelliJProjectUtil.isIntelliJPlatformProject(project) || IntelliJProjectUtil.isIntelliJPluginProject(project));
      });
    }

    messagePool.addListener(this);

    updateIconAndNotify();
  }

  @Override
  public @NotNull String ID() {
    return FATAL_ERROR;
  }

  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public void dispose() {
    messagePool.removeListener(this);
  }

  @Override
  public JComponent getComponent() {
    return component.get();
  }

  public AnAction getAction() {
    return action;
  }

  public void openErrorsDialog(@Nullable LogMessage message) {
    if (dialog != null || isOpeningInProgress) {
      return;
    }

    isOpeningInProgress = true;

    new Runnable() {
      @Override
      public void run() {
        if (!isOtherModalWindowActive()) {
          try (var ignored = ClientId.withClientId(ClientId.getLocalId())) {
            // always show IDE errors to the host
            doOpenErrorsDialog(message);
          }
          finally {
            isOpeningInProgress = false;
          }
        }
        else if (dialog == null) {
          EdtExecutorService.getScheduledExecutorInstance().schedule(this, 300L, TimeUnit.MILLISECONDS);
        }
      }
    }.run();
  }

  private void doOpenErrorsDialog(@Nullable LogMessage message) {
    dialog = new IdeErrorsDialog(messagePool, project, ijProject.get(), message) {
      @Override
      protected void dispose() {
        super.dispose();
        dialog = null;
        updateIconAndNotify();
      }

      @Override
      protected void updateOnSubmit() {
        super.updateOnSubmit();
        updateIcon(messagePool.getState());
      }
    };
    dialog.show();
  }

  private void updateIcon(MessagePool.State state) {
    UIUtil.invokeLaterIfNeeded(() -> {
      var icon = this.icon;
      if (icon == null) {
        icon = new IdeErrorsIcon(frame != null);
        icon.setVerticalAlignment(SwingConstants.CENTER);
        onClick.installOn(icon);
        this.icon = icon;
        component.get().add(icon, BorderLayout.CENTER);
      }

      icon.setState(state);
      component.get().setVisible(state != MessagePool.State.NoErrors);
      action.icon = icon.getIcon();
      action.state = state;
    });
  }

  @Override
  public void newEntryAdded() {
    updateIconAndNotify();
  }

  @Override
  public void poolCleared() {
    updateIconAndNotify();
  }

  @Override
  public void entryWasRead() {
    updateIconAndNotify();
  }

  private boolean isOtherModalWindowActive() {
    var activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    return activeWindow instanceof JDialog d && d.isModal() && (dialog == null || dialog.getWindow() != activeWindow);
  }

  private void updateIconAndNotify() {
    var state = messagePool.getState();
    updateIcon(state);

    if (state == MessagePool.State.NoErrors && balloon != null) {
      Disposer.dispose(balloon);
    }
    else if (state == MessagePool.State.UnreadErrors && balloon == null && isActive(frame) && project != null) {
      ApplicationManager.getApplication().invokeLater(() -> showErrorNotification(project, frame), project.getDisposed());
    }
  }

  @Contract("null -> false")
  private static boolean isActive(@Nullable IdeFrame frame) {
    return (frame instanceof ProjectFrameHelper pfh ? pfh.getFrame() : frame) instanceof Window w && w.isActive();
  }

  @RequiresEdt
  private void showErrorNotification(@NotNull Project project, @NotNull IdeFrame frame) {
    if (balloon != null) {
      return;
    }

    var displayType = NotificationsConfiguration.getNotificationsConfiguration().getDisplayType(GROUP_ID);
    if (displayType == NotificationDisplayType.NONE) {
      return;
    }

    var layout = frame.getBalloonLayout();
    if (layout == null) {
      Logger.getInstance(IdeMessagePanel.class).error("frame=" + frame + " (" + frame.getClass() + ')');
      return;
    }

    var notification = new Notification(GROUP_ID, DiagnosticBundle.message("error.new.notification.title"), NotificationType.ERROR)
      .setIcon(AllIcons.Ide.FatalError)
      .addAction(NotificationAction.createSimpleExpiring(DiagnosticBundle.message("error.new.notification.link"), () -> openErrorsDialog(null)));

    var layoutData = BalloonLayoutData.createEmpty();
    layoutData.fadeoutTime = displayType == NotificationDisplayType.STICKY_BALLOON ? 300000 : 10000;
    layoutData.textColor = JBUI.CurrentTheme.Notification.Error.FOREGROUND;
    layoutData.fillColor = JBUI.CurrentTheme.Notification.Error.BACKGROUND;
    layoutData.borderColor = JBUI.CurrentTheme.Notification.Error.BORDER_COLOR;
    layoutData.closeAll = () -> layout.closeAll();
    layoutData.showSettingButton = true;

    balloon = NotificationsManagerImpl.createBalloon(frame, notification, false, false, new Ref<>(layoutData), project);
    Disposer.register(balloon, () -> balloon = null);
    layout.add(balloon);
  }

  private final class IdeMessageAction extends AnAction implements DumbAware {
    private MessagePool.State state = MessagePool.State.NoErrors;
    private Icon icon;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      openErrorsDialog(null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(state != MessagePool.State.NoErrors);
      e.getPresentation().setIcon(icon);
    }
  }
}
