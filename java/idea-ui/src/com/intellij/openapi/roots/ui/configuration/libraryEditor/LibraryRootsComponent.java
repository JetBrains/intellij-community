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
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.ModuleEditor;
import com.intellij.openapi.roots.ui.configuration.PathUIUtils;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
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
public class LibraryRootsComponent implements Disposable {
  static final UrlComparator ourUrlComparator = new UrlComparator();

  private JPanel myPanel;
  private JButton myRemoveButton;
  private JButton myAttachClassesButton;
  private JButton myAttachJarDirectoriesButton;
  private JButton myAttachSourcesButton;
  private JButton myAttachJavadocsButton;
  private JButton myAttachUrlJavadocsButton;
  private JPanel myTreePanel;
  private JButton myAttachAnnotationsButton;
  private JButton myAttachMoreButton;
  private Tree myTree;
  private LibraryTableTreeBuilder myTreeBuilder;
  private static final Icon INVALID_ITEM_ICON = IconLoader.getIcon("/nodes/ppInvalid.png");
  private static final Icon JAR_DIRECTORY_ICON = IconLoader.getIcon("/nodes/jarDirectory.png");

  private final Collection<Runnable> myListeners = new ArrayList<Runnable>();
  @Nullable private final Project myProject;

  private final Map<DataKey, Object> myFileChooserUserData = new HashMap<DataKey, Object>();
  private final LibraryEditor myLibraryEditor;

  private LibraryRootsComponent(Project project,
                             LibraryEditor libraryEditor){
    myProject = project;
    myLibraryEditor = libraryEditor;
  }

  public static LibraryRootsComponent createComponent(final @Nullable Project project, @NotNull LibraryEditor libraryEditor) {
    LibraryRootsComponent rootsComponent = new LibraryRootsComponent(project, libraryEditor);
    rootsComponent.init(new LibraryTreeStructure(rootsComponent));
    if (project != null) {
      Disposer.register(project, rootsComponent);
    }
    return rootsComponent;
  }

  public static LibraryRootsComponent createComponent(@NotNull LibraryEditor libraryEditor) {
    return createComponent(null, libraryEditor);
  }

  public static boolean libraryAlreadyExists(LibraryTable.ModifiableModel table, String libraryName) {
    for (Iterator<Library> it = table.getLibraryIterator(); it.hasNext(); ) {
      final Library library = it.next();
      final String libName;
      if (table instanceof LibrariesModifiableModel){
        libName = ((LibrariesModifiableModel)table).getLibraryEditor(library).getName();
      }
      else {
        libName = library.getName();
      }
      if (libraryName.equals(libName)) {
        return true;
      }
    }
    return false;
  }
    
  public static String suggestNewLibraryName(LibraryTable.ModifiableModel table) {
    final String name = "Unnamed";
    String candidataName = name;
    for (int idx = 1; libraryAlreadyExists(table, candidataName); candidataName = name + (idx++));
    return candidataName;
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

    myRemoveButton.addActionListener(new RemoveAction());
    myAttachClassesButton.addActionListener(new AttachClassesAction());
    myAttachJarDirectoriesButton.addActionListener(new AttachJarDirectoriesAction());
    myAttachSourcesButton.addActionListener(new AttachSourcesAction());
    myAttachJavadocsButton.addActionListener(new AttachJavadocAction());
    myAttachUrlJavadocsButton.addActionListener(new AttachUrlJavadocAction());
    myAttachAnnotationsButton.addActionListener(new AttachAnnotationsAction());

    final LibraryTableAttachHandler[] handlers = LibraryTableAttachHandler.EP_NAME.getExtensions();
    final LibraryEditor libraryEditor = getLibraryEditor();
    if (handlers.length == 0 || myProject == null) {
      myAttachMoreButton.setVisible(false);
    }
    else {
      myAttachMoreButton.addActionListener(new AttachMoreAction(handlers, libraryEditor));
      if (handlers.length == 1) {
        myAttachMoreButton.setText(handlers[0].getLongName());
      }
    }

    treeSelectionListener.updateButtons();
    Disposer.register(this, myTreeBuilder);
  }

