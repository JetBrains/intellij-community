// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.util.NlsContexts.*;

/**
 * Notification bean class contains <b>title:</b>subtitle, content (plain text or HTML) and actions.
 * <br><br>
 * The notifications, generally, are shown in the balloons that appear on the screen when the corresponding events take place.<br>
 * Notification balloon is of two types: two or three lines.<br>
 * Two lines: title and content line; title and actions; content line and actions; contents on two lines.<br>
 * Three lines: title and content line and actions; contents on two lines and actions; contents on three lines or more; etc.
 * <br><br>
 * Warning: be careful not to use the links in HTML content, use {@link #addAction(AnAction)}
 *
 * @see NotificationAction
 * @see com.intellij.notification.SingletonNotificationManager
 *
 * @author spleaner
 * @author Alexander Lobas
 */
public class Notification {
  /**
   * Which actions to keep and which to show under the "Actions" dropdown link if actions do not fit horizontally
   * into the width of the notification.
   */
  public enum CollapseActionsDirection { KEEP_LEFTMOST, KEEP_RIGHTMOST }

  private static final Logger LOG = Logger.getInstance(Notification.class);
  private static final DataKey<Notification> KEY = DataKey.create("Notification");

  public final String id;

  /**
   * Unique ID for usage statistics.
   */
  @Nullable
  public final String displayId;

  private final String myGroupId;
  private Icon myIcon;
  private final NotificationType myType;

  private String myTitle;
  private String mySubtitle;
  private String myContent;
  private NotificationListener myListener;
  private String myDropDownText;
  private List<AnAction> myActions;
  private CollapseActionsDirection myCollapseActionsDirection = CollapseActionsDirection.KEEP_RIGHTMOST;
  private AnAction myContextHelpAction;

  private final AtomicBoolean myExpired = new AtomicBoolean(false);
  private Runnable myWhenExpired;
  private Boolean myImportant;
  private WeakReference<Balloon> myBalloonRef;
  private final long myTimestamp;

  public Notification(@NotNull String groupId, @Nullable Icon icon, @NotNull NotificationType type) {
    this(groupId, icon, null, null, null, type, null);
  }

  /**
   * @param groupId        notification group id
   * @param icon           notification icon, if <b>null</b> used icon from type
   * @param title          notification title
   * @param subtitle       notification subtitle
   * @param content        notification content
   * @param type           notification type
   * @param listener       notification lifecycle listener
   */
  public Notification(@NotNull @NonNls String groupId,
                      @Nullable Icon icon,
                      @Nullable @NotificationTitle String title,
                      @Nullable @NotificationSubtitle String subtitle,
                      @Nullable @NotificationContent String content,
                      @NotNull NotificationType type,
                      @Nullable NotificationListener listener) {
    myGroupId = groupId;
    myTitle = StringUtil.notNullize(title);
    myContent = StringUtil.notNullize(content);
    myType = type;
    myListener = listener;
    myTimestamp = System.currentTimeMillis();

    myIcon = icon;
    mySubtitle = subtitle;

    this.displayId = null;
    id = calculateId(this);
  }

  public Notification(@NotNull @NonNls String groupId,
                      @NotNull @NotificationTitle String title,
                      @NotNull @NotificationContent String content,
                      @NotNull NotificationType type) {
    this(groupId, null, title, content, type, null);
  }

  /**
   * @param groupId        notification group id
   * @param title          notification title
   * @param content        notification content
   * @param type           notification type
   * @param listener       notification lifecycle listener
   */
  public Notification(@NotNull @NonNls String groupId,
                      @NotNull @NotificationTitle String title,
                      @NotNull @NotificationContent String content,
                      @NotNull NotificationType type,
                      @Nullable NotificationListener listener) {
    this(groupId, null, title, content, type, listener);
  }

  public Notification(@NotNull @NonNls String groupId,
                      @Nullable @NonNls String displayId,
                      @NotNull @NotificationTitle String title,
                      @NotNull @NotificationContent String content,
                      @NotNull NotificationType type,
                      @Nullable NotificationListener listener) {
    myGroupId = groupId;
    myTitle = title;
    myContent = content;
    myType = type;
    myListener = listener;
    myTimestamp = System.currentTimeMillis();

    this.displayId = displayId;
    id = calculateId(this);
  }

