// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.integration.ui.actions;

import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

import static com.intellij.history.core.LocalHistoryNotificationIdsHolder.LABEL_CREATED;
import static com.intellij.history.core.LocalHistoryNotificationIdsHolder.LABEL_CREATION_FAILED;
import static com.intellij.history.core.LocalHistoryNotificationIdsHolderKt.getLocalHistoryNotificationGroup;
import static com.intellij.history.integration.ui.actions.ShowLocalHistoryUtilKt.canShowLocalHistoryFor;
import static com.intellij.history.integration.ui.actions.ShowLocalHistoryUtilKt.showLocalHistoryFor;

@ApiStatus.Internal
public final class PutLabelAction extends LocalHistoryAction {
  @Override
  protected void actionPerformed(@NotNull Project p, @NotNull IdeaGateway gw, @NotNull AnActionEvent e) {
    String labelName = Messages.showInputDialog(p, LocalHistoryBundle.message("put.label.name"),
                                                LocalHistoryBundle.message("put.label.dialog.title"), null, "",
                                                new NonEmptyInputValidator());

    if (labelName == null) return;

    Label label = LocalHistory.getInstance().putUserLabel(p, labelName);
    if (label == Label.NULL_INSTANCE) {
      getLocalHistoryNotificationGroup()
        .createNotification(LocalHistoryBundle.message("notification.content.label.creation.failed", labelName),
                            NotificationType.ERROR)
        .setDisplayId(LABEL_CREATION_FAILED)
        .notify(p);
      return;
    }

    Notification notification = getLocalHistoryNotificationGroup()
      .createNotification(LocalHistoryBundle.message("notification.content.label.name.created", labelName),
                          NotificationType.INFORMATION)
      .setDisplayId(LABEL_CREATED);
    Set<VirtualFile> baseDirectories = BaseProjectDirectories.getBaseDirectories(p);
    if (canShowLocalHistoryFor(gw, baseDirectories)) {
      notification.addAction(NotificationAction.createSimple(LocalHistoryBundle.message("notification.action.view.local.history"), () -> {
        showLocalHistoryFor(p, gw, new ArrayList<>(baseDirectories));
      }));
    }
    notification.notify(p);
  }
}