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

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.libraries.ui.AttachRootButtonDescriptor;
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent;
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor;
import com.intellij.openapi.roots.libraries.ui.LibraryRootsComponentDescriptor;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.ui.ex.MultiLineLabel;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
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
import java.io.File;
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
  private static final Icon INVALID_ITEM_ICON = IconLoader.getIcon("/nodes/ppInvalid.png");
  private static final Icon JAR_DIRECTORY_ICON = IconLoader.getIcon("/nodes/jarDirectory.png");

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
    updateProperties();
  }

  @NotNull
  @Override
  public LibraryProperties getProperties() {
    return getLibraryEditor().getProperties();
  }

  private void updateProperties() {
    StringBuilder text = new StringBuilder();
    for (String description : LibraryPresentationManager.getInstance().getDescriptions(getLibraryEditor().getFiles(OrderRootType.CLASSES))) {
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
    new MyTreeSpeedSearch(myTree);
    myTree.setCellRenderer(new LibraryTreeRenderer());
    final MyTreeSelectionListener treeSelectionListener = new MyTreeSelectionListener();
    myTree.getSelectionModel().addTreeSelectionListener(treeSelectionListener);
    myTreeBuilder = new LibraryTableTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure);
    myTreePanel.setLayout(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);

    final JPanel buttonsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 5, true, false));
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

  private class AttachItemAction implements ActionListener {
    private VirtualFile myLastChosen = null;
    private final AttachRootButtonDescriptor myDescriptor;

    protected AttachItemAction(AttachRootButtonDescriptor descriptor) {
      myDescriptor = descriptor;
    }

    public final void actionPerformed(ActionEvent e) {
      VirtualFile toSelect = getFileToSelect();
      final VirtualFile[] files = myDescriptor.selectFiles(myPanel, toSelect, myContextModule, getLibraryEditor());
      if (files.length == 0) return;

      final VirtualFile[] attachedFiles = attachFiles(myDescriptor.scanForActualRoots(files, myPanel), myDescriptor.getRootType(), myDescriptor.addAsJarDirectories());
      if (attachedFiles.length > 0) {
        myLastChosen = attachedFiles[0];
      }
      fireLibrariesChanged();
      myTree.requestFocus();
    }

    @Nullable
    private VirtualFile getFileToSelect() {
      if (myLastChosen != null) {
        return myLastChosen;
      }

      final VirtualFile directory = getExistingRootDirectory();
      if (directory != null) {
        return directory;
      }
      return getBaseDirectory();
    }
  }

  private VirtualFile[] attachFiles(final VirtualFile[] files, final OrderRootType rootType, final boolean isJarDirectories) {
    final VirtualFile[] filesToAttach = filterAlreadyAdded(files, rootType);
    if (filesToAttach.length > 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final LibraryEditor libraryEditor = getLibraryEditor();
          for (VirtualFile file : filesToAttach) {
            if (isJarDirectories) {
              libraryEditor.addJarDirectory(file, false, rootType);
            }
            else {
              libraryEditor.addRoot(file, rootType);
            }
          }
        }
      });
      updateProperties();
      myTreeBuilder.queueUpdate();
    }
    return filesToAttach;
  }

  private VirtualFile[] filterAlreadyAdded(VirtualFile[] files, final OrderRootType rootType) {
    if (files == null || files.length == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final Set<VirtualFile> chosenFilesSet = new HashSet<VirtualFile>(Arrays.asList(files));
    final Set<VirtualFile> alreadyAdded = new HashSet<VirtualFile>();
    final VirtualFile[] libraryFiles = getLibraryEditor().getFiles(rootType);
    ContainerUtil.addAll(alreadyAdded, libraryFiles);
    chosenFilesSet.removeAll(alreadyAdded);
    return VfsUtil.toVirtualFileArray(chosenFilesSet);
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

  protected void librariesChanged(boolean putFocusIntoTree) {
    updateProperties();
    myTreeBuilder.queueUpdate();
    if (putFocusIntoTree) {
      myTree.requestFocus();
    }
    fireLibrariesChanged();
  }

  private void fireLibrariesChanged() {
    Runnable[] runnables = myListeners.toArray(new Runnable[myListeners.size()]);
    for (Runnable listener : runnables) {
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



  static Icon getIconForUrl(final String url, final boolean isValid, final boolean isJarDirectory) {
    final Icon icon;
    if (isValid) {
      VirtualFile presentableFile;
      if (isJarFileRoot(url)) {
        presentableFile = LocalFileSystem.getInstance().findFileByPath(getPresentablePath(url));
      }
      else {
        presentableFile = VirtualFileManager.getInstance().findFileByUrl(url);
      }
      if (presentableFile != null && presentableFile.isValid()) {
        if (presentableFile.getFileSystem() instanceof HttpFileSystem) {
          icon = PlatformIcons.WEB_ICON;
        }
        else {
          if (presentableFile.isDirectory()) {
            if (isJarDirectory) {
              icon = JAR_DIRECTORY_ICON;
            }
            else {
              icon = PlatformIcons.DIRECTORY_CLOSED_ICON;
            }
          }
          else {
            icon = IconUtilEx.getIcon(presentableFile, 0, null);
          }
        }
      }
      else {
        icon = INVALID_ITEM_ICON;
      }
    }
    else {
      icon = INVALID_ITEM_ICON;
    }
    return icon;
  }

  static String getPresentablePath(final String url) {
    String presentablePath = VirtualFileManager.extractPath(url);
    if (isJarFileRoot(url)) {
      presentablePath = presentablePath.substring(0, presentablePath.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return presentablePath;
  }

  private static boolean isJarFileRoot(final String url) {
    return VirtualFileManager.extractPath(url).endsWith(JarFileSystem.JAR_SEPARATOR);
  }

  private static class MyTreeSpeedSearch extends TreeSpeedSearch {
    public MyTreeSpeedSearch(final Tree tree) {
      super(tree);
    }

    public boolean isMatchingElement(Object element, String pattern) {
      Object userObject = ((DefaultMutableTreeNode)((TreePath)element).getLastPathComponent()).getUserObject();
      if (userObject instanceof ItemElementDescriptor) {
        String str = getElementText(element);
        if (str == null) {
          return false;
        }
        if (!hasCapitals(pattern)) { // be case-sensitive only if user types capitals
          str = str.toLowerCase();
        }
        if (pattern.contains(File.separator)) {
          return compare(str,pattern);
        }
        final StringTokenizer tokenizer = new StringTokenizer(str, File.separator);
        while (tokenizer.hasMoreTokens()) {
          final String token = tokenizer.nextToken();
          if (compare(token,pattern)) {
            return true;
          }
        }
        return false;
      }
      else {
        return super.isMatchingElement(element, pattern);
      }
    }

    private static boolean hasCapitals(String str) {
      for (int idx = 0; idx < str.length(); idx++) {
        if (Character.isUpperCase(str.charAt(idx))) {
          return true;
        }
      }
      return false;
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
