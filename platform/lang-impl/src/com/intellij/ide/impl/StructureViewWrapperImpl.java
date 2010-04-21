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

package com.intellij.ide.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;

/**
 * @author Eugene Belyaev
 */
public class StructureViewWrapperImpl implements StructureViewWrapper, Disposable {
  private final Project myProject;

  private VirtualFile myFile;

  private StructureView myStructureView;
  private ModuleStructureComponent myModuleStructureComponent;

  private final JPanel myPanel;
  private final MergingUpdateQueue myUpdateQueue;
  private final String myKey = new String("DATA_SELECTOR");

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  private Runnable myPendingSelection;

  public StructureViewWrapperImpl(Project project) {
    myProject = project;
    myPanel = new ContentPanel();
    myPanel.setBackground(UIUtil.getTreeTextBackground());

    myUpdateQueue = new MergingUpdateQueue("StructureView", Registry.intValue("structureView.coalesceTime"), false, myPanel, this, myPanel, true);
    myUpdateQueue.setRestartTimerOnAdd(true);

    ActionManager.getInstance().addTimerListener(500, new TimerListener() {
      public ModalityState getModalityState() {
        return ModalityState.stateForComponent(myPanel);
      }

      public void run() {
        checkUpdate();
      }
    });

    getComponent().addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
          scheduleRebuild();
        }
      }
    });
  }

  private void checkUpdate() {
    if (myProject.isDisposed()) return;

    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (SwingUtilities.isDescendingFrom(myPanel, owner) || JBPopupFactory.getInstance().isPopupActive()) return;

    final DataContext dataContext = DataManager.getInstance().getDataContext(owner);
    if (dataContext.getData(myKey) == this) return;
    if (PlatformDataKeys.PROJECT.getData(dataContext) != myProject) return;

    final VirtualFile[] files = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (files != null && files.length == 1) {
      setFile(files[0]);
    }
    else if (files != null && files.length > 1) {
      setFile(null);
    }
  }

  private void setFile(VirtualFile file) {
    if (!Comparing.equal(file, myFile)) {
      myFile = file;
      scheduleRebuild();
    }
  }


  // -------------------------------------------------------------------------
  // StructureView interface implementation
  // -------------------------------------------------------------------------

  public JComponent getComponent() {
    return myPanel;
  }

  public void dispose() {
    rebuild();
  }

  public boolean selectCurrentElement(final FileEditor fileEditor, final VirtualFile file, final boolean requestFocus) {
    //todo [kirillk]
    // this is dirty hack since some bright minds decided to used different TreeUi every time, so selection may be followed
    // by rebuild on completely different instance of TreeUi

    Runnable runnable = new Runnable() {
      public void run() {
        if (myStructureView != null) {
          if (!Comparing.equal(myStructureView.getFileEditor(), fileEditor)) {
            myFile = file;
            rebuild();
          }
          myStructureView.navigateToSelectedElement(requestFocus);
        }
      }
    };

    if (isStructureViewShowing()) {
      if (myUpdateQueue.isEmpty()) {
        runnable.run();
      } else {
        myPendingSelection = runnable;
      }
    } else {
      myPendingSelection = runnable;
    }

    return true;
  }

  private void scheduleRebuild() {
    myUpdateQueue.queue(new Update("rebuild") {
      public void run() {
        if (myProject.isDisposed()) return;
        rebuild();
      }
    });
  }

  public void rebuild() {
    if (myProject.isDisposed()) return;
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    boolean hadFocus = ToolWindowId.STRUCTURE_VIEW.equals(ToolWindowManager.getInstance(myProject).getActiveToolWindowId());

    Dimension referenceSize = null;
    if (myStructureView != null) {
      if (myStructureView instanceof StructureView.Scrollable) {
        referenceSize = ((StructureView.Scrollable)myStructureView).getCurrentSize();
      }

      myStructureView.storeState();
      Disposer.dispose(myStructureView);
      myStructureView = null;
    }

    if (myModuleStructureComponent != null) {
      Disposer.dispose(myModuleStructureComponent);
      myModuleStructureComponent = null;
    }

    myPanel.removeAll();

    if (!isStructureViewShowing()) {
      return;
    }

    VirtualFile file = myFile;
    if (file == null) {
      final VirtualFile[] selectedFiles = FileEditorManager.getInstance(myProject).getSelectedFiles();
      if (selectedFiles.length > 0) {
        file = selectedFiles[0];
      }
    }

    if (file != null && file.isValid()) {
      if (file.isDirectory()) {
        if (ProjectRootsUtil.isModuleContentRoot(file, myProject)) {
          Module module = ModuleUtil.findModuleForFile(file, myProject);
          if (module != null) {
            myModuleStructureComponent = new ModuleStructureComponent(module);
            myPanel.add(myModuleStructureComponent, BorderLayout.CENTER);
            if (hadFocus) {
              JComponent focusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myModuleStructureComponent);
              if (focusedComponent != null) {
                IdeFocusManager.getInstance(myProject).requestFocus(focusedComponent, true);
              }
            }
          }
        }
      }
      else {
        FileEditor editor = FileEditorManager.getInstance(myProject).getSelectedEditor(file);
        boolean needDisposeEditor = false;
        if (editor == null) {
          editor = createTempFileEditor(file);
          needDisposeEditor = true;
        }
        if (editor != null && editor.isValid()) {
          final StructureViewBuilder structureViewBuilder = editor.getStructureViewBuilder();
          if (structureViewBuilder != null) {
            myStructureView = structureViewBuilder.createStructureView(editor, myProject);
            if (myStructureView instanceof StructureView.Scrollable) {
              ((StructureView.Scrollable)myStructureView).setReferenceSizeWhileInitializing(referenceSize);
            }
            myPanel.add(myStructureView.getComponent(), BorderLayout.CENTER);
            if (hadFocus) {
              JComponent focusedComponent = IdeFocusTraversalPolicy.getPreferredFocusedComponent(myStructureView.getComponent());
              if (focusedComponent != null) {
                IdeFocusManager.getInstance(myProject).requestFocus(focusedComponent, true);
              }
            }
            myStructureView.restoreState();
            myStructureView.centerSelectedRow();
          }
        }
        if (needDisposeEditor && editor != null) {
          Disposer.dispose(editor);
        }
      }
    }

    if (myModuleStructureComponent == null && myStructureView == null) {
      myPanel.add(new JLabel(IdeBundle.message("message.nothing.to.show.in.structure.view"), SwingConstants.CENTER), BorderLayout.CENTER);
    }

    myPanel.validate();
    myPanel.repaint();

    if (myPendingSelection != null) {
      Runnable selection = myPendingSelection;
      myPendingSelection = null;
      selection.run();
    }
  }

  @Nullable
  private FileEditor createTempFileEditor(VirtualFile file) {
    FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
    final FileEditorProvider[] providers = editorProviderManager.getProviders(myProject, file);
    for (FileEditorProvider provider : providers) {
      return provider.createEditor(myProject, file);
    }
    return null;
  }


  protected boolean isStructureViewShowing() {
    ToolWindowManager windowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = windowManager.getToolWindow(ToolWindowId.STRUCTURE_VIEW);
    // it means that window is registered
    return toolWindow != null && toolWindow.isVisible();
  }

  private class ContentPanel extends JPanel implements DataProvider {
    public ContentPanel() {
      super(new BorderLayout());
    }

    public Object getData(@NonNls String dataId) {
      if (dataId == myKey) return StructureViewWrapperImpl.this;
      return null;
    }
  }
}
