// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.jcef.JCEFHtmlPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerBundle;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;


/**
 * @author Konstantin Bulenkov
 */
public class WebPreviewFileEditor extends UserDataHolderBase implements FileEditor {
  public static final String WEB_PREVIEW_RELOAD_TOOLTIP_ID = "web.preview.reload.on.save";
  private final VirtualFile myFile;
  private final JCEFHtmlPanel myPanel;
  private final String myUrl;
  private static int previewsOpened = 0;

  public WebPreviewFileEditor(@NotNull Project project, @NotNull WebPreviewVirtualFile file) {
    myFile = file.getOriginalFile();
    myPanel = new JCEFHtmlPanel(file.getPreviewUrl().toExternalForm());
    myUrl = file.getPreviewUrl().toExternalForm();
    reloadPage();
    previewsOpened++;
    showPreviewTooltip();
  }

  private void reloadPage() {
    FileDocumentManager.getInstance().saveAllDocuments();
    ApplicationManager.getApplication().saveAll();
    myPanel.loadURL(myUrl);
  }

  private void showPreviewTooltip() {
    ApplicationManager.getApplication().invokeLater(() -> {
      GotItTooltip gotItTooltip = new GotItTooltip(WEB_PREVIEW_RELOAD_TOOLTIP_ID, BuiltInServerBundle.message("reload.on.save.preview.got.it.content"), this);
      if (!gotItTooltip.canShow()) return;

      gotItTooltip
        .withHeader(BuiltInServerBundle.message("reload.on.save.preview.got.it.title"))
        .withPosition(Balloon.Position.above);

      gotItTooltip.show(myPanel.getComponent(), (c, b) ->  new Point(0, 0) );
    });
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

  public static boolean isPreviewOpened() {
    return previewsOpened > 0;
  }

  @Override
  public @Nullable FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {
    previewsOpened--;
  }
}
