/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

public class EditorTracker extends AbstractProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.EditorTracker");

  private final WindowManager myWindowManager;
  private final EditorFactory myEditorFactory;

  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"}) 
  private final ToolWindowManager myToolwindowManager;

  private final Map<Window, List<Editor>> myWindowToEditorsMap = new HashMap<Window, List<Editor>>();
  private final Map<Window, WindowFocusListener> myWindowToWindowFocusListenerMap = new HashMap<Window, WindowFocusListener>();
  private final Map<Editor, Window> myEditorToWindowMap = new HashMap<Editor, Window>();
  private List<Editor> myActiveEditors = Collections.emptyList();

  private MyEditorFactoryListener myEditorFactoryListener;
  private final EventDispatcher<EditorTrackerListener> myDispatcher = EventDispatcher.create(EditorTrackerListener.class);

  private IdeFrameImpl myIdeFrame;
  private Window myActiveWindow = null;

  //todo:
  //toolwindow manager is unfortunately needed since
  //it actually initializes frame in WindowManager
  public EditorTracker(Project project, final WindowManager windowManager, final EditorFactory editorFactory, ToolWindowManager toolwindowManager) {
    super(project);
    myWindowManager = windowManager;
    myEditorFactory = editorFactory;
    myToolwindowManager = toolwindowManager;
  }

  public void projectOpened() {
    myIdeFrame = ((WindowManagerEx)myWindowManager).getFrame(myProject);
    myProject.getMessageBus().connect(myProject).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerAdapter() {
      public void selectionChanged(FileEditorManagerEvent event) {
        if (myIdeFrame.getFocusOwner() == null) return;
        setActiveWindow(myIdeFrame);
      }
    });

    myEditorFactoryListener = new MyEditorFactoryListener();
    myEditorFactory.addEditorFactoryListener(myEditorFactoryListener);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        myEditorFactoryListener.dispose(null);
        myEditorFactory.removeEditorFactoryListener(myEditorFactoryListener);
      }
    });
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "EditorTracker";
  }

  private void editorFocused(Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Window window = myEditorToWindowMap.get(editor);
    if (window == null) return;

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

  private void registerEditor(Editor editor) {
    unregisterEditor(editor);

    final Window window = windowByEditor(editor);
    if (window == null) return;

    myEditorToWindowMap.put(editor, window);
    List<Editor> list = myWindowToEditorsMap.get(window);
    if (list == null) {
      list = new ArrayList<Editor>();
      myWindowToEditorsMap.put(window, list);

      if (!(window instanceof IdeFrameImpl)) {
        WindowFocusListener listener =  new WindowFocusListener() {
          public void windowGainedFocus(WindowEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("windowGainedFocus:" + window);
            }

            setActiveWindow(window);
          }

          public void windowLostFocus(WindowEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("windowLostFocus:" + window);
            }

            setActiveWindow(null);
          }
        };
        myWindowToWindowFocusListenerMap.put(window, listener);
        window.addWindowFocusListener(listener);
      }
    }
    list.add(editor);

    if (myActiveWindow == window) {
      setActiveWindow(window); // to fire event
    }
  }

  private void unregisterEditor(Editor editor) {
    Window oldWindow = myEditorToWindowMap.get(editor);
    if (oldWindow != null) {
      myEditorToWindowMap.remove(editor);
      List<Editor> editorsList = myWindowToEditorsMap.get(oldWindow);
      boolean removed = editorsList.remove(editor);
      LOG.assertTrue(removed);
      
      if (editorsList.isEmpty()) {
        myWindowToEditorsMap.remove(oldWindow);
        final WindowFocusListener listener = myWindowToWindowFocusListenerMap.remove(oldWindow);
        if (listener != null) oldWindow.removeWindowFocusListener(listener);
      }
    }
  }

  private Window windowByEditor(Editor editor) {
    Window window = SwingUtilities.windowForComponent(editor.getComponent());
    if (window instanceof IdeFrameImpl) {
      if (window != myIdeFrame) return null;
    }
    return window;
  }

  @NotNull
  public List<Editor> getActiveEditors() {
    return myActiveEditors;
  }

  private void setActiveWindow(Window window) {
    myActiveWindow = window;
    List<Editor> editors = editorsByWindow(myActiveWindow);
    setActiveEditors(editors);
  }

  @NotNull
  private List<Editor> editorsByWindow(Window window) {
    List<Editor> list = myWindowToEditorsMap.get(window);
    if (list == null) return Collections.emptyList();
    List<Editor> filtered = new SmartList<Editor>();
    for (Editor editor : list) {
      if (editor.getContentComponent().isShowing()) {
        filtered.add(editor);
      }
    }
    return filtered;
  }

  private void setActiveEditors(@NotNull List<Editor> editors) {
    myActiveEditors = editors;

    if (LOG.isDebugEnabled()) {
      LOG.debug("active editors changed:");
      for (Editor editor : editors) {
        PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
        LOG.debug("    " + psiFile);
      }
    }

    myDispatcher.getMulticaster().activeEditorsChanged(editors);
  }

  public void addEditorTrackerListener(EditorTrackerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeEditorTrackerListener(EditorTrackerListener listener) {
    myDispatcher.removeListener(listener);
  }

  private class MyEditorFactoryListener implements EditorFactoryListener {
    private final Map<Editor, Runnable> myExecuteOnEditorRelease = new HashMap<Editor, Runnable>();

    public void editorCreated(EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (psiFile == null) return;

      final JComponent component = editor.getComponent();
      final JComponent contentComponent = editor.getContentComponent();

      final HierarchyListener hierarchyListener = new HierarchyListener() {
        public void hierarchyChanged(HierarchyEvent e) {
          registerEditor(editor);
        }
      };
      component.addHierarchyListener(hierarchyListener);

      final FocusListener focusListener = new FocusListener() {
        public void focusGained(FocusEvent e) {
          editorFocused(editor);
        }

        public void focusLost(FocusEvent e) {
        }
      };
      contentComponent.addFocusListener(focusListener);

      myExecuteOnEditorRelease.put(event.getEditor(), new Runnable() {
        public void run() {
          component.removeHierarchyListener(hierarchyListener);
          contentComponent.removeFocusListener(focusListener);
        }
      });
    }

    public void editorReleased(EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      if (editor.getProject() != null && editor.getProject() != myProject) return;
      unregisterEditor(editor);
      dispose(editor);
    }

    private void dispose(Editor editor) {
      if (editor == null) {
        for (Runnable r : myExecuteOnEditorRelease.values()) {
          r.run();
        }
        myExecuteOnEditorRelease.clear();
      }
      else {
        final Runnable runnable = myExecuteOnEditorRelease.get(editor);
        if (runnable != null) {
          runnable.run();
          myExecuteOnEditorRelease.remove(editor);
        }
      }
    }
  }
}
