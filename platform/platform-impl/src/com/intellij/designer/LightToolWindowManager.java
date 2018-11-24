/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.designer;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * @author Alexander Lobas
 */
public abstract class LightToolWindowManager implements Disposable {
  public static final String EDITOR_MODE = "UI_DESIGNER_EDITOR_MODE.";

  private final MergingUpdateQueue myWindowQueue = new MergingUpdateQueue(getComponentName(), 200, true, null, this);
  protected final Project myProject;
  protected final FileEditorManager myFileEditorManager;
  protected volatile ToolWindow myToolWindow;

  private final PropertiesComponent myPropertiesComponent;
  public final String myEditorModeKey;
  private ToggleEditorModeAction myLeftEditorModeAction;
  private ToggleEditorModeAction myRightEditorModeAction;

  private MessageBusConnection myConnection;

  protected LightToolWindowManager(Project project, FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
    myPropertiesComponent = PropertiesComponent.getInstance(myProject);
    myEditorModeKey = EDITOR_MODE + getComponentName() + ".STATE";

    ProjectUtil.runWhenProjectOpened(project, () -> projectOpened());
  }

  protected void projectOpened() {
    initToolWindow();

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized((DumbAwareRunnable)() -> {
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

  @Nullable
  protected abstract DesignerEditorPanelFacade getDesigner(FileEditor editor);

  @Nullable
  public DesignerEditorPanelFacade getActiveDesigner() {
    for (FileEditor editor : myFileEditorManager.getSelectedEditors()) {
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
    DefaultActionGroup group = new DefaultActionGroup("In Editor Mode", true);

    if (myLeftEditorModeAction == null) {
      myLeftEditorModeAction = createToggleAction(ToolWindowAnchor.LEFT);
    }
    group.add(myLeftEditorModeAction);

    if (myRightEditorModeAction == null) {
      myRightEditorModeAction = createToggleAction(ToolWindowAnchor.RIGHT);
    }
    group.add(myRightEditorModeAction);

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
                                                @NotNull String title,
                                                @NotNull Icon icon,
                                                @NotNull JComponent component,
                                                @NotNull JComponent focusedComponent,
                                                int defaultWidth,
                                                @Nullable AnAction[] actions) {
    return new LightToolWindow(content,
                               title,
                               icon,
                               component,
                               focusedComponent,
                               designer.getContentSplitter(),
                               getEditorMode(),
                               this,
                               myProject,
                               myPropertiesComponent,
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

  private void runUpdateContent(Consumer<DesignerEditorPanelFacade> action) {
    for (FileEditor editor : myFileEditorManager.getAllEditors()) {
      DesignerEditorPanelFacade designer = getDesigner(editor);
      if (designer != null) {
        action.accept(designer);
      }
    }
  }

  protected final boolean isEditorMode() {
    return getEditorMode() != null;
  }

  @Nullable
  public final ToolWindowAnchor getEditorMode() {
    String value = myPropertiesComponent.getValue(myEditorModeKey);
    if (value == null) {
      return getAnchor();
    }
    return value.equals("ToolWindow") ? null : ToolWindowAnchor.fromText(value);
  }

  protected final void setEditorMode(@Nullable ToolWindowAnchor newState) {
    ToolWindowAnchor oldState = getEditorMode();
    myPropertiesComponent.setValue(myEditorModeKey, newState == null ? "ToolWindow" : newState.toString());

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

  @NotNull
  protected String getComponentName() {
    return getClass().getName();
  }
}