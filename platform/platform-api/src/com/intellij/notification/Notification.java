// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 * <p><b>Warning:</b> please avoid links in HTML content, use {@link #addAction(AnAction)} instead.</p>
 * <p>Use {@link Notifications.Bus} to show notifications.</p>
 *
 * @see NotificationAction
 * @see com.intellij.notification.SingletonNotificationManager
 *
 * @author spleaner
 * @author Alexander Lobas
 */
public class Notification {
  /**
   * Tells which actions to keep (i.e. do not put under the "Actions" dropdown) when actions do not fit horizontally
   * into the width of the notification.
   */
  public enum CollapseActionsDirection {KEEP_LEFTMOST, KEEP_RIGHTMOST}

  private static final Logger LOG = Logger.getInstance(Notification.class);
  private static final DataKey<Notification> KEY = DataKey.create("Notification");

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
  private @Nullable List<AnAction> myActions;
  private @NotNull CollapseActionsDirection myCollapseDirection = CollapseActionsDirection.KEEP_RIGHTMOST;
  private @Nullable AnAction myContextHelpAction;
  private @Nullable Runnable myWhenExpired;
  private @Nullable Boolean myImportant;

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

  public @NotNull Notification setDisplayId(@NotNull String displayId) {
    this.myDisplayId = displayId;
    return this;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public @NotNull Notification setIcon(@Nullable Icon icon) {
    myIcon = icon;
    return this;
  }

  public @NotNull String getGroupId() {
    return myGroupId;
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

  public @NotNull Notification setListener(@NotNull NotificationListener listener) {
    myListener = listener;
    return this;
  }

  public static @NotNull Notification get(@NotNull AnActionEvent e) {
    return Objects.requireNonNull(e.getData(KEY));
  }

  public static void fire(final @NotNull Notification notification, @NotNull AnAction action) {
    fire(notification, action, null);
  }

  public static void fire(final @NotNull Notification notification, @NotNull AnAction action, @Nullable DataContext context) {
    DataContext contextWrapper = dataId -> KEY.is(dataId) ? notification : context != null ? context.getData(dataId) : null;
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.NOTIFICATION, contextWrapper);
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event);
    }
  }

  public static void setDataProvider(@NotNull Notification notification, @NotNull JComponent component) {
    DataManager.registerDataProvider(component, dataId -> KEY.is(dataId) ? notification : null);
  }

  public @NotNull @LinkLabel String getDropDownText() {
    if (myDropDownText == null) {
      myDropDownText = IdeBundle.message("link.label.actions");
    }
    return myDropDownText;
  }

  /**
   * @param dropDownText text for popup when all actions collapsed (when all actions width more notification width)
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

  /**
   * @see NotificationAction
   */
  public @NotNull Notification addAction(@NotNull AnAction action) {
    (myActions != null ? myActions : (myActions = new ArrayList<>())).add(action);
    return this;
  }

  public @NotNull Notification addActions(@NotNull Collection<? extends AnAction> actions) {
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

    Runnable whenExpired = myWhenExpired;
    if (whenExpired != null) whenExpired.run();
  }

  public Notification whenExpired(@Nullable Runnable whenExpired) {
    myWhenExpired = whenExpired;
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
  @Deprecated
  public final void addActions(@NotNull List<? extends AnAction> actions) {
    addActions((Collection<? extends AnAction>)actions);
  }

  /** @deprecated use {@link #getCollapseDirection} */
  @Deprecated
  public CollapseActionsDirection getCollapseActionsDirection() {
    return myCollapseDirection;
  }

  /** @deprecated use {@link #setCollapseDirection} */
  @Deprecated
  public void setCollapseActionsDirection(CollapseActionsDirection collapseDirection) {
    myCollapseDirection = collapseDirection;
  }
  //</editor-fold>
}
