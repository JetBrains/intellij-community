// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.largeFilesEditor.encoding;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.LazyInitializer;
import com.intellij.util.LazyInitializer.LazyValue;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.DebouncedUpdates;
import com.intellij.util.ui.update.UpdateQueue;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;

import static com.intellij.ide.HelpTooltipKt.setToolTipText;
import static com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope;
import static kotlinx.coroutines.CoroutineScopeKt.cancel;

class LargeFileEncodingWidget extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget {
  public static final String WIDGET_ID = "largeFileEncodingWidget";

  private static final Logger logger = Logger.getInstance(LargeFileEncodingWidget.class);

  protected final CoroutineScope myParentScope;

  private final CoroutineScope myScope;
  private final LazyValue<TextPanel> myComponent;
  private final UpdateQueue<Unit> myUpdateQueue;

  private boolean myActionEnabled;

  LargeFileEncodingWidget(@NotNull Project project, @NotNull CoroutineScope parentScope) {
    super(project);

    myScope = childScope(parentScope, "LargeFileEncodingWidget", EmptyCoroutineContext.INSTANCE, true);
    myParentScope = parentScope;
    myComponent = LazyInitializer.create(() -> {
      var result = new TextPanel.WithIconAndArrows();
      result.setBorder(JBUI.CurrentTheme.StatusBar.Widget.border());
      return result;
    });
    myUpdateQueue = DebouncedUpdates.<Unit>forScope(myScope, "LargeFileEncodingWidget", 250)
      .runLatest(_ -> update());
  }

  @Override
  public @NotNull StatusBarWidget copy() {
    return new LargeFileEncodingWidget(getProject(), myParentScope);
  }

  @Override
  public @NotNull String ID() {
    return WIDGET_ID;
  }

  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);

    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        requestUpdate();
      }

      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        requestUpdate();
      }
    });

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        requestUpdate();
        tryShowPopup();
        return true;
      }
    }.installOn(myComponent.get(), true);

    requestUpdate();
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
    ListPopup popup = action.createPopup(largeFileEditorAccess.getVirtualFile(), largeFileEditorAccess.getEditor());
    RelativePoint pos = JBPopupFactory.getInstance().guessBestPopupLocation(where);
    popup.showInScreenCoordinates(where, pos.getScreenPoint());
  }

  public void requestUpdate() {
    myUpdateQueue.queue(Unit.INSTANCE);
  }

  @RequiresBackgroundThread
  private void update() {
    var largeFileEditorAccess = LargeFileEditorAccessor.getAccess(myStatusBar);

    boolean actionEnabled;
    @NlsSafe String charsetName;
    String toolTipText;
    boolean visible;

    if (largeFileEditorAccess == null) {
      toolTipText = "";
      charsetName = "";
      visible = false;
      actionEnabled = false;
    }
    else {
      actionEnabled = true;
      charsetName = largeFileEditorAccess.getCharsetName();
      toolTipText = EditorBundle.message("large.file.editor.tooltip.file.encoding.is.some", charsetName);
      visible = true;
    }

    SwingUtilities.invokeLater(() -> {
      if (isDisposed()) return;

      myActionEnabled = actionEnabled;
      var myComponent = this.myComponent.get();
      myComponent.setVisible(visible);
      setToolTipText(myComponent, HtmlChunk.text(toolTipText));
      myComponent.setText(charsetName);

      StatusBar statusBar = myStatusBar;
      if (statusBar != null) {
        statusBar.updateWidget(ID());
      }
      else {
        logger.warn("[LargeFileEditorSubsystem] LargeFileEncodingWidget.requestUpdate(): myStatusBar is null!!!)");
      }
    });
  }

  @Override
  public JComponent getComponent() {
    return myComponent.get();
  }

  @Override
  public void dispose() {
    cancel(myScope, null);

    super.dispose();
  }
}
