// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.ide.JavaUiBundle;
import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class RepositoryLibraryResolveErrorNotification extends Notification {
  private final List<String> myErrors = new ArrayList<>();

  RepositoryLibraryResolveErrorNotification() {
    super(
      JarRepositoryManager.GROUP.getDisplayId(),
      JavaUiBundle.message("notification.title.repository.library.synchronization"),
      "",
      NotificationType.ERROR
    );
  }

  void addLibraryResolveError(@NotNull RepositoryLibraryProperties lib) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    myErrors.add(lib.getMavenId());
    String message;
    int limit = 10;
    if (myErrors.size() < limit) {
      message = JavaUiBundle.message("notification.content.no.files.were.downloaded",
                                     StringUtil.join(myErrors, ", "));

    }
    else {
      message = JavaUiBundle.message("notification.content.no.files.were.downloaded.multiple",
                                     StringUtil.join(ContainerUtil.getFirstItems(myErrors, limit), ", "),
                                     myErrors.size() - limit);
    }
    setContent(message);
    expire();
    Notifications.Bus.notify(this);
  }

  static synchronized void showOrUpdate(@NotNull RepositoryLibraryProperties libWithError, @NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    RepositoryLibraryResolveErrorNotification[] notifications =
      NotificationsManager.getNotificationsManager().getNotificationsOfType(RepositoryLibraryResolveErrorNotification.class, project);
    RepositoryLibraryResolveErrorNotification notification = notifications.length > 0 ? notifications[0] : null;
    if (notification == null) {
      notification = new RepositoryLibraryResolveErrorNotification();
    }
    notification.addLibraryResolveError(libWithError);
  }
}
