// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypesBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.messages.SimpleMessageBusConnection;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OSFileAssociationStartupConfigurator implements ApplicationInitializedListener {
  private final static Logger LOG = Logger.getInstance(OSFileAssociationStartupConfigurator.class);

  @Override
  public void componentsInitialized() {
    OSFileAssociationPreferences preferences = OSFileAssociationPreferences.getInstance();
    if (!preferences.fileTypeNames.isEmpty() && preferences.ideLocationChanged()) {
      LOG.info("Restoring file type associations on IDE location change");
      SimpleMessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().simpleConnect();
      MyResultHandler resultHandler = new MyResultHandler(connection);
      connection.subscribe(AppLifecycleListener.TOPIC, resultHandler);
      connection.subscribe(ProjectManager.TOPIC, resultHandler);
      OSAssociateFileTypesUtil.restoreAssociations(resultHandler);
      preferences.updateIdeLocationHash();
    }
  }

  private static class MyResultHandler implements OSAssociateFileTypesUtil.Callback,
                                                  AppLifecycleListener,
                                                  ProjectManagerListener {

    private final static String NOTIF_GROUP_ID = "os.file.ide.association";

    private Notification myNotification;
    private SimpleMessageBusConnection myConnection;

    private MyResultHandler(@NotNull SimpleMessageBusConnection connection) {
      myConnection = connection;
    }

    @Override
    public void beforeStart() {}

    @Override
    public void onSuccess(boolean isOsRestartRequired) {
      LOG.info("File-IDE associations successfully restored.");
      myNotification = new Notification(NOTIF_GROUP_ID, getNotifTitle(),
                                        FileTypesBundle.message("filetype.associate.notif.success",
                                                                ApplicationInfo.getInstance().getFullApplicationName()) +
                                        (isOsRestartRequired ? "\n" + FileTypesBundle.message("filetype.associate.message.os.restart") : ""),
                                        isOsRestartRequired ? NotificationType.WARNING : NotificationType.INFORMATION);
    }

    @Override
    public void onFailure(@NotNull @Nls String errorMessage) {
      LOG.warn("File-IDE associations can't be restored: " + errorMessage);
      myNotification = new Notification(NOTIF_GROUP_ID, getNotifTitle(),
                                        FileTypesBundle.message("filetype.associate.notif.error"),
                                        NotificationType.ERROR);
    }

    private static @Nls String getNotifTitle() {
      return FileTypesBundle.message("filetype.associate.notif.title");
    }

    @Override
    public void welcomeScreenDisplayed() {
      doNotify(null);
    }

    @Override
    public void projectOpened(@NotNull Project project) {
      doNotify(project);
    }

    private void doNotify(@Nullable Project project) {
      if (myNotification != null) {
        Notifications.Bus.notify(myNotification, project);
        myConnection.disconnect();
        myConnection = null;
        myNotification = null;
      }
    }
  }
}
