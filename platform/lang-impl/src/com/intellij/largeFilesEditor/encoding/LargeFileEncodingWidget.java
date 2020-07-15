// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.encoding;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class LargeFileEncodingWidget extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget {
  public static final String WIDGET_ID = "largeFileEncodingWidget";

  private static final Logger logger = Logger.getInstance(LargeFileEncodingWidget.class);

  private final TextPanel myComponent;
  private Alarm myUpdateAlarm;

  private boolean myActionEnabled;

  public LargeFileEncodingWidget(@NotNull final Project project) {
    super(project);
    myComponent = new TextPanel.WithIconAndArrows();
    myComponent.setBorder(WidgetBorder.WIDE);
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    requestUpdate();
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    requestUpdate();
  }

  @Override
  public StatusBarWidget copy() {
    return new LargeFileEncodingWidget(getProject());
  }

  @Override
  @NotNull
  public String ID() {
    return WIDGET_ID;
  }

  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    myUpdateAlarm = new Alarm(this);
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        requestUpdate();
        tryShowPopup();
        return true;
      }
    }.installOn(myComponent, true);
    update();
  }

  private void tryShowPopup() {
    if (!myActionEnabled) {
      return;
    }
    LargeFileEditorAccess largeFileEditorAccess = LargeFileEditorAccessor.getAccess(myStatusBar);
    if (largeFileEditorAccess != null) {
      showPopup(largeFileEditorAccess);
    }
    else {
      logger.warn("[LargeFileEditorSubsystem] LargeFileEncodingWidget.tryShowPopup():" +
                  " this method was called while LargeFileEditor is not available as active text editor");
      requestUpdate();
    }
  }

  private void showPopup(@NotNull LargeFileEditorAccess largeFileEditorAccess) {
    ChangeLargeFileEncodingAction action = new ChangeLargeFileEncodingAction(myStatusBar);
    JComponent where = getComponent();
    ListPopup popup = action.createPopup(largeFileEditorAccess.getVirtualFile(), largeFileEditorAccess.getEditor(),
                                         where);
    RelativePoint pos = JBPopupFactory.getInstance().guessBestPopupLocation(where);
    popup.showInScreenCoordinates(where, pos.getScreenPoint());
  }

  public void requestUpdate() {
    if (myUpdateAlarm.isDisposed()) return;

    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(() -> update(), 200, ModalityState.any());
  }

  private void update() {
    if (isDisposed()) return;

    LargeFileEditorAccess largeFileEditorAccess = LargeFileEditorAccessor.getAccess(myStatusBar);

    myActionEnabled = false;
    String charsetName;
    String toolTipText;

    if (largeFileEditorAccess == null) {
      toolTipText = "";
      charsetName = "";
      myComponent.setVisible(false);
    }
    else {
      myActionEnabled = true;
      charsetName = largeFileEditorAccess.getCharsetName();
      toolTipText = EditorBundle.message("large.file.editor.tooltip.file.encoding.is.some", charsetName);
      myComponent.setVisible(true);
    }

    myComponent.setToolTipText(toolTipText);
    myComponent.setText(charsetName);

    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
    else {
      logger.warn("[LargeFileEditorSubsystem] LargeFileEncodingWidget.requestUpdate(): myStatusBar is null!!!)");
    }
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
