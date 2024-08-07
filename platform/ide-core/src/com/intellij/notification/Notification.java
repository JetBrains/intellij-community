// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.util.NlsContexts.*;

/**
 * <p>A notification has an optional title and subtitle, mandatory content (plain text or HTML), and optional actions.</p>
 *
 * <p>The notifications, generally, are shown in the balloons that appear on the screen when the corresponding events take place.
 * Notification balloon is of two types: two or three lines.<br>
 * Two lines: title and content line; title and actions; content line and actions; contents on two lines.<br>
 * Three lines: title and content line and actions; contents on two lines and actions; contents on three lines or more; etc.</p>
 *
 * <p><b>Warning:</b> please avoid links in HTML content, use {@link #addAction(AnAction)} and {@link NotificationAction#createSimpleExpiring(String, Runnable)} instead.</p>
 * <p>Use {@link Notifications.Bus} to show notifications.</p>
 *
 * @see NotificationAction
 * @see com.intellij.notification.SingletonNotificationManager
 *
 * @author Alexander Lobas
 */
public class Notification {
  /**
   * Tells which actions to keep when actions do not fit horizontally into the width of the notification
   * (i.e. do not put under the "Actions" dropdown).
   */
  public enum CollapseActionsDirection {KEEP_LEFTMOST, KEEP_RIGHTMOST}

  private static final Logger LOG = Logger.getInstance(Notification.class);
  public static final DataKey<Notification> KEY = DataKey.create("Notification");

  public final @NotNull String id;

  private final @NotNull String myGroupId;
  private final @NotNull NotificationType myType;

  private @Nullable String myDisplayId;
  private @Nullable Icon myIcon;
  private @NotNull @NotificationTitle String myTitle;
  private @Nullable @NotificationSubtitle String mySubtitle;
  private @NotNull @NotificationContent String myContent;
  private @Nullable NotificationListener myListener;
  private @Nullable @LinkLabel String myDropDownText;
  private @Nullable List<@NotNull AnAction> myActions;
  private @NotNull CollapseActionsDirection myCollapseDirection = CollapseActionsDirection.KEEP_LEFTMOST;
  private @Nullable AnAction myContextHelpAction;
  private @Nullable List<@NotNull Runnable> myWhenExpired;
  private @Nullable Boolean myImportant;
  private boolean mySuggestionType;
  private boolean myImportantSuggestion;
  private String myDoNotAskId;
  private @Nls String myDoNotAskDisplayName;
  private boolean myIsShowingPopupSuppressed;
  private String myRemindLaterHandlerId;
  private @Nullable String myToolWindowId;

  private final AtomicBoolean myExpired = new AtomicBoolean(false);
  private final AtomicReference<WeakReference<Balloon>> myBalloonRef = new AtomicReference<>();
  private final long myTimestamp = System.currentTimeMillis();

  /** See {@link #Notification(String, String, String, NotificationType)} */
  public Notification(@NotNull String groupId, @NotNull @NotificationContent String content, @NotNull NotificationType type) {
    this(groupId, "", content, type);
  }

  /**
   * @param groupId notification group ID registered in {@code plugin.xml} via {@link com.intellij.notification.impl.NotificationGroupEP}
   * @param title   an optional title (use an empty string ({@code ""}) to display the content without a title)
   */
  public Notification(@NotNull String groupId,
                      @NotNull @NotificationTitle String title,
                      @NotNull @NotificationContent String content,
                      @NotNull NotificationType type) {
    id = myTimestamp + "." + System.identityHashCode(this);
    myGroupId = groupId;
    myType = type;
    myTitle = title;
    myContent = content;
  }

  public boolean isSuggestionType() {
    return mySuggestionType;
  }

  @Contract(value = "_ -> this", mutates = "this")
  public @NotNull Notification setSuggestionType(boolean suggestionType) {
    mySuggestionType = suggestionType;
    return this;
  }

  public boolean isImportantSuggestion() {
    return myImportantSuggestion;
  }

  @Contract(value = "_ -> this", mutates = "this")
  public @NotNull Notification setImportantSuggestion(boolean importantSuggestion) {
    myImportantSuggestion = importantSuggestion;
    return this;
  }

  /**
   * Returns the time (in milliseconds since Jan 1, 1970) when the notification was created.
   */
  public long getTimestamp() {
    return myTimestamp;
  }

  /**
   * Unique ID for usage statistics.
   */
  public @Nullable String getDisplayId() {
    return myDisplayId;
  }

