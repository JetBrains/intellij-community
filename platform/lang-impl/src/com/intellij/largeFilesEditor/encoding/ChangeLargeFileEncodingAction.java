// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.encoding;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.ChangeFileEncodingAction;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.nio.charset.Charset;

class ChangeLargeFileEncodingAction extends ChangeFileEncodingAction {

  private static final Logger logger = Logger.getInstance(ChangeLargeFileEncodingAction.class);

  private final StatusBar statusBar;

  ChangeLargeFileEncodingAction(StatusBar statusBar) {
    this.statusBar = statusBar;
  }

  private boolean chosen(@NotNull Charset charset) {
    LargeFileEditorAccess largeFileEditorAccess = LargeFileEditorAccessor.getAccess(statusBar);
    if (largeFileEditorAccess == null) {
      logger.warn("tried to change encoding while editor is not accessible");
      return false;
    }
    boolean result = largeFileEditorAccess.tryChangeEncoding(charset);
    updateWidget();
    return result;
  }

  private void updateWidget() {
    StatusBarWidget widget = statusBar.getWidget(LargeFileEncodingWidget.WIDGET_ID);
    if (widget instanceof LargeFileEncodingWidget) {
      ((LargeFileEncodingWidget)widget).requestUpdate();
    }
    else {
      logger.warn("[LargeFileEditorSubsystem] ChangeFileEncodingAction.updateWidget(): "
                  + (widget == null
                     ? " variable 'widget' is null"
                     : " variable is instance of " + widget.getClass().getName()));
    }
  }

  @Override
  protected boolean chosen(Document document, Editor editor, @Nullable VirtualFile virtualFile, byte[] bytes, @NotNull Charset charset) {
    return chosen(charset);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(true);
  }

  ListPopup createPopup(VirtualFile vFile, Editor editor, Component componentParent) {
    DataContext dataContext = wrapInDataContext(vFile, editor, componentParent);
    DefaultActionGroup group = createActionGroup(vFile, editor, null, null, null);
    return JBPopupFactory.getInstance().createActionGroupPopup(getTemplatePresentation().getText(),
                                                               group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
  }

  private static DataContext wrapInDataContext(VirtualFile vFile, Editor editor, Component componentParent) {
    DataContext parent = DataManager.getInstance().getDataContext(componentParent);
    return SimpleDataContext.builder()
      .setParent(parent)
      .add(CommonDataKeys.VIRTUAL_FILE, vFile)
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, new VirtualFile[]{vFile})
      .add(CommonDataKeys.PROJECT, editor.getProject())
      .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, editor.getComponent())
      .build();
  }
}