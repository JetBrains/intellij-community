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

package com.intellij.ide.palette.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.palette.PaletteDragEventListener;
import com.intellij.ide.palette.PaletteItem;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

/**
 * @author yole
 */
public class PaletteManager implements ProjectComponent {
  private final Project myProject;
  private final FileEditorManager myFileEditorManager;
  private PaletteWindow myPaletteWindow;
  private ToolWindow myPaletteToolWindow;
  private final List<KeyListener> myKeyListeners = ContainerUtil.createEmptyCOWList();
  private final List<PaletteDragEventListener> myDragEventListeners = ContainerUtil.createEmptyCOWList();
  private final List<ListSelectionListener> mySelectionListeners = ContainerUtil.createEmptyCOWList();

  public PaletteManager(Project project, FileEditorManager fileEditorManager) {
    myProject = project;
    myFileEditorManager = fileEditorManager;
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myPaletteWindow = new PaletteWindow(myProject);
        myPaletteToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(IdeBundle.message("toolwindow.palette"),
                                                                                          myPaletteWindow,
                                                                                          ToolWindowAnchor.RIGHT,
                                                                                          myProject,
                                                                                          true);
        myPaletteToolWindow.setIcon(IconLoader.getIcon("/general/toolWindowPalette.png"));
        myPaletteToolWindow.setAvailable(false, null);
        final MyFileEditorManagerListener myListener = new MyFileEditorManagerListener();
        myFileEditorManager.addFileEditorManagerListener(myListener, myProject);
      }
    });
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "PaletteManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public static PaletteManager getInstance(final Project project) {
    return project.getComponent(PaletteManager.class);
  }

  public void clearActiveItem() {
    if (myPaletteWindow != null) {
      myPaletteWindow.clearActiveItem();
    }
  }

  @Nullable
  public PaletteItem getActiveItem() {
    if (myPaletteWindow != null) {
      return myPaletteWindow.getActiveItem();
    }
    return null;
  }

  @Nullable
  public <T extends PaletteItem> T getActiveItem(Class<T> cls) {
    PaletteItem item = getActiveItem();
    if (item != null && item.getClass().isInstance(item)) {
      //noinspection unchecked
      return (T) item;
    }
    return null;
  }

  public void addKeyListener(KeyListener l) {
    myKeyListeners.add(l);
  }

  public void removeKeyListener(KeyListener l) {
    myKeyListeners.remove(l);
  }

  public void addDragEventListener(PaletteDragEventListener l) {
    myDragEventListeners.add(l);
  }

  public void removeDragEventListener(PaletteDragEventListener l) {
    myDragEventListeners.remove(l);
  }

  public void addSelectionListener(ListSelectionListener l) {
    mySelectionListeners.add(l);
  }

  public void removeSelectionListener(ListSelectionListener l) {
    mySelectionListeners.remove(l);
  }

  private MergingUpdateQueue myQueue = new MergingUpdateQueue("palette", 200, true, null);

  private void processFileEditorChange(@Nullable final VirtualFile selectedFile) {

    myQueue.cancelAllUpdates();
    myQueue.queue(new Update("update") {
      public void run() {
        if (myPaletteWindow == null) return;
        myPaletteWindow.refreshPaletteIfChanged(selectedFile);
        if (myPaletteWindow.getActiveGroupCount() == 0) {
          myPaletteToolWindow.setAvailable(false, null);
        }
        else {
          myPaletteToolWindow.setAvailable(true, null);
          myPaletteToolWindow.show(null);
        }
      }
    });
  }

  void notifyKeyEvent(final KeyEvent e) {
    for(KeyListener l: myKeyListeners) {
      if (e.getID() == KeyEvent.KEY_PRESSED) {
        l.keyPressed(e);
      }
      else if (e.getID() == KeyEvent.KEY_RELEASED) {
        l.keyReleased(e);
      }
      else if (e.getID() == KeyEvent.KEY_TYPED) {
        l.keyTyped(e);
      }
    }
  }

  void notifyDropActionChanged(int gestureModifiers) {
    for(PaletteDragEventListener l: myDragEventListeners) {
      l.dropActionChanged(gestureModifiers);
    }
  }

  void notifySelectionChanged(final ListSelectionEvent event) {
    for(ListSelectionListener l: mySelectionListeners) {
      l.valueChanged(event);
    }
  }

  private class MyFileEditorManagerListener implements FileEditorManagerListener {
    public void fileOpened(FileEditorManager source, VirtualFile file) {
      processFileEditorChange(file);
    }

    public void fileClosed(FileEditorManager source, VirtualFile file) {
      processFileEditorChange(null);
    }

    public void selectionChanged(FileEditorManagerEvent event) {
      processFileEditorChange(event.getNewFile());
    }
  }
}