  public Tree getTree() {
    return myTree;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public <T> void addFileChooserContext(DataKey<T> key, T value) {
    myFileChooserUserData.put(key, value);
  }

  public LibraryEditor getLibraryEditor() {
    return myLibraryEditor;
  }

  public boolean hasChanges() {
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
    myTreeBuilder = null;
  }

  private abstract class AttachItemAction implements ActionListener {
    private final FileChooserDescriptor myDescriptor;
    private VirtualFile myLastChosen = null;

    protected abstract String getTitle();
    protected abstract String getDescription();
    protected abstract OrderRootType getRootType();

    protected AttachItemAction() {
      myDescriptor = createDescriptor();
    }

    protected FileChooserDescriptor createDescriptor() {
      return new FileChooserDescriptor(false, true, true, false, true, true);
    }

    protected VirtualFile[] scanForActualRoots(VirtualFile[] rootCandidates) {
      return rootCandidates;
    }

    protected boolean addAsJarDirectories() {
      return false;
    }
    
    public final void actionPerformed(ActionEvent e) {
      myDescriptor.setTitle(getTitle());
      myDescriptor.setDescription(getDescription());
      for (Map.Entry<DataKey, Object> entry : myFileChooserUserData.entrySet()) {
        myDescriptor.putUserData(entry.getKey(), entry.getValue());
      }
      VirtualFile toSelect = getFileToSelect();
      final VirtualFile[] attachedFiles =
        attachFiles(scanForActualRoots(FileChooser.chooseFiles(myPanel, myDescriptor, toSelect)), getRootType(),
                    addAsJarDirectories());
      if (attachedFiles.length > 0) {
        myLastChosen = attachedFiles[0];
      }
      fireLibrariesChanged();
      myTree.requestFocus();
    }

    @Nullable
    private VirtualFile getFileToSelect() {
      VirtualFile toSelect = myLastChosen;
      if (toSelect == null) {
        for (OrderRootType orderRootType : OrderRootType.getAllPersistentTypes()) {
          final VirtualFile[] existingRoots = getLibraryEditor().getFiles(orderRootType);
          if (existingRoots.length > 0) {
            VirtualFile existingRoot = existingRoots [0];
            if (existingRoot.getFileSystem() instanceof JarFileSystem) {
              existingRoot = JarFileSystem.getInstance().getVirtualFileForJar(existingRoot);
            }
            if (existingRoot != null) {
              if (existingRoot.isDirectory()) {
                toSelect = existingRoot;
              }
              else {
                toSelect = existingRoot.getParent();
              }
            }
            break;
          }
        }
      }
      if (toSelect == null) {
        final Project project = myProject;
        if (project != null) {
          //todo[nik] perhaps we shouldn't select project base dir if global library is edited
          toSelect = project.getBaseDir();
        }
      }
      return toSelect;
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
              libraryEditor.addJarDirectory(file, false);
            }
            else {
              libraryEditor.addRoot(file, rootType);
            }
          }
        }
      });
      myTreeBuilder.updateFromRoot();
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

  private class AttachClassesAction extends AttachItemAction {
    @SuppressWarnings({"RefusedBequest"})
    protected FileChooserDescriptor createDescriptor() {
      return new FileChooserDescriptor(false, true, true, false, false, true);
    }

    protected String getTitle() {
      final String name = getLibraryEditor().getName();
      if (StringUtil.isEmpty(name)) {
        return ProjectBundle.message("library.attach.classes.action");
      }
      else {
        return ProjectBundle.message("library.attach.classes.to.library.action", name);
      }
    }

    protected String getDescription() {
      return ProjectBundle.message("library.attach.classes.description");
    }

    protected OrderRootType getRootType() {
      return OrderRootType.CLASSES;
    }
  }

  private class AttachJarDirectoriesAction extends AttachItemAction {
    @SuppressWarnings({"RefusedBequest"})
    protected FileChooserDescriptor createDescriptor() {
      return new FileChooserDescriptor(false, true, false, false, false, true);
    }

    protected boolean addAsJarDirectories() {
      return true;
    }

    protected String getTitle() {
      final String name = getLibraryEditor().getName();
      if (StringUtil.isEmpty(name)) {
        return ProjectBundle.message("library.attach.jar.directory.action");
      }
      else {
        return ProjectBundle.message("library.attach.jar.directory.to.library.action", name);
      }
    }

    protected String getDescription() {
      return ProjectBundle.message("library.attach.jar.directory.description");
    }

    protected OrderRootType getRootType() {
      return OrderRootType.CLASSES;
    }
  }

  private class AttachSourcesAction extends AttachItemAction {
    protected String getTitle() {
      return ProjectBundle.message("library.attach.sources.action");
    }

    protected String getDescription() {
      return ProjectBundle.message("library.attach.sources.description");
    }

    protected OrderRootType getRootType() {
      return OrderRootType.SOURCES;
    }

    protected VirtualFile[] scanForActualRoots(final VirtualFile[] rootCandidates) {
      return PathUIUtils.scanAndSelectDetectedJavaSourceRoots(myPanel, rootCandidates);
    }
  }

  private class AttachAnnotationsAction extends AttachItemAction {
    protected String getTitle() {
      return ProjectBundle.message("library.attach.external.annotations.action");
    }

    protected String getDescription() {
      return ProjectBundle.message("library.attach.external.annotations.description");
    }

    protected OrderRootType getRootType() {
      return AnnotationOrderRootType.getInstance();
    }

    @Override
    protected FileChooserDescriptor createDescriptor() {
      return new FileChooserDescriptor(false, true, false, false, false, false);
    }
  }

  private class AttachJavadocAction extends AttachItemAction {
    protected String getTitle() {
      return ProjectBundle.message("library.attach.javadoc.action");
    }

    protected String getDescription() {
      return ProjectBundle.message("library.attach.javadoc.description");
    }

    protected OrderRootType getRootType() {
      return JavadocOrderRootType.getInstance();
    }
  }

  private class AttachUrlJavadocAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      final VirtualFile vFile = Util.showSpecifyJavadocUrlDialog(myPanel);
      if (vFile != null) {
        attachFiles(new VirtualFile[] {vFile}, JavadocOrderRootType.getInstance(), false);
      }
      myTree.requestFocus();
    }
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
    myTreeBuilder.updateFromRoot();
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
      myRemoveButton.setEnabled(elementsClass != null &&
                !(elementsClass.isAssignableFrom(ClassesElement.class) || elementsClass.equals(SourcesElement.class) || elementsClass.isAssignableFrom(JavadocElement.class) || elementsClass.isAssignableFrom(AnnotationElement.class))
      );
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
          icon = Icons.WEB_ICON;
        }
        else {
          if (presentableFile.isDirectory()) {
            if (isJarDirectory) {
              icon = JAR_DIRECTORY_ICON;
            }
            else {
              icon = Icons.DIRECTORY_CLOSED_ICON;
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
      if (userObject instanceof ItemElementDescriptor || userObject instanceof LibraryElementDescriptor) {
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
    private final LibraryEditor myLibraryEditor;

    public AttachMoreAction(LibraryTableAttachHandler[] handlers, final LibraryEditor libraryEditor) {
      myHandlers = handlers;
      myLibraryEditor = libraryEditor;
    }

    public void actionPerformed(ActionEvent e) {
      final Ref<Library.ModifiableModel> modelRef = Ref.create(null);
      final NullableComputable<Library.ModifiableModel> computable;
      if (myLibraryEditor instanceof ExistingLibraryEditor) {
        final ExistingLibraryEditor libraryEditor = (ExistingLibraryEditor)myLibraryEditor;
        //todo[nik, greg] actually we cannot reliable find target library if the editor is closed so jars are downloaded under the modal progress dialog now
        computable = new NullableComputable<Library.ModifiableModel>() {
          public Library.ModifiableModel compute() {
            if (myTreeBuilder == null) {
              // The following lines were born in severe pain & suffering, please respect
              final Library library = libraryEditor.getLibrary();
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
              return libraryEditor.getModel();
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
              if (myProject != null && myLibraryEditor instanceof ExistingLibraryEditor) {
                ModuleStructureConfigurable.getInstance(myProject).fireItemsChangeListener(((ExistingLibraryEditor)myLibraryEditor).getLibrary());
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
        myHandlers[0].performAttach(myProject, myLibraryEditor, computable).doWhenDone(successRunnable).doWhenRejected(rejectRunnable);
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
                handler.performAttach(myProject, myLibraryEditor, computable).doWhenProcessed(successRunnable).doWhenRejected(rejectRunnable);
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
