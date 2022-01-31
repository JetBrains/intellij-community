// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.UnlockOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Experimental
public class IdeUiService {

  public void revealFile(Path file) {
  }

  public UnlockOption askForUnlock(@NotNull Project project, List<? extends VirtualFile> files) {
    return null;
  }

  public boolean isFileRecentlyChanged(Project project, VirtualFile file) {

    return false;
  }

  public void logIdeScriptUsageEvent(Class<?> clazz) {

  }

  public void systemNotify(@NlsContexts.SystemNotificationTitle String title, @NlsContexts.SystemNotificationText String text) {

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
                              @Nls String title, @Nls String fullMessage, @Nls String description,
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

  public VirtualFile[] chooseFiles(FileChooserDescriptor descriptor, Project project, VirtualFile toSelect) {
    return VirtualFile.EMPTY_ARRAY;
  }

  public VirtualFile chooseFile(FileChooserDescriptor descriptor, JComponent component, Project project, VirtualFile dir) {
    return null;
  }

  public SSLContext getSslContext() {
    return null;
  }

  public String getProxyLogin() {
    return null;
  }

  public String getPlainProxyPassword() {
    return null;
  }

  public boolean isProxyAuth() {
    return false;
  }

  public List<Proxy> getProxyList(URL url) {
    return new ArrayList<>();
  }

  public void prepareURL(String url) throws IOException {

  }

  public void showRefactoringMessageDialog(String title,
                                           String message,
                                           String helpTopic,
                                           String iconId,
                                           boolean showCancelButton,
                                           Project project) {

  }

  public void showErrorHint(Editor editor, String message) {

  }


  public static IdeUiService getInstance() {
    return ApplicationManager.getApplication().getService(IdeUiService.class);
  }

  public boolean showErrorDialog(String title, String message) {
    return false;
  }
}