  @Contract(value = "_ -> this", mutates = "this")
  public @NotNull Notification setDisplayId(@NotNull String displayId) {
    myDisplayId = displayId;
    return this;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  @Contract(value = "_ -> this", mutates = "this")
  public @NotNull Notification setIcon(@Nullable Icon icon) {
    myIcon = icon;
    return this;
  }

  public @NotNull String getGroupId() {
    return myGroupId;
  }

  /**
   * Unique ID for "Don’t show again" action for a specific notification. By default, used group ID and they title.
   * Only for suggestion notifications.
   *
   * @param displayName tile for UI in Preferences | Appearance & Behavior | Notifications
   */
  public void configureDoNotAskOption(@NotNull String id, @NotNull @Nls String displayName) {
    myDoNotAskId = id;
    myDoNotAskDisplayName = displayName;
  }

  @ApiStatus.Internal
  public boolean canShowFor(@Nullable Project project) {
    if (myDoNotAskId == null) {
      @NlsSafe String title = NotificationGroup.getGroupTitle(myGroupId);
      if (title == null) {
        title = myGroupId;
      }
      myDoNotAskDisplayName = title;
      myDoNotAskId = myGroupId;
    }
    String id = "Notification.DoNotAsk-" + myDoNotAskId;
    boolean doNotAsk = PropertiesComponent.getInstance().getBoolean(id, false);
    if (doNotAsk) {
      return false;
    }
    if (project != null) {
      return !PropertiesComponent.getInstance(project).getBoolean(id, false);
    }
    return true;
  }

  @ApiStatus.Internal
  @Contract(value = "_ -> this")
  public Notification setDoNotAskFor(@Nullable Project project) {
    PropertiesComponent manager = project == null ? PropertiesComponent.getInstance() : PropertiesComponent.getInstance(project);
    manager.setValue("Notification.DoNotAsk-" + myDoNotAskId, true);
    manager.setValue("Notification.DisplayName-DoNotAsk-" + myDoNotAskId, myDoNotAskDisplayName);
    return this;
  }

  @ApiStatus.Internal
  public void setSuppressShowingPopup(boolean isShowingPopupSuppressed) {
    myIsShowingPopupSuppressed = isShowingPopupSuppressed;
  }

  @ApiStatus.Internal
  public boolean isShowingPopupSuppressed() {
    return myIsShowingPopupSuppressed;
  }

  @ApiStatus.Internal
  public @Nullable String getRemindLaterHandlerId() {
    return myRemindLaterHandlerId;
  }

  /**
   * Unique ID for "Remind me tomorrow" action for a specific notification.
   * Only for suggestion notifications.
   *
   * @see NotificationRemindLaterHandler
   */
  @Contract(value = "_ -> this", mutates = "this")
  public Notification setRemindLaterHandlerId(@NotNull String remindLaterHandlerId) {
    myRemindLaterHandlerId = remindLaterHandlerId;
    return this;
  }

  public boolean hasTitle() {
    return !isEmpty(myTitle) || !isEmpty(mySubtitle);
  }

  public @NotNull @NotificationTitle String getTitle() {
    return myTitle;
  }

  public @NotNull Notification setTitle(@Nullable @NotificationTitle String title) {
    myTitle = StringUtil.notNullize(title);
    return this;
  }

  public @NotNull Notification setTitle(@Nullable @NotificationTitle String title, @Nullable @NotificationSubtitle String subtitle) {
    return setTitle(title).setSubtitle(subtitle);
  }

  public @Nullable @NotificationTitle String getSubtitle() {
    return mySubtitle;
  }

  public @NotNull Notification setSubtitle(@Nullable @NotificationTitle String subtitle) {
    mySubtitle = subtitle;
    return this;
  }

  public boolean hasContent() {
    return !isEmpty(myContent);
  }

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable String text) {
    return StringUtil.isEmptyOrSpaces(text) || StringUtil.isEmptyOrSpaces(StringUtil.stripHtml(text, false));
  }

  public @NotNull @NotificationContent String getContent() {
    return myContent;
  }

  public @NotNull Notification setContent(@NotificationContent @Nullable String content) {
    myContent = StringUtil.notNullize(content);
    return this;
  }

  public @Nullable NotificationListener getListener() {
    return myListener;
  }

  /** @deprecated please use {@link #addAction(AnAction)} instead */
  @Deprecated
  public @NotNull Notification setListener(@NotNull NotificationListener listener) {
    myListener = listener;
    return this;
  }

  public static @NotNull Notification get(@NotNull AnActionEvent e) {
    return Objects.requireNonNull(e.getData(KEY));
  }

