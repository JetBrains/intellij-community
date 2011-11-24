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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.roots.libraries.ui.*;
import com.intellij.openapi.roots.libraries.ui.impl.RootDetectionUtil;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 11, 2004
 */
public class LibraryRootsComponent implements Disposable, LibraryEditorComponent {
  static final UrlComparator ourUrlComparator = new UrlComparator();

  private JPanel myPanel;
  private JButton myRemoveButton;
  private JPanel myTreePanel;
  private JButton myAttachMoreButton;
  private MultiLineLabel myPropertiesLabel;
  private JPanel myPropertiesPanel;
  private JPanel myAttachButtonsPanel;
  private LibraryPropertiesEditor myPropertiesEditor;
  private Tree myTree;
  private LibraryTableTreeBuilder myTreeBuilder;

  private final Collection<Runnable> myListeners = new ArrayList<Runnable>();
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
    final Set<LibraryKind<?>> excluded = type != null ? Collections.<LibraryKind<?>>singleton(type.getKind()) : Collections.<LibraryKind<?>>emptySet();
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
    final MyTreeSelectionListener treeSelectionListener = new MyTreeSelectionListener();
    myTree.getSelectionModel().addTreeSelectionListener(treeSelectionListener);
    myTreeBuilder = new LibraryTableTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure);
    myTreePanel.setLayout(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);

    final JPanel buttonsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
    final List<? extends RootDetector> detectors = myDescriptor.getRootDetectors();
    if (!detectors.isEmpty()) {
      JButton button = new JButton(ProjectBundle.message("button.text.attach.files"));
      button.addActionListener(new AttachFilesListener(detectors));
      buttonsPanel.add(button);
    }
    for (AttachRootButtonDescriptor descriptor : myDescriptor.createAttachButtons()) {
      JButton button = new JButton(descriptor.getButtonText());
      button.addActionListener(new AttachItemAction(descriptor));
      buttonsPanel.add(button);
    }
    myAttachButtonsPanel.add(buttonsPanel, BorderLayout.CENTER);

    myRemoveButton.addActionListener(new RemoveAction());
    final LibraryTableAttachHandler[] handlers = LibraryTableAttachHandler.EP_NAME.getExtensions();
    if (handlers.length == 0 || myProject == null || getLibraryEditor().getType() != null) {
      myAttachMoreButton.setVisible(false);
    }
    else {
      myAttachMoreButton.addActionListener(new AttachMoreAction(handlers));
      if (handlers.length == 1) {
        myAttachMoreButton.setText(handlers[0].getLongName());
      }
    }

    treeSelectionListener.updateButtons();
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
    if (!(userObject instanceof NodeDescriptor))  {
      return null;
    }
    final Object element = ((NodeDescriptor)userObject).getElement();
    if (!(element instanceof LibraryTableTreeContentElement)) {
      return null;
    }
    return element;
  }

  public void renameLibrary(String newName) {
    final LibraryEditor libraryEditor = getLibraryEditor();
    libraryEditor.setName(newName);
    librariesChanged(false);
  }

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

  private class AttachFilesListener extends AttachItemActionBase {
    private final List<? extends RootDetector> myDetectors;

    public AttachFilesListener(List<? extends RootDetector> detectors) {
      myDetectors = detectors;
    }

    @Override
    protected List<OrderRoot> selectRoots(@Nullable VirtualFile initialSelection) {
      final FileChooserDescriptor chooserDescriptor = myDescriptor.createAttachFilesChooserDescriptor();
      final String name = getLibraryEditor().getName();
      chooserDescriptor.setTitle(StringUtil.isEmpty(name) ? ProjectBundle.message("library.attach.files.action")
                                                          : ProjectBundle.message("library.attach.files.to.library.action", name));
      chooserDescriptor.setDescription(ProjectBundle.message("library.attach.files.description"));
      if (myContextModule != null) {
        chooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myContextModule);
      }
      final VirtualFile[] files = FileChooser.chooseFiles(myPanel, chooserDescriptor, initialSelection);
      if (files.length == 0) return Collections.emptyList();

      return RootDetectionUtil.detectRoots(Arrays.asList(files), myPanel, myProject, myDetectors, true);
    }
  }

  public abstract class AttachItemActionBase implements ActionListener {
    private VirtualFile myLastChosen = null;

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

    public final void actionPerformed(ActionEvent e) {
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

    protected AttachItemAction(AttachRootButtonDescriptor descriptor) {
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

  private class RemoveAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      final Object[] selectedElements = getSelectedElements();
      if (selectedElements.length == 0) {
        return;
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          for (Object selectedElement : selectedElements) {
            if (selectedElement instanceof ItemElement) {
              final ItemElement itemElement = (ItemElement)selectedElement;
              getLibraryEditor().removeRoot(itemElement.getUrl(), itemElement.getRootType());
            }
          }
        }
      });
      librariesChanged(true);
    }
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
    Runnable[] listeners = myListeners.toArray(new Runnable[myListeners.size()]);
    for (Runnable listener : listeners) {
      listener.run();
    }
  }

  public void addListener(Runnable listener) {
    myListeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    myListeners.remove(listener);
  }

  private class MyTreeSelectionListener implements TreeSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      updateButtons();
    }

    public void updateButtons() {
      final Object[] selectedElements = getSelectedElements();
      final Class<?> elementsClass = getElementsClass(selectedElements);
      myRemoveButton.setEnabled(elementsClass != null && !elementsClass.isAssignableFrom(OrderRootTypeElement.class));
    }

    @Nullable
    private Class<?> getElementsClass(Object[] elements) {
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

  private class AttachMoreAction implements ActionListener {
    private final LibraryTableAttachHandler[] myHandlers;

    public AttachMoreAction(LibraryTableAttachHandler[] handlers) {
      myHandlers = handlers;
    }

    public void actionPerformed(ActionEvent e) {
      final LibraryEditor libraryEditor = getLibraryEditor();
      final Ref<Library.ModifiableModel> modelRef = Ref.create(null);
      final NullableComputable<Library.ModifiableModel> computable;
      if (libraryEditor instanceof ExistingLibraryEditor) {
        final ExistingLibraryEditor existingLibraryEditor = (ExistingLibraryEditor)libraryEditor;
        //todo[nik, greg] actually we cannot reliable find target library if the editor is closed so jars are downloaded under the modal progress dialog now
        computable = new NullableComputable<Library.ModifiableModel>() {
          public Library.ModifiableModel compute() {
            if (myTreeBuilder == null) {
              // The following lines were born in severe pain & suffering, please respect
              final Library library = existingLibraryEditor.getLibrary();
              final InvocationHandler invocationHandler = Proxy.isProxyClass(library.getClass())? Proxy.getInvocationHandler(library) : null;
              final Library realLibrary = invocationHandler instanceof ModuleEditor.ProxyDelegateAccessor? (Library)((ModuleEditor.ProxyDelegateAccessor)invocationHandler)
                .getDelegate() : library;
              final Module module = realLibrary instanceof LibraryImpl && ((LibraryImpl)realLibrary).isDisposed()? ((LibraryImpl)realLibrary).getModule() : null;
              if (module != null && module.isDisposed()) return null; // no way
              final Library targetLibrary = module != null? LibraryUtil.findLibrary(module, realLibrary.getName()) : realLibrary;
              final Library.ModifiableModel model = targetLibrary.getModifiableModel();
              modelRef.set(model);
              return model;
            }
            else {
              return existingLibraryEditor.getModel();
            }
          }
        };
      }
      else {
        computable = null;
      }

      final Runnable successRunnable = new Runnable() {
        public void run() {
          if (modelRef.get() != null) {
            modelRef.get().commit();
          }
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              if (myTreeBuilder != null) myTreeBuilder.queueUpdate();
              if (myProject != null && libraryEditor instanceof ExistingLibraryEditor) {
                ModuleStructureConfigurable.getInstance(myProject).fireItemsChangeListener(((ExistingLibraryEditor)libraryEditor).getLibrary());
              }
            }
          });
        }
      };
      final Runnable rejectRunnable = new Runnable() {
        public void run() {
          if (modelRef.get() != null) {
            Disposer.dispose(modelRef.get());
          }
        }
      };
      if (myHandlers.length == 1) {
        myHandlers[0].performAttach(myProject, libraryEditor, computable).doWhenDone(successRunnable).doWhenRejected(rejectRunnable);
      }
      else {
        final ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<LibraryTableAttachHandler>(null, myHandlers) {
          @NotNull
          public String getTextFor(final LibraryTableAttachHandler handler) {
            return handler.getShortName();
          }

          public Icon getIconFor(final LibraryTableAttachHandler handler) {
            return handler.getIcon();
          }

          public PopupStep onChosen(final LibraryTableAttachHandler handler, final boolean finalChoice) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                handler.performAttach(myProject, libraryEditor, computable).doWhenProcessed(successRunnable).doWhenRejected(rejectRunnable);
              }
            });
            return PopupStep.FINAL_CHOICE;
          }
        });
        popup.showUnderneathOf(myAttachMoreButton);

      }
    }
  }
}
