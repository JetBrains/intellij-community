package com.intellij.database.datagrid;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;

public interface DataGridNotifications {
  String EXTRACTORS_GROUP_ID = "Extractors";
  String PASTE_GROUP_ID = "GridPaste";
  NotificationGroup EXTRACTORS_GROUP = NotificationGroupManager.getInstance().getNotificationGroup(EXTRACTORS_GROUP_ID);
  NotificationGroup PASTE_GROUP = NotificationGroupManager.getInstance().getNotificationGroup(PASTE_GROUP_ID);
}
