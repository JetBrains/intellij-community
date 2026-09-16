// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.codeWithMe.ClientId;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.openapi.wm.impl.status.StatusBarAccessibilityUtil;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.ClickListener;
import com.intellij.util.LazyInitializer;
import com.intellij.util.LazyInitializer.LazyValue;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.DebouncedUpdates;
import com.intellij.util.ui.update.UpdateQueue;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.notification.NotificationAction.createSimpleExpiring;

/** Internal API. See a note in {@link MessagePool}. */
@ApiStatus.Internal
public final class IdeMessagePanel implements MessagePoolAdvisor, IconLikeCustomStatusBarWidget {
  private static final Logger LOG = Logger.getInstance(IdeMessagePanel.class);

  private static final boolean NOTIFICATIONS_ENABLED = !System.getProperty("idea.fatal.error.notification").equals("disabled");

  public static final String FATAL_ERROR = "FatalError";

  private static final String GROUP_ID = "IDE-errors";

  /** The debounce window for {@link #updateIconAndNotify()}. */
  private static final int UPDATE_DELAY_MS = 200;
  private static final Object UPDATE_REQUEST = new Object();
  private final UpdateQueue<Object> updateRequests;

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
  private final AtomicBoolean pluginUpdateScheduled = new AtomicBoolean(false);

  private final MessagePoolAdvisor releaseExceptionsFilter = new IdeMessagePanelReleaseExceptionsFilter();

  private final ClickListener onClick = new ClickListener() {
    @Override
    public boolean onClick(@NotNull MouseEvent event, int clickCount) {
      openErrorsDialog(null);
      return true;
    }
  };

