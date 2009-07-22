package com.intellij.notification.impl;

import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Date;
import java.util.Collection;

/**
 * @author spleaner
 */
public class NotificationsManager extends NotificationsBase implements ApplicationComponent {
  private NotificationModel<Notification> myModel = new NotificationModel<Notification>();

  public static NotificationsManager getNotificationsManager() {
    return ApplicationManager.getApplication().getComponent(NotificationsManager.class);
  }

  private static final NotNullFunction<Notification, Boolean> ALL = new NotNullFunction<Notification, Boolean>() {
    @NotNull
    public Boolean fun(Notification notificationWrapper) {
      return true;
    }
  };

  private static final NotNullFunction<Notification, Boolean> APPLICATION = new NotNullFunction<Notification, Boolean>() {
    @NotNull
    public Boolean fun(Notification notificationWrapper) {
      return ((NotificationWrapper)notificationWrapper).getProject() == null;
    }
  };

  public void clear(@Nullable Project project) {
    myModel.clear(createFilter(project, true));
  }

  public void archive() {
    myModel.archive();
  }

  @NotNull
  public String getComponentName() {
    return "NotificationsManager";
  }

  public void initComponent() {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(TOPIC, this);
  }

  public NotificationModel<Notification> getModel() {
    return myModel;
  }

  public void disposeComponent() {
    myModel.clear(ALL);
  }

  protected void doNotify(String id, String name, String description, NotificationType type, NotificationListener listener) {
    myModel.add(new NotificationWrapper(new NotificationImpl(id, name, description, type, listener), null));
  }

  public void invalidateAll(@NotNull String id) {
    myModel.invalidateAll(id, ALL);
  }

  private static NotNullFunction<Notification, Boolean> createFilter(@Nullable final Project project, final boolean strict) {
    return project == null ? APPLICATION : new ProjectFilter(project, strict);
  }

  public boolean hasNotifications(@Nullable final Project project) {
    return myModel.getCount(createFilter(project, false)) > 0;
  }

  @Nullable
  public Notification getLatestNotification(@Nullable final Project project) {
    return myModel.getFirst(createFilter(project, false));
  }

  public void notify(NotificationImpl notification, Project project) {
    myModel.add(new NotificationWrapper(notification, project));
  }

  public void invalidateAll(final String id, @Nullable final Project project) {
    myModel.invalidateAll(id, createFilter(project, false));
  }

  public void removeListener(@NotNull final NotificationModelListener<Notification> notificationListener) {
    myModel.removeListener(notificationListener);
  }

  public void addListener(@NotNull final NotificationModelListener<Notification> notificationListener) {
    myModel.addListener(notificationListener);
  }

  public int count(@Nullable final Project project) {
    return myModel.getCount(createFilter(project, false));
  }

  @Nullable
  public Notification remove(Notification notification) {
    return myModel.remove(notification);
  }

  @Nullable
  public Notification getAt(final int index, @Nullable final Project project) {
    return myModel.get(index, createFilter(project, false));
  }

  public void remove(@NotNull final Notification[] notification) {
    myModel.remove(notification);
  }

  public Collection<Notification> getByType(@Nullable final NotificationType type, @Nullable final Project project) {
    return myModel.getByType(type, createFilter(project, false));
  }

  public Collection<Notification> getArchive(@Nullable Project project) {
    return myModel.getArchive(createFilter(project, false));
  }

  private static class ProjectFilter implements NotNullFunction<Notification, Boolean> {
    private Project myProject;
    private boolean myStrict;

    private ProjectFilter(@NotNull final Project project, final boolean strict) {
      myProject = project;
      myStrict = strict;
    }

    @NotNull
    public Boolean fun(Notification notificationWrapper) {
      final Project project = ((NotificationWrapper)notificationWrapper).getProject();
      return myStrict ? project == myProject : project == null || project == myProject;
    }
  }

  private static class NotificationWrapper implements Notification {
    private Project myProject;
    private Notification myNotification;

    private NotificationWrapper(@NotNull final Notification notification, @Nullable final Project project) {
      myNotification = notification;
      myProject = project;
    }

    @NotNull
    public String getId() {
      return myNotification.getId();
    }

    @NotNull
    public String getName() {
      return myNotification.getName();
    }

    @NotNull
    public String getDescription() {
      return myNotification.getDescription();
    }

    @NotNull
    public NotificationListener getListener() {
      return myNotification.getListener();
    }

    @NotNull
    public NotificationType getType() {
      return myNotification.getType();
    }

    public Date getDate() {
      return myNotification.getDate();
    }

    @NotNull
    public Icon getIcon() {
      return myNotification.getIcon();
    }

    @NotNull
    public Color getBackgroundColor() {
      return myNotification.getBackgroundColor();
    }

    public Project getProject() {
      return myProject;
    }
  }
}
