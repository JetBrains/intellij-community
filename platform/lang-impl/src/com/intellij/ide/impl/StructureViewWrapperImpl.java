/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.structureView.*;
import com.intellij.ide.structureView.impl.StructureViewComposite;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.*;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public class StructureViewWrapperImpl implements StructureViewWrapper, Disposable {
  private final Project myProject;
  private final ToolWindowEx myToolWindow;

  private VirtualFile myFile;

  private StructureView myStructureView;
  private ModuleStructureComponent myModuleStructureComponent;

  private JPanel[] myPanels = new JPanel[0];
  private final MergingUpdateQueue myUpdateQueue;
  private final String myKey = new String("DATA_SELECTOR");

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  private Runnable myPendingSelection;
  private boolean myFirstRun = true;

  public StructureViewWrapperImpl(Project project, ToolWindowEx toolWindow) {
    myProject = project;
    myToolWindow = toolWindow;
    
    myUpdateQueue = new MergingUpdateQueue("StructureView", Registry.intValue("structureView.coalesceTime"), false, myToolWindow.getComponent(), this, myToolWindow.getComponent(), true);
    myUpdateQueue.setRestartTimerOnAdd(true);

    final TimerListener timerListener = new TimerListener() {
      public ModalityState getModalityState() {
        return ModalityState.stateForComponent(myToolWindow.getComponent());
      }

      public void run() {
        checkUpdate();
      }
    };
    ActionManager.getInstance().addTimerListener(500, timerListener);
    Disposer.register(this, new Disposable() {
      @Override
      public void dispose() {
        ActionManager.getInstance().removeTimerListener(timerListener);
      }
    });

    myToolWindow.getComponent().addHierarchyListener(new HierarchyListener() {
      public void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
          scheduleRebuild();
        }
      }
    });
    myToolWindow.getContentManager().addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        if (myStructureView instanceof StructureViewComposite) {
          StructureViewComposite.StructureViewDescriptor[] views = ((StructureViewComposite)myStructureView).getStructureViews();
          for (StructureViewComposite.StructureViewDescriptor view : views) {
            if (view.title.equals(event.getContent().getTabName())) {
              updateHeaderActions(view.structureView);
              break;
            }
          }
        }
      }
    });
    Disposer.register(myToolWindow.getContentManager(), this);
  }

  private void checkUpdate() {
    if (myProject.isDisposed()) return;

    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (SwingUtilities.isDescendingFrom(myToolWindow.getComponent(), owner) || JBPopupFactory.getInstance().isPopupActive()) return;

    final DataContext dataContext = DataManager.getInstance().getDataContext(owner);
    if (dataContext.getData(myKey) == this) return;
    if (PlatformDataKeys.PROJECT.getData(dataContext) != myProject) return;

    final VirtualFile[] files = hasFocus() ? null : PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (!myToolWindow.isVisible()) {
      if (files != null && files.length > 0) {
        myFile = files[0];
      }
      return;
    }

    if (files != null && files.length == 1) {
      setFile(files[0]);
    }
    else if (files != null && files.length > 1) {
      setFile(null);
    } else if (myFirstRun) {
      final FileEditorManagerImpl editorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(myProject);
      final List<Pair<VirtualFile,EditorWindow>> history = editorManager.getSelectionHistory();
      if (! history.isEmpty()) {
        setFile(history.get(0).getFirst());
      }
    }

    myFirstRun = false;
  }

  private boolean hasFocus() {
    final JComponent tw = myToolWindow.getComponent();
    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    while (owner != null) {
      if (owner == tw) return true;
      owner = owner.getParent();
    }
    return false;
  }

  private void setFile(VirtualFile file) {
    boolean forceRebuild = !Comparing.equal(file, myFile);
    if (!forceRebuild && myStructureView != null) {
      StructureViewModel model = myStructureView.getTreeModel();
      StructureViewTreeElement treeElement = model.getRoot();
      Object value = treeElement.getValue();
      if (value == null || value instanceof PsiElement && !((PsiElement)value).isValid()) {
        forceRebuild = true;
      }
    }
    if (forceRebuild) {
      myFile = file;
      scheduleRebuild();
    }
  }


  // -------------------------------------------------------------------------
  // StructureView interface implementation
  // -------------------------------------------------------------------------

  public void dispose() {
    //we don't really need it
    //rebuild();
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

    final ContentManager contentManager = myToolWindow.getContentManager();
    contentManager.removeAllContents(true);
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

    String[] names = new String[] {""};
    JComponent focusedComponent = null;
    if (file != null && file.isValid()) {
      if (file.isDirectory()) {
        if (ProjectRootsUtil.isModuleContentRoot(file, myProject)) {
          Module module = ModuleUtil.findModuleForFile(file, myProject);
          if (module != null) {
            myModuleStructureComponent = new ModuleStructureComponent(module);
            focusedComponent = hadFocus ? IdeFocusTraversalPolicy.getPreferredFocusedComponent(myModuleStructureComponent) : null;
            createSinglePanel(myModuleStructureComponent.getComponent());
            Disposer.register(this, myModuleStructureComponent);
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
            Disposer.register(this, myStructureView);
            updateHeaderActions(myStructureView);

            if (myStructureView instanceof StructureView.Scrollable) {
              ((StructureView.Scrollable)myStructureView).setReferenceSizeWhileInitializing(referenceSize);
            }

            final StructureViewComposite.StructureViewDescriptor[] views;

            if (myStructureView instanceof StructureViewComposite) {
              final StructureViewComposite composite = (StructureViewComposite)myStructureView;
              views = composite.getStructureViews();
              myPanels = new JPanel[views.length];
              names = new String[views.length];
              for (int i = 0; i < myPanels.length; i++) {
                myPanels[i] = createContentPanel(views[i].structureView.getComponent());
                names[i] = views[i].title;
              }
            } else {
              createSinglePanel(myStructureView.getComponent());
            }
            focusedComponent = hadFocus ? IdeFocusTraversalPolicy.getPreferredFocusedComponent(myStructureView.getComponent()) : null;
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
      createSinglePanel(new JLabel(IdeBundle.message("message.nothing.to.show.in.structure.view"), SwingConstants.CENTER));
    }

    for (int i = 0; i < myPanels.length; i++) {
      final Content content = ContentFactory.SERVICE.getInstance().createContent(myPanels[i], names[i], false);
      contentManager.addContent(content);
      if (i == 0 && myStructureView != null) {
        Disposer.register(content, myStructureView);
      }
    }
    if (hadFocus && focusedComponent != null) {
      IdeFocusManager.getInstance(myProject).requestFocus(focusedComponent, true);
    }

    if (myPendingSelection != null) {
      Runnable selection = myPendingSelection;
      myPendingSelection = null;
      selection.run();
    }
  }

  private void updateHeaderActions(StructureView structureView) {
    ActionGroup gearActions = null;
    AnAction[] titleActions = AnAction.EMPTY_ARRAY;
    if (structureView instanceof StructureViewComponent) {
      gearActions = ((StructureViewComponent)structureView).getGearActions();
      titleActions = ((StructureViewComponent)structureView).getTitleActions();
    }
    myToolWindow.setAdditionalGearActions(gearActions);
    myToolWindow.setTitleActions(titleActions);
  }

  private void createSinglePanel(final JComponent component) {
    myPanels = new JPanel[1];
    myPanels[0] = createContentPanel(component);
  }

  private ContentPanel createContentPanel(JComponent component) {
    final ContentPanel panel = new ContentPanel();
    panel.setBackground(UIUtil.getTreeTextBackground());
    panel.add(component, BorderLayout.CENTER);
    return panel;
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
      if (dataId.equals(myKey)) return StructureViewWrapperImpl.this;
      return null;
    }
  }
}