  public IdeMessagePanel(@Nullable IdeFrame frame, @NotNull MessagePool messagePool, @NotNull CoroutineScope coroutineScope) {
    component = LazyInitializer.create(() -> {
      var result = new IdeMessagePanelComponent();
      onClick.installOn(result);
      return result;
    });

    this.frame = frame;
    this.project = frame == null ? null : frame.getProject();
    this.messagePool = messagePool;

    // The queue coalesces the requests, runs the checks on a background thread, and only then touches the UI.
    updateRequests = DebouncedUpdates.forScope(coroutineScope, "IdeMessagePanel.updateIconAndNotify", UPDATE_DELAY_MS)
      .withContext(Dispatchers.getDefault())
      .runBatched(ignored -> updateIconAndNotify())
      .cancelOnDispose(this);

    if (project != null) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        ijProject.set(IntelliJProjectUtil.isIntelliJPlatformProject(project) || IntelliJProjectUtil.isIntelliJPluginProject(project));
      });
    }

    messagePool.addAdvisor(this);

    var application = ApplicationManager.getApplication();
    if (!application.isEAP() && !application.isInternal()) {
      if (!ExceptionAutoReportUtil.INSTANCE.isAutoReportVisibleBlocking()) {
        LOG.debug("Suppressing bundled exceptions in release build, automatic reporting is not available");
        messagePool.addAdvisor(releaseExceptionsFilter);
      }
    }

    scheduleUpdateIconAndNotify();
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
    messagePool.removeAdvisor(this);
    messagePool.removeAdvisor(releaseExceptionsFilter);
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
        scheduleUpdateIconAndNotify();
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
  public @Nullable Object afterEntryAdded(@NotNull AfterEntryAddedEvent e, @NotNull Continuation<? super @NotNull Unit> $completion) {
    var app = ApplicationManager.getApplication();
    if (app == null) {
      return MessagePoolAdvisor.super.afterEntryAdded(e, $completion);
    }

    var message = e.getMessage();
    if (app.isInternal() || app.isEAP()
        || NOTIFICATIONS_ENABLED
        || showPluginError(message.getThrowable(), message.getMessage(), findPlugin(message.getThrowable()))) {
      LOG.debug("Update error indicator");
      scheduleUpdateIconAndNotify();
    }

    return MessagePoolAdvisor.super.afterEntryAdded(e, $completion);
  }

  private static @Nullable IdeaPluginDescriptor findPlugin(Throwable throwable) {
    return PluginManagerCore.getPlugin(PluginUtil.getInstance().findPluginId(throwable));
  }

  private boolean showPluginError(Throwable throwable, @Nullable String message, @Nullable IdeaPluginDescriptor plugin) {
    var submitter = DefaultIdeaErrorLogger.findSubmitter(throwable, plugin);
    if (plugin != null
        && !isBuiltIn(plugin)
        && !pluginUpdateScheduled.getAndSet(true)
        && UpdateSettings.getInstance().isPluginsCheckNeeded()) {
      UpdateCheckerFacade.getInstance().updateAndShowResult();  // push users to update plugins producing exceptions
    }
    return !(submitter instanceof ITNReporter) || ((ITNReporter)submitter).showErrorInRelease(new IdeaLoggingEvent(message, throwable));
  }

  static boolean isBuiltIn(@Nullable IdeaPluginDescriptor plugin) {
    if (plugin == null) return true;
    return plugin.isBundled() || PluginManagerCore.isUpdatedBundledPlugin(plugin);
  }

  @Override
  public void poolCleared(@NotNull PoolClearedEvent e) {
    scheduleUpdateIconAndNotify();
  }

  @Override
  public void entryWasRead(@NotNull EntryReadEvent e) {
    scheduleUpdateIconAndNotify();
  }

  private boolean isOtherModalWindowActive() {
    var activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    return activeWindow instanceof JDialog d && d.isModal() && (dialog == null || dialog.getWindow() != activeWindow);
  }

  /**
   * Asks for an icon and notification update. The request is debounced, and the update runs on a background thread.
   * This method is safe to call from any thread.
   */
  private void scheduleUpdateIconAndNotify() {
    updateRequests.queue(UPDATE_REQUEST);
  }

  /** Reads the pool state and updates the UI. The caller must go through {@link #scheduleUpdateIconAndNotify()}. */
  @RequiresBackgroundThread
  private void updateIconAndNotify() {
    var state = messagePool.getState();
    updateIcon(state);

    var displayType = NotificationsConfiguration.getNotificationsConfiguration().getDisplayType(GROUP_ID);

    // The balloon and the window state belong to the UI, so the rest runs on the EDT.
    UIUtil.invokeLaterIfNeeded(() -> {
      if (state == MessagePool.State.NoErrors && balloon != null) {
        Disposer.dispose(balloon);
      }
      else if (state == MessagePool.State.UnreadErrors && balloon == null && displayType != NotificationDisplayType.NONE
               && isActive(frame) && project != null && !project.isDisposed()) {
        showErrorNotification(project, frame, displayType);
      }
    });
  }

  @Contract("null -> false")
  private static boolean isActive(@Nullable IdeFrame frame) {
    return (frame instanceof ProjectFrameHelper pfh ? pfh.getFrame() : frame) instanceof Window w && w.isActive();
  }

  @RequiresEdt
  private void showErrorNotification(@NotNull Project project, @NotNull IdeFrame frame, @NotNull NotificationDisplayType displayType) {
    if (balloon != null) {
      return;
    }

    var layout = frame.getBalloonLayout();
    if (layout == null) {
      Logger.getInstance(IdeMessagePanel.class).error("frame=" + frame + " (" + frame.getClass() + ')');
      return;
    }

    var notification = new Notification(GROUP_ID, DiagnosticBundle.message("error.new.notification.title"), NotificationType.ERROR)
      .setIcon(AllIcons.Ide.FatalError)
      .addAction(createSimpleExpiring(DiagnosticBundle.message("error.new.notification.link"), () -> openErrorsDialog(null)));

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

  private final class IdeMessagePanelComponent extends JPanel {
    private IdeMessagePanelComponent() {
      super(new BorderLayout());
      setOpaque(false);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleIdeMessagePanelComponent();
      }
      return accessibleContext;
    }

    private final class AccessibleIdeMessagePanelComponent extends AccessibleJPanel {
      private final AccessibleAction myAccessibleAction =
        StatusBarAccessibilityUtil.createAccessibleAction(IdeMessagePanelComponent.this, () -> openErrorsDialog(null));

      @Override
      public AccessibleRole getAccessibleRole() {
        return AccessibleRole.PUSH_BUTTON;
      }

      @Override
      public String getAccessibleName() {
        var name = super.getAccessibleName();
        return name == null ? DiagnosticBundle.message("error.new.notification.title") : name;
      }

      @Override
      public String getAccessibleDescription() {
        var description = super.getAccessibleDescription();
        return description == null ? DiagnosticBundle.message("error.new.notification.link") : description;
      }

      @Override
      public AccessibleAction getAccessibleAction() {
        return myAccessibleAction;
      }
    }
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
