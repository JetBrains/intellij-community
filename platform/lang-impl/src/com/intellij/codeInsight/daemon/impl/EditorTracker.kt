// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;

public class EditorTracker implements Disposable {
  private static final Logger LOG = Logger.getInstance(EditorTracker.class);

  protected final Project project;

  private final Map<Window, List<Editor>> myWindowToEditorsMap = new HashMap<>();
  private final Map<Window, WindowAdapter> myWindowToWindowFocusListenerMap = new HashMap<>();
  private final Map<Editor, Window> myEditorToWindowMap = new HashMap<>();
  private List<? extends Editor> myActiveEditors = Collections.emptyList(); // accessed in EDT only

  private Window myActiveWindow;
  private final Map<Editor, Runnable> myExecuteOnEditorRelease = new HashMap<>();

  public EditorTracker(@NotNull Project project) {
    this.project = project;
  }

  public static EditorTracker getInstance(@NotNull Project project) {
    return project.getService(EditorTracker.class);
  }

  static final class MyAppLevelFileEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      Project project = event.getManager().getProject();
      JFrame frame = WindowManager.getInstance().getFrame(project);
      if (frame != null && frame.getFocusOwner() != null) {
        getInstance(project).setActiveWindow(frame);
      }
    }
  }

  static final class MyAppLevelEditorFactoryListener implements EditorFactoryListener {
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
      Project project = event.getEditor().getProject();
      if (project != null && !project.isDisposed()) {
        getInstance(project).editorCreated(event, project);
      }
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
      Project project = event.getEditor().getProject();
      if (project != null && !project.isDisposed()) {
        getInstance(project).editorReleased(event, project);
      }
    }
  }

  private void registerEditor(@NotNull Editor editor, @NotNull Project project) {
    unregisterEditor(editor);

    Window window = windowByEditor(editor, project);
    if (window == null) {
      return;
    }

    myEditorToWindowMap.put(editor, window);
    List<Editor> list = myWindowToEditorsMap.get(window);
    if (list == null) {
      list = new ArrayList<>();
      myWindowToEditorsMap.put(window, list);

      if (!(window instanceof IdeFrameImpl)) {
        WindowAdapter listener =  new WindowAdapter() {
          @Override
          public void windowGainedFocus(WindowEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("windowGainedFocus:" + window);
            }

            setActiveWindow(window);
          }

          @Override
          public void windowLostFocus(WindowEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("windowLostFocus:" + window);
            }

            setActiveWindow(null);
          }

          @Override
          public void windowClosed(WindowEvent event) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("windowClosed:" + window);
            }

            setActiveWindow(null);
          }
        };
        myWindowToWindowFocusListenerMap.put(window, listener);
        window.addWindowFocusListener(listener);
        window.addWindowListener(listener);
        if (window.isFocused()) {  // windowGainedFocus is missed; activate by force
          setActiveWindow(window);
        }
      }
    }
    list.add(editor);

    if (myActiveWindow == window) {
      setActiveWindow(window); // to fire event
    }
  }

  private void unregisterEditor(@NotNull Editor editor) {
    Window oldWindow = myEditorToWindowMap.get(editor);
    if (oldWindow != null) {
      myEditorToWindowMap.remove(editor);
      List<Editor> editorsList = myWindowToEditorsMap.get(oldWindow);
      boolean removed = editorsList.remove(editor);
      LOG.assertTrue(removed);
      if (oldWindow == myActiveWindow) {
        updateActiveEditors(myActiveWindow);
      }

      if (editorsList.isEmpty()) {
        myWindowToEditorsMap.remove(oldWindow);
        WindowAdapter listener = myWindowToWindowFocusListenerMap.remove(oldWindow);
        if (listener != null) {
          oldWindow.removeWindowFocusListener(listener);
          oldWindow.removeWindowListener(listener);
        }
      }
    }
  }

  private static @Nullable Window windowByEditor(@NotNull Editor editor, @NotNull Project project) {
    Window window = SwingUtilities.windowForComponent(editor.getComponent());
    ProjectFrameHelper frameHelper = ProjectFrameHelper.getFrameHelper(window);
    return (frameHelper != null && frameHelper.getProject() != project) ? null : window;
  }

  public @NotNull List<? extends Editor> getActiveEditors() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myActiveEditors;
  }

  private void setActiveWindow(@Nullable Window window) {
    myActiveWindow = window;
    updateActiveEditors(window);
  }

  private void updateActiveEditors(@Nullable Window window) {
    List<Editor> list = window == null ? null : myWindowToEditorsMap.get(window);
    if (list == null || list.isEmpty()) {
      setActiveEditors(Collections.emptyList());
    }
    else {
      List<Editor> editors = new SmartList<>();
      for (Editor editor : list) {
        if (editor.getContentComponent().isShowing() && !editor.isDisposed()) {
          editors.add(editor);
        }
      }
      setActiveEditors(editors);
    }
  }

  public void setActiveEditors(@NotNull List<? extends Editor> editors) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (editors.equals(myActiveEditors)) {
      return;
    }

    myActiveEditors = editors;

    if (LOG.isDebugEnabled()) {
      LOG.debug("active editors changed:");
      for (Editor editor : editors) {
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        LOG.debug("    " + psiFile);
      }
    }

    project.getMessageBus().syncPublisher(EditorTrackerListener.TOPIC).activeEditorsChanged(editors);
  }

  private void editorCreated(@NotNull EditorFactoryEvent event, @NotNull Project project) {
    Editor editor = event.getEditor();
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile != null) {
      createEditorImpl(editor, project);
    }
  }

  protected void createEditorImpl(@NotNull Editor editor, @NotNull Project project) {
    JComponent component = editor.getComponent();
    JComponent contentComponent = editor.getContentComponent();

    PropertyChangeListener propertyChangeListener = evt -> {
      if (evt.getOldValue() == null && evt.getNewValue() != null) {
        registerEditor(editor, project);
      }
    };
    component.addPropertyChangeListener("ancestor", propertyChangeListener);

    FocusListener focusListener = new FocusListener() {
      @Override
      public void focusGained(@NotNull FocusEvent e) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        Window window = myEditorToWindowMap.get(editor);
        if (window == null) {
          return;
        }

        List<Editor> list = myWindowToEditorsMap.get(window);
        int index = list.indexOf(editor);
        LOG.assertTrue(index >= 0);
        if (list.isEmpty()) return;

        for (int i = index - 1; i >= 0; i--) {
          list.set(i + 1, list.get(i));
        }
        list.set(0, editor);

        setActiveWindow(window);
      }

      @Override
      public void focusLost(@NotNull FocusEvent e) {
      }
    };
    contentComponent.addFocusListener(focusListener);

    myExecuteOnEditorRelease.put(editor, () -> {
      component.removePropertyChangeListener("ancestor", propertyChangeListener);
      contentComponent.removeFocusListener(focusListener);
    });
  }

  private void editorReleased(@NotNull EditorFactoryEvent event, @NotNull Project project) {
    editorReleasedImpl(event.getEditor(), project);
  }

  protected void editorReleasedImpl(@NotNull Editor editor, @NotNull Project project) {
    unregisterEditor(editor);
    executeOnRelease(editor);
  }

  @Override
  public void dispose() {
    executeOnRelease(null);
  }

  private void executeOnRelease(@Nullable Editor editor) {
    if (editor == null) {
      for (Runnable r : myExecuteOnEditorRelease.values()) {
        r.run();
      }
      myExecuteOnEditorRelease.clear();
    }
    else {
      Runnable runnable = myExecuteOnEditorRelease.get(editor);
      if (runnable != null) {
        runnable.run();
        myExecuteOnEditorRelease.remove(editor);
      }
    }
  }
}
