// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public abstract class LightToolWindowManager implements Disposable {
  public static final String EDITOR_MODE = "UI_DESIGNER_EDITOR_MODE.";

  private final MergingUpdateQueue myWindowQueue = new MergingUpdateQueue(getComponentName(), 200, true, null, this);
  protected final Project myProject;
  protected volatile ToolWindow myToolWindow;

  public final String myEditorModeKey;

  private MessageBusConnection myConnection;

  protected LightToolWindowManager(@NotNull Project project) {
    myProject = project;
    myEditorModeKey = EDITOR_MODE + getComponentName() + ".STATE";

    StartupManager.getInstance(myProject).runAfterOpened(() -> {
      if (getEditorMode() == null) {
        initListeners();
        bindToDesigner(getActiveDesigner());
      }
    });
  }

  private void initListeners() {
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        bindToDesigner(getActiveDesigner());
      }

      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        ApplicationManager.getApplication().invokeLater(() -> bindToDesigner(getActiveDesigner()));
      }

      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        bindToDesigner(getDesigner(event.getNewEditor()));
      }
    });
  }

  private void removeListeners() {
    myConnection.disconnect();
    myConnection = null;
  }

  protected abstract @Nullable DesignerEditorPanelFacade getDesigner(FileEditor editor);

  public @Nullable DesignerEditorPanelFacade getActiveDesigner() {
    if (myProject.isDisposed()) return null;
    for (FileEditor editor : FileEditorManager.getInstance(myProject).getSelectedEditors()) {
      DesignerEditorPanelFacade designer = getDesigner(editor);
      if (designer != null) {
        return designer;
      }
    }

    return null;
  }

  private void bindToDesigner(final DesignerEditorPanelFacade designer) {
    myWindowQueue.cancelAllUpdates();
    myWindowQueue.queue(new Update("update") {
      @Override
      public void run() {
        if (myToolWindow == null) {
          if (designer == null) {
            return;
          }
          initToolWindow();
        }
        updateToolWindow(designer);
      }
    });
  }

  protected abstract void initToolWindow();

  protected abstract void updateToolWindow(@Nullable DesignerEditorPanelFacade designer);

  protected final void initGearActions() {
    ToolWindowEx toolWindow = (ToolWindowEx)myToolWindow;
    toolWindow.setAdditionalGearActions(new DefaultActionGroup(createGearActions()));
  }

  protected abstract ToolWindowAnchor getAnchor();

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // LightToolWindow
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public AnAction createGearActions() {
    DefaultActionGroup group = DefaultActionGroup.createPopupGroup(IdeBundle.messagePointer("popup.title.in.editor.mode"));

    group.add(createToggleAction(ToolWindowAnchor.LEFT));
    group.add(createToggleAction(ToolWindowAnchor.RIGHT));
    group.add(createToggleAction(null));

    return group;
  }

  protected abstract ToggleEditorModeAction createToggleAction(ToolWindowAnchor anchor);

  public final void bind(@NotNull DesignerEditorPanelFacade designer) {
    if (isEditorMode()) {
      myCreateAction.accept(designer);
    }
  }

  public final void dispose(@NotNull DesignerEditorPanelFacade designer) {
    if (isEditorMode()) {
      disposeContent(designer);
    }
  }

  protected final Object getContent(@NotNull DesignerEditorPanelFacade designer) {
    LightToolWindow toolWindow = (LightToolWindow)designer.getClientProperty(getComponentName());
    return toolWindow == null ? null : toolWindow.getContent();
  }

  protected abstract LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer);

  protected final LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer,
                                                @NotNull LightToolWindowContent content,
                                                @Nls(capitalization = Nls.Capitalization.Title) @NotNull String title,
                                                @NotNull Icon icon,
                                                @NotNull JComponent component,
                                                @NotNull JComponent focusedComponent,
                                                int defaultWidth,
                                                @Nullable List<AnAction> actions) {
    return new LightToolWindow(content,
                               title,
                               icon,
                               component,
                               focusedComponent,
                               designer.getContentSplitter(),
                               getEditorMode(),
                               this,
                               myProject,
                               getComponentName(),
                               defaultWidth,
                               actions);
  }

  protected final void disposeContent(DesignerEditorPanelFacade designer) {
    String key = getComponentName();
    LightToolWindow toolWindow = (LightToolWindow)designer.getClientProperty(key);
    designer.putClientProperty(key, null);
    toolWindow.dispose();
  }

  private final Consumer<DesignerEditorPanelFacade> myCreateAction =
    designer -> designer.putClientProperty(getComponentName(), createContent(designer));

  private final Consumer<DesignerEditorPanelFacade> myUpdateAnchorAction =
    designer -> {
      LightToolWindow toolWindow = (LightToolWindow)designer.getClientProperty(getComponentName());
      toolWindow.updateAnchor(getEditorMode());
    };

  private final Consumer<DesignerEditorPanelFacade> myDisposeAction = designer -> disposeContent(designer);

  private void runUpdateContent(Consumer<? super DesignerEditorPanelFacade> action) {
    for (FileEditor editor : FileEditorManager.getInstance(myProject).getAllEditors()) {
      DesignerEditorPanelFacade designer = getDesigner(editor);
      if (designer != null) {
        action.accept(designer);
      }
    }
  }

  protected final boolean isEditorMode() {
    return getEditorMode() != null;
  }

  public final @Nullable ToolWindowAnchor getEditorMode() {
    String value = PropertiesComponent.getInstance(myProject).getValue(myEditorModeKey);
    if (value == null) {
      return getAnchor();
    }
    return value.equals("ToolWindow") ? null : ToolWindowAnchor.fromText(value);
  }

  protected final void setEditorMode(@Nullable ToolWindowAnchor newState) {
    ToolWindowAnchor oldState = getEditorMode();
    PropertiesComponent.getInstance(myProject).setValue(myEditorModeKey, newState == null ? "ToolWindow" : newState.toString());

    if (oldState != null && newState != null) {
      runUpdateContent(myUpdateAnchorAction);
    }
    else if (newState != null) {
      removeListeners();
      updateToolWindow(null);
      runUpdateContent(myCreateAction);
    }
    else {
      runUpdateContent(myDisposeAction);
      initListeners();
      bindToDesigner(getActiveDesigner());
    }
  }

  final ToolWindow getToolWindow() {
    return myToolWindow;
  }

  @Override
  public void dispose() {
    myToolWindow = null;
  }

  protected @NotNull String getComponentName() {
    return getClass().getName();
  }
}