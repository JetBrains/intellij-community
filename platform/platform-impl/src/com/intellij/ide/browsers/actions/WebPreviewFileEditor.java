// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.jcef.JCEFHtmlPanel;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author Konstantin Bulenkov
 */
public class WebPreviewFileEditor extends UserDataHolderBase implements FileEditor {
  private final VirtualFile myFile;
  private final JCEFHtmlPanel myPanel;

  public WebPreviewFileEditor(@NotNull Project project, @NotNull WebPreviewVirtualFile file) {
    myFile = file.getOriginalFile();
    myPanel = new JCEFHtmlPanel(myFile.getUrl());
    myPanel.getCefBrowser().createImmediately();
    Alarm alarm = new Alarm(this);
    PsiFile psiFile = PsiManager.getInstance(project).findFile(myFile);
    if (psiFile != null) {
      Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
      reloadHtml(document);
      if (document != null) {
        document.addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent event) {
            alarm.cancelAllRequests();
            alarm.addRequest(() -> reloadHtml(document), 100);
          }
        });
      }
    }
  }

  private void reloadHtml(Document document) {
    FileDocumentManager.getInstance().saveDocument(document);
    myPanel.setHtml(document.getText());
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
    return IdeBundle.message("web.preview.file.editor.name", myFile.getName());
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
}
