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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.DataManager;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.ui.*;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.AnActionButtonUpdater;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 11, 2004
 */
public class LibraryRootsComponent implements Disposable, LibraryEditorComponent {
  static final UrlComparator ourUrlComparator = new UrlComparator();

  private JPanel myPanel;
  private JPanel myTreePanel;
  private MultiLineLabel myPropertiesLabel;
  private JPanel myPropertiesPanel;
  private LibraryPropertiesEditor myPropertiesEditor;
  private Tree myTree;
  private LibraryTableTreeBuilder myTreeBuilder;

  private final Collection<Runnable> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  @Nullable private final Project myProject;

  private final Computable<LibraryEditor> myLibraryEditorComputable;
  private LibraryRootsComponentDescriptor myDescriptor;
  private Module myContextModule;

  public LibraryRootsComponent(@Nullable Project project, @NotNull LibraryEditor libraryEditor) {
    this(project, new Computable.PredefinedValueComputable<LibraryEditor>(libraryEditor));
  }

  public LibraryRootsComponent(@Nullable Project project, @NotNull Computable<LibraryEditor> libraryEditorComputable) {
    myProject = project;
    myLibraryEditorComputable = libraryEditorComputable;
    final LibraryEditor editor = getLibraryEditor();
    final LibraryType type = editor.getType();
    if (type != null) {
      myDescriptor = type.createLibraryRootsComponentDescriptor();
      //noinspection unchecked
      myPropertiesEditor = type.createPropertiesEditor(this);
      if (myPropertiesEditor != null) {
        myPropertiesPanel.add(myPropertiesEditor.createComponent(), BorderLayout.CENTER);
      }
    }
    if (myDescriptor == null) {
      myDescriptor = new DefaultLibraryRootsComponentDescriptor();
    }
    init(new LibraryTreeStructure(this, myDescriptor));
    updatePropertiesLabel();
  }

  @NotNull
  @Override
  public LibraryProperties getProperties() {
    return getLibraryEditor().getProperties();
  }

  @Override
  public boolean isNewLibrary() {
    return getLibraryEditor() instanceof NewLibraryEditor;
  }

  public void updatePropertiesLabel() {
    StringBuilder text = new StringBuilder();
    final LibraryType<?> type = getLibraryEditor().getType();
    final Set<LibraryKind> excluded =
      type != null ? Collections.<LibraryKind>singleton(type.getKind()) : Collections.<LibraryKind>emptySet();
    for (String description : LibraryPresentationManager.getInstance().getDescriptions(getLibraryEditor().getFiles(OrderRootType.CLASSES),
                                                                                       excluded)) {
      if (text.length() > 0) {
        text.append("\n");
      }
      text.append(description);
    }
    myPropertiesLabel.setText(text.toString());
  }

  private void init(AbstractTreeStructure treeStructure) {
    myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    new LibraryRootsTreeSpeedSearch(myTree);
    myTree.setCellRenderer(new LibraryTreeRenderer());
    myTreeBuilder = new LibraryTableTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure);
    myTreePanel.setLayout(new BorderLayout());

