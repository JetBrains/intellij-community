// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.ui.jcef.JCEFHtmlPanel;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;

/**
 * @author Konstantin Bulenkov
 */
public class WebPreviewFileEditor extends UserDataHolderBase implements FileEditor {

  private final Project myProject;
  private final VirtualFile myFile;
  private final JCEFHtmlPanel myPanel;

  public WebPreviewFileEditor(@NotNull Project project, @NotNull WebPreviewVirtualFile file) {
    myProject = project;
    myFile = file.getOriginalFile();
    myPanel = new JCEFHtmlPanel(myFile.getUrl());
    myPanel.getCefBrowser().createImmediately();
    reloadHtml();
    Alarm alarm = new Alarm(this);
    Runnable run = new Runnable() {
      @Override
      public void run() {
        reloadHtml();
        alarm.addRequest(this, 10000);
      }
    };
    alarm.addRequest(run, 10000);

    VirtualFileManager.getInstance().addVirtualFileManagerListener(new MyVirtualFileListener(), this);
  }

  private void reloadHtml() {
    try {
      myPanel.setHtml(FileUtil.loadTextAndClose(myFile.getInputStream()));
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel.getComponent();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myPanel.getComponent();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName() {
    return "Preview for " + myFile.getName();
  }

  @Override
  public void setState(@NotNull FileEditorState state) {

  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public @Nullable FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {

  }

  class MyVirtualFileListener implements VirtualFileListener, @NotNull VirtualFileManagerListener {
    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
      if (myFile.equals(event.getFile())) {
        reloadHtml();
      }
    }

    @Override
    public void beforeFileDeletion(@NotNull VirtualFileEvent event) {
      if (myFile.equals(event.getFile())) {
        FileEditorManager.getInstance(myProject).closeFile(myFile);
      }
    }
  }
}