  public static void fire(@NotNull Notification notification, @NotNull AnAction action, @Nullable DataContext context) {
    DataContext dataContext = context != null ? context : CustomizedDataContext.withSnapshot(
      DataContext.EMPTY_CONTEXT, sink -> sink.set(KEY, notification));
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.NOTIFICATION, dataContext);
    IdeUiService.getInstance().performActionDumbAwareWithCallbacks(action, event);
  }

  public @NotNull @LinkLabel String getDropDownText() {
    if (myDropDownText == null) {
      myDropDownText = IdeCoreBundle.message("notifications.action.more");
    }
    return myDropDownText;
  }

  /**
   * @param dropDownText when actions don't fit into the balloon width, they are collapsed and this text is displayed instead
   */
  public @NotNull Notification setDropDownText(@NotNull @LinkLabel String dropDownText) {
    myDropDownText = dropDownText;
    return this;
  }

  public @NotNull CollapseActionsDirection getCollapseDirection() {
    return myCollapseDirection;
  }

  public @NotNull Notification setCollapseDirection(CollapseActionsDirection collapseDirection) {
    myCollapseDirection = collapseDirection;
    return this;
  }

  public @NotNull List<AnAction> getActions() {
    return myActions != null ? myActions : Collections.emptyList();
  }

  /** @see NotificationAction */
  public @NotNull Notification addAction(@NotNull AnAction action) {
    (myActions != null ? myActions : (myActions = new ArrayList<>())).add(action);
    return this;
  }

  public @NotNull Notification addActions(@NotNull Collection<? extends @NotNull AnAction> actions) {
    (myActions != null ? myActions : (myActions = new ArrayList<>())).addAll(actions);
    return this;
  }

  public @Nullable AnAction getContextHelpAction() {
    return myContextHelpAction;
  }

  public @NotNull Notification setContextHelpAction(AnAction action) {
    myContextHelpAction = action;
    return this;
  }

  public @NotNull NotificationType getType() {
    return myType;
  }

  public boolean isExpired() {
    return myExpired.get();
  }

  public void expire() {
    if (!myExpired.compareAndSet(false, true)) return;

    UIUtil.invokeLaterIfNeeded(this::hideBalloon);
    NotificationsManager.getNotificationsManager().expire(this);

    if (myWhenExpired != null) {
      for (Runnable each : myWhenExpired) {
        each.run();
      }
    }
  }

  public Notification whenExpired(@NotNull Runnable whenExpired) {
    (myWhenExpired != null ? myWhenExpired : (myWhenExpired = new ArrayList<>())).add(whenExpired);
    return this;
  }

  public void hideBalloon() {
    hideBalloon(myBalloonRef.getAndSet(null));
  }

  private static void hideBalloon(@Nullable Reference<? extends Balloon> balloonRef) {
    var balloon = SoftReference.dereference(balloonRef);
    if (balloon != null) {
      UIUtil.invokeLaterIfNeeded(balloon::hide);
    }
  }

  public void setBalloon(@NotNull Balloon balloon) {
    var oldBalloon = myBalloonRef.getAndSet(new WeakReference<>(balloon));
    hideBalloon(oldBalloon);
    balloon.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        myBalloonRef.updateAndGet(prev -> SoftReference.dereference(prev) == balloon ? null : prev);
      }
    });
  }

  public @Nullable Balloon getBalloon() {
    return SoftReference.dereference(myBalloonRef.get());
  }

  public void notify(@Nullable Project project) {
    Notifications.Bus.notify(this, project);
  }

  public @NotNull Notification setImportant(boolean important) {
    myImportant = important;
    return this;
  }

  public boolean isImportant() {
    return myImportant != null ? myImportant : getListener() != null || myActions != null && !myActions.isEmpty();
  }

  /**
   * Sets the tool window ID, overriding the ID specified in the notification group registration.
   */
  public @NotNull Notification setToolWindowId(@Nullable String toolWindowId) {
    myToolWindowId = toolWindowId;
    return this;
  }

  public @Nullable String getToolWindowId() {
    return myToolWindowId;
  }

  public final void assertHasTitleOrContent() {
    LOG.assertTrue(hasTitle() || hasContent(), "Notification should have title and/or content; groupId: " + myGroupId);
  }

  @Override
  public String toString() {
    return String.format("Notification{id='%s', myGroupId='%s', myType=%s, myTitle='%s', mySubtitle='%s', myContent='%s'}",
                         id, myGroupId, myType, myTitle, mySubtitle, myContent);
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #Notification(String, String, NotificationType)} and {@link #setIcon} */
  @Deprecated
  public Notification(@NotNull String groupId, @Nullable Icon icon, @NotNull NotificationType type) {
    this(groupId, "", type);
    setIcon(icon);
  }

  /** @deprecated use {@link #Notification(String, String, String, NotificationType)} and {@link #setListener} */
  @Deprecated
  public Notification(@NotNull String groupId,
                      @NotNull @NotificationTitle String title,
                      @NotNull @NotificationContent String content,
                      @NotNull NotificationType type,
                      @Nullable NotificationListener listener) {
    this(groupId, title, content, type);
    myListener = listener;
  }

  /** @deprecated use {@link #Notification(String, String, NotificationType)}, {@link #setIcon}, {@link #setSubtitle}, {@link #setListener} */
  @Deprecated
  public Notification(@NotNull String groupId,
                      @Nullable Icon icon,
                      @Nullable @NotificationTitle String title,
                      @Nullable @NotificationSubtitle String subtitle,
                      @Nullable @NotificationContent String content,
                      @NotNull NotificationType type,
                      @Nullable NotificationListener listener) {
    this(groupId, content != null ? content : "", type);
    setIcon(icon);
    setTitle(title, subtitle);
    myListener = listener;
  }

  /** @deprecated use {@link #addActions(Collection)} or {@link #addAction} */
  @Deprecated(forRemoval = true)
  public final void addActions(@NotNull List<? extends AnAction> actions) {
    addActions((Collection<? extends AnAction>)actions);
  }

  /** @deprecated use {@link #setCollapseDirection} */
  @Deprecated(forRemoval = true)
  public void setCollapseActionsDirection(CollapseActionsDirection collapseDirection) {
    myCollapseDirection = collapseDirection;
  }
  //</editor-fold>
}