  /**
   * Returns the time (in milliseconds since Jan 1, 1970) when the notification was created.
   */
  public long getTimestamp() {
    return myTimestamp;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public Notification setIcon(@Nullable Icon icon) {
    myIcon = icon;
    return this;
  }

  @NotNull
  public String getGroupId() {
    return myGroupId;
  }

  public boolean hasTitle() {
    return !StringUtil.isEmptyOrSpaces(myTitle) || !StringUtil.isEmptyOrSpaces(mySubtitle);
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  public Notification setTitle(@Nullable @NotificationTitle String title) {
    myTitle = StringUtil.notNullize(title);
    return this;
  }

  @NotNull
  public Notification setTitle(@Nullable @NotificationTitle String title,
                               @Nullable @NotificationSubtitle String subtitle) {
    return setTitle(title).setSubtitle(subtitle);
  }

  @Nullable
  public String getSubtitle() {
    return mySubtitle;
  }

  @NotNull
  public Notification setSubtitle(@Nullable String subtitle) {
    mySubtitle = subtitle;
    return this;
  }

  public boolean hasContent() {
    return !StringUtil.isEmptyOrSpaces(myContent);
  }

  @NotNull
  public String getContent() {
    return myContent;
  }

  @NotNull
  public Notification setContent(@Nullable String content) {
    myContent = StringUtil.notNullize(content);
    return this;
  }

  @Nullable
  public NotificationListener getListener() {
    return myListener;
  }

  @NotNull
  public Notification setListener(@NotNull NotificationListener listener) {
    myListener = listener;
    return this;
  }

  @NotNull
  public List<AnAction> getActions() {
    return ContainerUtil.notNullize(myActions);
  }

  @NotNull
  public static Notification get(@NotNull AnActionEvent e) {
    //noinspection ConstantConditions
    return e.getData(KEY);
  }

  public static void fire(@NotNull final Notification notification, @NotNull AnAction action) {
    fire(notification, action, null);
  }

  public static void fire(@NotNull final Notification notification, @NotNull AnAction action, @Nullable DataContext context) {
    AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.NOTIFICATION, dataId -> {
      if (KEY.is(dataId)) {
        return notification;
      }
      return context == null ? null : context.getData(dataId);
    });
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event, event.getDataContext());
    }
  }

  public static void setDataProvider(@NotNull Notification notification, @NotNull JComponent component) {
    DataManager.registerDataProvider(component, dataId -> KEY.getName().equals(dataId) ? notification : null);
  }

  @NotNull
  public String getDropDownText() {
    if (myDropDownText == null) {
      myDropDownText = "Actions";
    }
    return myDropDownText;
  }

  /**
   * @param dropDownText text for popup when all actions collapsed (when all actions width more notification width)
   */
  @NotNull
  public Notification setDropDownText(@NotNull @LinkLabel String dropDownText) {
    myDropDownText = dropDownText;
    return this;
  }

  public CollapseActionsDirection getCollapseActionsDirection() {
    return myCollapseActionsDirection;
  }

  public void setCollapseActionsDirection(CollapseActionsDirection collapseActionsDirection) {
    myCollapseActionsDirection = collapseActionsDirection;
  }

  /**
   * @see NotificationAction
   */
  @NotNull
  public Notification addAction(@NotNull AnAction action) {
    if (myActions == null) {
      myActions = new ArrayList<>();
    }
    myActions.add(action);
    return this;
  }

  public Notification setContextHelpAction(AnAction action) {
    myContextHelpAction = action;
    return this;
  }

  public AnAction getContextHelpAction() {
    return myContextHelpAction;
  }

  @NotNull
  public NotificationType getType() {
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
    if (myBalloonRef != null) {
      final Balloon balloon = myBalloonRef.get();
      if (balloon != null) {
        balloon.hide();
      }
      myBalloonRef = null;
    }
  }

  public void setBalloon(@NotNull final Balloon balloon) {
    hideBalloon();
    myBalloonRef = new WeakReference<>(balloon);
    balloon.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        if (SoftReference.dereference(myBalloonRef) == balloon) {
          myBalloonRef = null;
        }
      }
    });
  }

  @Nullable
  public Balloon getBalloon() {
    return SoftReference.dereference(myBalloonRef);
  }

  public void notify(@Nullable Project project) {
    Notifications.Bus.notify(this, project);
  }

  public Notification setImportant(boolean important) {
    myImportant = important;
    return this;
  }

  public boolean isImportant() {
    if (myImportant != null) {
      return myImportant;
    }

    return getListener() != null || !ContainerUtil.isEmpty(myActions);
  }

  @NotNull
  private static String calculateId(@NotNull Object notification) {
    return System.currentTimeMillis() + "." + System.identityHashCode(notification);
  }

  public final void assertHasTitleOrContent() {
    LOG.assertTrue(hasTitle() || hasContent(), "Notification should have title and/or content; groupId: " + myGroupId);
  }

  @Override
  public String toString() {
    return String.format("Notification{id='%s', myGroupId='%s', myType=%s, myTitle='%s', mySubtitle='%s', myContent='%s'}",
                         id, myGroupId, myType, myTitle, mySubtitle, myContent);
  }
}
