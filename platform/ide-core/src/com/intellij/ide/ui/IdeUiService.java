// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.UnlockOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLSocketFactory;
import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

@ApiStatus.Experimental
public class IdeUiService {

  public void revealFile(File file) {
  }

  public UnlockOption askForUnlock(@NotNull Project project,
                                   List<VirtualFile> files) {
    return null;
  }

  public boolean isFileRecentlyChanged(Project project, VirtualFile file) {

    return false;
  }

  public void logUsageEvent(Class clazz, String groupId, String eventId) {

  }

  public void systemNotify(String title, String text) {

  }

  public DataContext createUiDataContext(Component component) {
    return null;
  }

  public Component getComponentFromRecentMouseEvent() {
    return null;
  }

  public void browse(URL url) {

  }

  public void browse(String url) {

  }

  public void performActionDumbAwareWithCallbacks(AnAction action, AnActionEvent event) {

  }

  public void notifyByBalloon(Project project,
                              String toolWindowId,
                              MessageType messageType,
                              String title, String fullMessage, String description,
                              Icon icon,
                              HyperlinkListener listener) {

  }

  public URLConnection openHttpConnection(String url) throws IOException {
    return null;
  }

  public SSLSocketFactory getSslSocketFactory() {
    return null;
  }

  public boolean isUseSafeWrite() {
    return false;
  }

  public static IdeUiService getInstance() {
    return ApplicationManager.getApplication().getService(IdeUiService.class);
  }
}