    ToolbarDecorator toolbarDecorator = ToolbarDecorator.createDecorator(myTree).disableUpDownActions().disableAddAction()
      .setRemoveActionName(ProjectBundle.message("library.detach.action"))
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final Object[] selectedElements = getSelectedElements();
          if (selectedElements.length == 0) {
            return;
          }
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              for (Object selectedElement : selectedElements) {
                if (selectedElement instanceof ItemElement) {
                  final ItemElement itemElement = (ItemElement)selectedElement;
                  getLibraryEditor().removeRoot(itemElement.getUrl(), itemElement.getRootType());
                }
                else if (selectedElement instanceof OrderRootTypeElement) {
                  final OrderRootType rootType = ((OrderRootTypeElement)selectedElement).getOrderRootType();
                  final String[] urls = getLibraryEditor().getUrls(rootType);
                  for (String url : urls) {
                    getLibraryEditor().removeRoot(url, rootType);
                  }
                }
              }
            }
          });
          librariesChanged(true);
        }
      });

    toolbarDecorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        final AnAction[] children = getActions();
        if (children.length == 0) return;
        final DefaultActionGroup actions = new DefaultActionGroup(children);
        JBPopupFactory.getInstance().createActionGroupPopup(null, actions,
                                                            DataManager.getInstance().getDataContext(button.getContextComponent()),
                                                            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
          .show(button.getPreferredPopupPoint());
      }

      private AnAction[] getActions() {
        List<AnAction> actions = new ArrayList<AnAction>();
        actions.add(new AttachFilesAction(myDescriptor.getAttachFilesActionName()));
        for (AttachRootButtonDescriptor descriptor : myDescriptor.createAttachButtons()) {
          actions.add(new AttachItemAction(descriptor, descriptor.getButtonText()));
        }
        return actions.toArray(new AnAction[actions.size()]);
      }
    });



    myTreePanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER);
    ToolbarDecorator.findRemoveButton(myTreePanel).addCustomUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        final Object[] selectedElements = getSelectedElements();
        for (Object element : selectedElements) {
          if (element instanceof ItemElement) {
            return true;
          }
          if (element instanceof OrderRootTypeElement && getLibraryEditor().getUrls(((OrderRootTypeElement)element).getOrderRootType()).length > 0) {
            return true;
          }
        }
        return false;
      }
    });

    Disposer.register(this, myTreeBuilder);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  @Nullable
  public Project getProject() {
    return myProject;
  }

  public void setContextModule(Module module) {
    myContextModule = module;
  }

  @Override
  @Nullable
  public VirtualFile getExistingRootDirectory() {
    for (OrderRootType orderRootType : OrderRootType.getAllPersistentTypes()) {
      final VirtualFile[] existingRoots = getLibraryEditor().getFiles(orderRootType);
      if (existingRoots.length > 0) {
        VirtualFile existingRoot = existingRoots[0];
        if (existingRoot.getFileSystem() instanceof JarFileSystem) {
          existingRoot = JarFileSystem.getInstance().getVirtualFileForJar(existingRoot);
        }
        if (existingRoot != null) {
          if (existingRoot.isDirectory()) {
            return existingRoot;
          }
          else {
            return existingRoot.getParent();
          }
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public VirtualFile getBaseDirectory() {
    if (myProject != null) {
      //todo[nik] perhaps we shouldn't select project base dir if global library is edited
      return myProject.getBaseDir();
    }
    return null;
  }

  @Override
  public LibraryEditor getLibraryEditor() {
    return myLibraryEditorComputable.compute();
  }

  public boolean hasChanges() {
    if (myPropertiesEditor != null && myPropertiesEditor.isModified()) {
      return true;
    }
    return getLibraryEditor().hasChanges();
  }

  private Object[] getSelectedElements() {
    if (myTreeBuilder == null || myTreeBuilder.isDisposed()) return ArrayUtil.EMPTY_OBJECT_ARRAY;
    final TreePath[] selectionPaths = myTreeBuilder.getTree().getSelectionPaths();
    if (selectionPaths == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    List<Object> elements = new ArrayList<Object>();
    for (TreePath selectionPath : selectionPaths) {
      final Object pathElement = getPathElement(selectionPath);
      if (pathElement != null) {
        elements.add(pathElement);
      }
    }
    return ArrayUtil.toObjectArray(elements);
  }

  @Nullable
  private static Object getPathElement(final TreePath selectionPath) {
    if (selectionPath == null) {
      return null;
    }
    final DefaultMutableTreeNode lastPathComponent = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    if (lastPathComponent == null) {
      return null;
    }
    final Object userObject = lastPathComponent.getUserObject();
    if (!(userObject instanceof NodeDescriptor)) {
      return null;
    }
    final Object element = ((NodeDescriptor)userObject).getElement();
    if (!(element instanceof LibraryTableTreeContentElement)) {
      return null;
    }
    return element;
  }

  @Override
  public void renameLibrary(String newName) {
    final LibraryEditor libraryEditor = getLibraryEditor();
    libraryEditor.setName(newName);
    librariesChanged(false);
  }

  @Override
  public void dispose() {
    if (myPropertiesEditor != null) {
      myPropertiesEditor.disposeUIResources();
    }
    myTreeBuilder = null;
  }

  public void resetProperties() {
    if (myPropertiesEditor != null) {
      myPropertiesEditor.reset();
    }
  }

  public void applyProperties() {
    if (myPropertiesEditor != null && myPropertiesEditor.isModified()) {
      myPropertiesEditor.apply();
    }
  }

  @Override
  public void updateRootsTree() {
    if (myTreeBuilder != null) {
      myTreeBuilder.queueUpdate();
    }
  }

  private class AttachFilesAction extends AttachItemActionBase {
    public AttachFilesAction(String title) {
      super(title);
    }

    @Override
    protected List<OrderRoot> selectRoots(@Nullable VirtualFile initialSelection) {
      final String name = getLibraryEditor().getName();
      final FileChooserDescriptor chooserDescriptor = myDescriptor.createAttachFilesChooserDescriptor(name);
      if (myContextModule != null) {
        chooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myContextModule);
      }
      final VirtualFile[] files = FileChooser.chooseFiles(chooserDescriptor, myPanel, myProject, initialSelection);
      if (files.length == 0) return Collections.emptyList();

      return RootDetectionUtil.detectRoots(Arrays.asList(files), myPanel, myProject, myDescriptor);
    }
  }

  public abstract class AttachItemActionBase extends DumbAwareAction {
    private VirtualFile myLastChosen = null;

    protected AttachItemActionBase(String text) {
      super(text);
    }

    @Nullable
    protected VirtualFile getFileToSelect() {
      if (myLastChosen != null) {
        return myLastChosen;
      }

      final VirtualFile directory = getExistingRootDirectory();
      if (directory != null) {
        return directory;
      }
      return getBaseDirectory();
    }

    @Override
    public void actionPerformed(@Nullable AnActionEvent e) {
      VirtualFile toSelect = getFileToSelect();
      List<OrderRoot> roots = selectRoots(toSelect);
      if (roots.isEmpty()) return;

      final List<OrderRoot> attachedRoots = attachFiles(roots);
      final OrderRoot first = ContainerUtil.getFirstItem(attachedRoots);
      if (first != null) {
        myLastChosen = first.getFile();
      }
      fireLibrariesChanged();
      myTree.requestFocus();
    }

    protected abstract List<OrderRoot> selectRoots(@Nullable VirtualFile initialSelection);
  }

  private class AttachItemAction extends AttachItemActionBase {
    private final AttachRootButtonDescriptor myDescriptor;

    protected AttachItemAction(AttachRootButtonDescriptor descriptor, String title) {
      super(title);
      myDescriptor = descriptor;
    }

    @Override
    protected List<OrderRoot> selectRoots(@Nullable VirtualFile initialSelection) {
      final VirtualFile[] files = myDescriptor.selectFiles(myPanel, initialSelection, myContextModule, getLibraryEditor());
      if (files.length == 0) return Collections.emptyList();

      List<OrderRoot> roots = new ArrayList<OrderRoot>();
      for (VirtualFile file : myDescriptor.scanForActualRoots(files, myPanel)) {
        roots.add(new OrderRoot(file, myDescriptor.getRootType(), myDescriptor.addAsJarDirectories()));
      }
      return roots;
    }
  }

  private List<OrderRoot> attachFiles(List<OrderRoot> roots) {
    final List<OrderRoot> rootsToAttach = filterAlreadyAdded(roots);
    if (!rootsToAttach.isEmpty()) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          getLibraryEditor().addRoots(rootsToAttach);
        }
      });
      updatePropertiesLabel();
      myTreeBuilder.queueUpdate();
    }
    return rootsToAttach;
  }

  private List<OrderRoot> filterAlreadyAdded(@NotNull List<OrderRoot> roots) {
    List<OrderRoot> result = new ArrayList<OrderRoot>();
    for (OrderRoot root : roots) {
      final VirtualFile[] libraryFiles = getLibraryEditor().getFiles(root.getType());
      if (!ArrayUtil.contains(root.getFile(), libraryFiles)) {
        result.add(root);
      }
    }
    return result;
  }

  private void librariesChanged(boolean putFocusIntoTree) {
    updatePropertiesLabel();
    myTreeBuilder.queueUpdate();
    if (putFocusIntoTree) {
      myTree.requestFocus();
    }
    fireLibrariesChanged();
  }

  private void fireLibrariesChanged() {
    for (Runnable listener : myListeners) {
      listener.run();
    }
  }

  public void addListener(Runnable listener) {
    myListeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    myListeners.remove(listener);
  }

  @Nullable
  private static Class<?> getElementsClass(Object[] elements) {
    if (elements.length == 0) {
      return null;
    }
    Class<?> cls = null;
    for (Object element : elements) {
      if (cls == null) {
        cls = element.getClass();
      }
      else {
        if (!cls.equals(element.getClass())) {
          return null;
        }
      }
    }
    return cls;
  }
}
