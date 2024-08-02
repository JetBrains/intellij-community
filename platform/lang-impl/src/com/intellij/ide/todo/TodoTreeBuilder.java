// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.ide.todo.nodes.TodoFileNode;
import com.intellij.ide.todo.nodes.TodoItemNode;
import com.intellij.ide.todo.nodes.TodoTreeHelper;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiTodoSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class TodoTreeBuilder implements Disposable {

  private static final Logger LOG = Logger.getInstance(TodoTreeBuilder.class);
  public static final Comparator<NodeDescriptor<?>> NODE_DESCRIPTOR_COMPARATOR =
    Comparator.<NodeDescriptor<?>>comparingInt(NodeDescriptor::getWeight).thenComparingInt(NodeDescriptor::getIndex);

  protected final @NotNull Project myProject;

  /**
   * All files that have T.O.D.O items are presented as tree. This tree help a lot
   * to separate these files by directories.
   */
  protected final @NotNull FileTree myFileTree = new FileTree();
  /**
   * This set contains "dirty" files. File is "dirty" if it's currently unknown
   * whether the file contains T.O.D.O item or not. To determine this it's necessary
   * to perform {@link #hasDirtyFiles()} operation (potentially CPU expensive).
   * These "dirty" files are validated in {@link #validateCache()} method.
   * <p>
   * Mutate in a background thread only.
   */
  protected final Set<VirtualFile> myDirtyFileSet = ConcurrentHashMap.newKeySet();

  //used from EDT and from StructureTreeModel invoker thread
  protected final Map<VirtualFile, EditorHighlighter> myFile2Highlighter = ContainerUtil.createConcurrentSoftValueMap();

  private final @NotNull JTree myTree;
  /**
   * If this flag is false then the refresh() method does nothing. But when
   * the flag becomes true and myDirtyFileSet isn't empty the update is invoked.
   * This is done for optimization reasons: if TodoPane is not visible then
   * updates isn't invoked.
   */
  private volatile boolean myUpdatable;

  /**
   * Updates tree if containing files change VCS status.
   */
  private final MyFileStatusListener myFileStatusListener = new MyFileStatusListener();
  private TodoTreeStructure myTreeStructure;
  private StructureTreeModel<? extends TodoTreeStructure> myModel;
  private boolean myDisposed;

  private final TodoTreeBuilderCoroutineHelper myCoroutineHelper = new TodoTreeBuilderCoroutineHelper(this);

  /**
   * To be used in {@link #rebuildCache()} only!
   */
  private final List<CompletableFuture<?>> myFutures = new SmartList<>();

  public TodoTreeBuilder(@NotNull JTree tree,
                  @NotNull Project project) {
    myTree = tree;
    myProject = project;

    Disposer.register(myProject, this);
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(new MyPsiTreeChangeListener(), this);

    //setCanYieldUpdate(true);
  }

  protected @NotNull PsiTodoSearchHelper getSearchHelper() {
    return PsiTodoSearchHelper.getInstance(myProject);
  }

  protected final @NotNull Project getProject() {
    return myProject;
  }

  protected final @NotNull JTree getTree() {
    return myTree;
  }

  protected final @NotNull TodoTreeBuilderCoroutineHelper getCoroutineHelper() {
    return myCoroutineHelper;
  }

  protected final @NotNull StructureTreeModel<? extends TodoTreeStructure> getModel() {
    return myModel;
  }

  protected final void setModel(@NotNull StructureTreeModel<? extends TodoTreeStructure> model) {
    myModel = model;
  }

  /**
   * Initializes the builder. Subclasses should don't forget to call this method after constructor has
   * been invoked.
   */
  public final void init() {
    myTreeStructure = createTreeStructure();
    myTreeStructure.setTreeBuilder(this);

    try {
      rebuildCache();
    }
    catch (IndexNotReadyException ignore) {
    }

    FileStatusManager.getInstance(myProject).addFileStatusListener(myFileStatusListener, this);
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public final void dispose() {
    myDisposed = true;
  }

  protected final boolean isUpdatable() {
    return myUpdatable;
  }

  /**
   * Sets whether the builder updates the tree when data change.
   */
  protected final void setUpdatable(boolean updatable) {
    if (myUpdatable != updatable) {
      myUpdatable = updatable;
      if (updatable) {
        updateTree();
      }
    }
  }

  protected abstract @NotNull TodoTreeStructure createTreeStructure();

  public final TodoTreeStructure getTodoTreeStructure() {
    return myTreeStructure;
  }

  /**
   * @return read-only iterator of all current PSI files that can contain TODOs.
   * Don't invoke its {@code remove} method. For "removing" use {@code markFileAsDirty} method.
   * <b>Note, that {@code next()} method of iterator can return {@code null} elements.</b>
   * These {@code null} elements correspond to the invalid PSI files (PSI file cannot be found by
   * virtual file, or virtual file is invalid).
   * The reason why we return such "dirty" iterator is the performance.
   */
  public Iterator<PsiFile> getAllFiles() {
    final Iterator<VirtualFile> iterator = myFileTree.getFileIterator();
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public @Nullable PsiFile next() {
        VirtualFile vFile = iterator.next();
        if (vFile == null || !vFile.isValid()) {
          return null;
        }
        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(vFile);
        if (psiFile == null || !psiFile.isValid()) {
          return null;
        }
        return psiFile;
      }

      @Override
      public void remove() {
        throw new IllegalArgumentException();
      }
    };
  }

  /**
   * @return read-only iterator of all valid PSI files that can have T.O.D.O items
   * and which are located under specified {@code psiDirectory}.
   * @see FileTree#getFiles(VirtualFile)
   */
  public Iterator<PsiFile> getFiles(PsiDirectory psiDirectory) {
    return getFiles(psiDirectory, true);
  }

  /**
   * @return read-only iterator of all valid PSI files that can have T.O.D.O items
   * and which are located under specified {@code psiDirectory}.
   * @see FileTree#getFiles(VirtualFile)
   */
  public Iterator<PsiFile> getFiles(PsiDirectory psiDirectory, final boolean skip) {
    List<VirtualFile> files = myFileTree.getFiles(psiDirectory.getVirtualFile());
    List<PsiFile> psiFileList = new ArrayList<>(files.size());
    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (VirtualFile file : files) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(psiDirectory);
      if (module != null) {
        final boolean isInContent = ModuleRootManager.getInstance(module).getFileIndex().isInContent(file);
        if (!isInContent) continue;
      }
      if (file.isValid()) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) {
          final PsiDirectory directory = psiFile.getContainingDirectory();
          if (directory == null || !skip || !TodoTreeHelper.getInstance(myProject).skipDirectory(directory)) {
            psiFileList.add(psiFile);
          }
        }
      }
    }
    return psiFileList.iterator();
  }

  /**
   * @return read-only iterator of all valid PSI files that can have T.O.D.O items
   * and which are located under specified {@code psiDirectory}.
   * @see FileTree#getFiles(VirtualFile)
   */
  public Iterator<PsiFile> getFilesUnderDirectory(PsiDirectory psiDirectory) {
    List<VirtualFile> files = myFileTree.getFilesUnderDirectory(psiDirectory.getVirtualFile());
    List<PsiFile> psiFileList = new ArrayList<>(files.size());
    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (VirtualFile file : files) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(psiDirectory);
      if (module != null) {
        final boolean isInContent = ModuleRootManager.getInstance(module).getFileIndex().isInContent(file);
        if (!isInContent) continue;
      }
      if (file.isValid()) {
        PsiFile psiFile = psiManager.findFile(file);
        if (psiFile != null) {
          psiFileList.add(psiFile);
        }
      }
    }
    return psiFileList.iterator();
  }


  /**
   * @return read-only iterator of all valid PSI files that can have T.O.D.O items
   * and which in specified {@code module}.
   * @see FileTree#getFiles(VirtualFile)
   */
  public Iterator<PsiFile> getFiles(Module module) {
    if (module.isDisposed()) return Collections.emptyIterator();
    ArrayList<PsiFile> psiFileList = new ArrayList<>();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile virtualFile : contentRoots) {
      List<VirtualFile> files = myFileTree.getFiles(virtualFile);
      PsiManager psiManager = PsiManager.getInstance(myProject);
      for (VirtualFile file : files) {
        if (fileIndex.getModuleForFile(file) != module) continue;
        if (file.isValid()) {
          PsiFile psiFile = psiManager.findFile(file);
          if (psiFile != null) {
            psiFileList.add(psiFile);
          }
        }
      }
    }
    return psiFileList.iterator();
  }


  /**
   * @return {@code true} if specified {@code psiFile} can contains too items.
   * It means that file is in "dirty" file set or in "current" file set.
   */
  private boolean canContainTodoItems(PsiFile psiFile) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    VirtualFile vFile = psiFile.getVirtualFile();
    return myFileTree.contains(vFile) || myDirtyFileSet.contains(vFile);
  }

  /**
   * Marks specified {@link VirtualFile} as dirty.
   * It means that file is being added into "dirty" file set.
   * It presents in current file set also but the next validateCache call will validate this
   * "dirty" file. This method should be invoked when any modifications inside the file
   * have happened.
   */
  @RequiresBackgroundThread
  protected final void markFileAsDirty(@NotNull VirtualFile file) {
    if (!(file instanceof LightVirtualFile)) {
      myDirtyFileSet.add(file);
    }
  }

  protected final synchronized @NotNull CompletableFuture<?> rebuildCache() {
    for (CompletableFuture<?> future : myFutures) {
      future.cancel(true);
    }
    myFutures.clear();

    CompletableFuture<?> future = myCoroutineHelper.scheduleCacheAndTreeUpdate();
    myFutures.add(future);
    return future;
  }

  @RequiresBackgroundThread
  protected void collectFiles(@NotNull Consumer<? super @NotNull PsiFile> consumer) {
    TodoTreeStructure treeStructure = getTodoTreeStructure();
    PsiTodoSearchHelper searchHelper = getSearchHelper();
    searchHelper.processFilesWithTodoItems(psiFile -> {
      if (searchHelper.getTodoItemsCount(psiFile) > 0 && treeStructure.accept(psiFile)) {
        consumer.accept(psiFile);
      }
      return true;
    });
  }

  @RequiresBackgroundThread
  protected final void clearCache() {
    myFileTree.clear();
    myDirtyFileSet.clear();
    myFile2Highlighter.clear();
  }

  protected final boolean hasDirtyFiles() {
    synchronized (myDirtyFileSet) {
      if (myDirtyFileSet.isEmpty()) {
        return false;
      }

      validateCache();
      return true;
    }
  }

  @RequiresEdt
  private void withLoadingPanel(@NotNull Consumer<? super JBLoadingPanel> consumer) {
    JBLoadingPanel loadingPanel = UIUtil.getParentOfType(JBLoadingPanel.class, myTree);
    if (loadingPanel != null) {
      consumer.accept(loadingPanel);
    }
  }

  private void validateCache() {
    TodoTreeStructure treeStructure = getTodoTreeStructure();
    // First we need to update "dirty" file set.
    for (VirtualFile file : myDirtyFileSet) {
      PsiFile psiFile = file.isValid() ? PsiManager.getInstance(myProject).findFile(file) : null;
      if (psiFile == null || !treeStructure.accept(psiFile)) {
        if (myFileTree.contains(file)) {
          myFileTree.removeFile(file);
          myFile2Highlighter.remove(file);
        }
      }
      else { // file is valid and contains T.O.D.O items
        myFileTree.removeFile(file);
        myFileTree.add(file); // file can be moved. remove/add calls move it to another place
        EditorHighlighter highlighter = myFile2Highlighter.get(file);
        if (highlighter != null) { // update highlighter text
          highlighter.setText(PsiDocumentManager.getInstance(myProject).getDocument(psiFile).getCharsSequence());
        }
      }
    }

    myDirtyFileSet.clear();
  }

  protected boolean isAutoExpandNode(NodeDescriptor descriptor) {
    return getTodoTreeStructure().isAutoExpandNode(descriptor);
  }

  /**
   * @return first {@code SmartTodoItemPointer} that is the children (in depth) of the specified {@code element}.
   * If {@code element} itself is a {@code TodoItem} then the method returns the {@code element}.
   */
  public TodoItemNode getFirstPointerForElement(@Nullable Object element) {
    if (element instanceof TodoItemNode) {
      return (TodoItemNode)element;
    }
    else if (!(element instanceof AbstractTreeNode)) {
      return null;
    }
    else {
      Object[] children = getTodoTreeStructure().getChildElements(element);
      if (children.length == 0) {
        return null;
      }
      Object firstChild = children[0];
      if (firstChild instanceof TodoItemNode) {
        return (TodoItemNode)firstChild;
      }
      else {
        return getFirstPointerForElement(firstChild);
      }
    }
  }

  /**
   * @return last {@code SmartTodoItemPointer} that is the children (in depth) of the specified {@code element}.
   * If {@code element} itself is a {@code TodoItem} then the method returns the {@code element}.
   */
  public TodoItemNode getLastPointerForElement(Object element) {
    if (element instanceof TodoItemNode) {
      return (TodoItemNode)element;
    }
    else {
      Object[] children = getTodoTreeStructure().getChildElements(element);
      if (children.length == 0) {
        return null;
      }
      Object firstChild = children[children.length - 1];
      if (firstChild instanceof TodoItemNode) {
        return (TodoItemNode)firstChild;
      }
      else {
        return getLastPointerForElement(firstChild);
      }
    }
  }

  public final @NotNull Promise<?> updateTree() {
    return myUpdatable ?
           Promises.asPromise(myCoroutineHelper.scheduleUpdateTree()) :
           Promises.resolvedPromise();
  }

  @VisibleForTesting
  @RequiresEdt
  protected void onUpdateStarted() {
    withLoadingPanel(JBLoadingPanel::startLoading);
  }

  @VisibleForTesting
  @RequiresEdt
  protected void onUpdateFinished() {
    withLoadingPanel(JBLoadingPanel::stopLoading);
  }

  public void select(Object obj) {
    TodoNodeVisitor visitor = getVisitorFor(obj);

    if (visitor == null) {
      TreeUtil.promiseSelectFirst(myTree);
    }
    else {
      TreeUtil.promiseSelect(myTree, visitor).onError(error -> {
        //select root if path disappeared from the tree
        TreeUtil.promiseSelectFirst(myTree);
      });
    }
  }

  protected static @Nullable TodoNodeVisitor getVisitorFor(@NotNull Object obj) {
    if (obj instanceof TodoItemNode) {
      SmartTodoItemPointer value = ((TodoItemNode)obj).getValue();
      if (value != null) {
        return new TodoNodeVisitor(value::getTodoItem,
                                   value.getTodoItem().getFile().getVirtualFile());
      }
      else {
        return null;
      }
    }
    else {
      Object o = obj instanceof AbstractTreeNode ? ((AbstractTreeNode<?>)obj).getValue() : null;
      return new TodoNodeVisitor(() -> obj instanceof AbstractTreeNode ? ((AbstractTreeNode<?>)obj).getValue() : obj,
                                 o instanceof PsiElement ? PsiUtilCore.getVirtualFile((PsiElement)o) : null);
    }
  }

  static @Nullable PsiFile getFileForNodeDescriptor(@NotNull NodeDescriptor<?> obj) {
    if (obj instanceof TodoFileNode) {
      return ((TodoFileNode)obj).getValue();
    }
    else if (obj instanceof TodoItemNode) {
      SmartTodoItemPointer pointer = ((TodoItemNode)obj).getValue();
      return pointer.getTodoItem().getFile();
    }
    return null;
  }

  /**
   * Sets whether packages are shown or not.
   */
  void setShowPackages(boolean state) {
    getTodoTreeStructure().setShownPackages(state);
    rebuildTreeOnSettingChange();
  }

  /**
   * @param state if {@code true} then view is in "flatten packages" mode.
   */
  void setFlattenPackages(boolean state) {
    getTodoTreeStructure().setFlattenPackages(state);
    rebuildTreeOnSettingChange();
  }

  void setShowModules(boolean state) {
    getTodoTreeStructure().setShownModules(state);
    rebuildTreeOnSettingChange();
  }

  private void rebuildTreeOnSettingChange() {
    myCoroutineHelper.scheduleCacheValidationAndTreeUpdate();
  }

  /**
   * Sets new {@code TodoFilter}, rebuild whole the caches and immediately update the tree.
   *
   * @see TodoTreeStructure#setTodoFilter(TodoFilter)
   */
  void setTodoFilter(TodoFilter filter) {
    getTodoTreeStructure().setTodoFilter(filter);
    try {
      rebuildCache();
    }
    catch (IndexNotReadyException ignored) {
    }
  }

  /**
   * @return next {@code TodoItem} for the passed {@code pointer}. Returns {@code null}
   * if the {@code pointer} is the last t.o.d.o item in the tree.
   */
  public TodoItemNode getNextPointer(TodoItemNode pointer) {
    Object sibling = getNextSibling(pointer);
    if (sibling == null) {
      return null;
    }
    if (sibling instanceof TodoItemNode) {
      return (TodoItemNode)sibling;
    }
    else {
      return getFirstPointerForElement(sibling);
    }
  }

  /**
   * @return next sibling of the passed element. If there is no sibling then
   * returns {@code null}.
   */
  Object getNextSibling(Object obj) {
    Object parent = getTodoTreeStructure().getParentElement(obj);
    if (parent == null) {
      return null;
    }
    Object[] children = getTodoTreeStructure().getChildElements(parent);
    Arrays.sort(children, (Comparator)NODE_DESCRIPTOR_COMPARATOR);
    int idx = -1;
    for (int i = 0; i < children.length; i++) {
      if (obj.equals(children[i])) {
        idx = i;
        break;
      }
    }
    if (idx == -1) {
      return null;
    }
    if (idx < children.length - 1) {
      return children[idx + 1];
    }
    // passed object is the last in the list. In this case we have to return first child of the
    // next parent's sibling.
    return getNextSibling(parent);
  }

  /**
   * @return next {@code SmartTodoItemPointer} for the passed {@code pointer}. Returns {@code null}
   * if the {@code pointer} is the last t.o.d.o item in the tree.
   */
  public TodoItemNode getPreviousPointer(TodoItemNode pointer) {
    Object sibling = getPreviousSibling(pointer);
    if (sibling == null) {
      return null;
    }
    if (sibling instanceof TodoItemNode) {
      return (TodoItemNode)sibling;
    }
    else {
      return getLastPointerForElement(sibling);
    }
  }

  /**
   * @return previous sibling of the element of passed type. If there is no sibling then
   * returns {@code null}.
   */
  Object getPreviousSibling(Object obj) {
    Object parent = getTodoTreeStructure().getParentElement(obj);
    if (parent == null) {
      return null;
    }
    Object[] children = getTodoTreeStructure().getChildElements(parent);
    Arrays.sort(children, (Comparator)NODE_DESCRIPTOR_COMPARATOR);
    int idx = -1;
    for (int i = 0; i < children.length; i++) {
      if (obj.equals(children[i])) {
        idx = i;

        break;
      }
    }
    if (idx == -1) {
      return null;
    }
    if (idx > 0) {
      return children[idx - 1];
    }
    // passed object is the first in the list. In this case we have to return last child of the
    // previous parent's sibling.
    return getPreviousSibling(parent);
  }

  /**
   * @return {@code SelectInEditorManager} for the specified {@code psiFile}. Highlighters are
   * lazy created and initialized.
   */
  public EditorHighlighter getHighlighter(PsiFile psiFile, Document document) {
    VirtualFile file = psiFile.getVirtualFile();
    EditorHighlighter highlighter = myFile2Highlighter.get(file);
    if (highlighter == null) {
      highlighter = HighlighterFactory.createHighlighter(UsageTreeColorsScheme.getInstance().getScheme(), file.getName(), myProject);
      highlighter.setText(document.getCharsSequence());
      myFile2Highlighter.put(file, highlighter);
    }
    return highlighter;
  }

  public boolean isDirectoryEmpty(@NotNull PsiDirectory psiDirectory) {
    return myFileTree.isDirectoryEmpty(psiDirectory.getVirtualFile());
  }

  private final class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {

    @Override
    public void childAdded(@NotNull PsiTreeChangeEvent e) {
      // If local modification
      PsiFile file = e.getFile();
      if (file != null) {
        scheduleMarkFileAsDirtyAndUpdateTree(file);
        return;
      }
      // If added element if PsiFile and it doesn't contains TODOs, then do nothing
      PsiElement child = e.getChild();
      if (child instanceof PsiFile psiFile) {
        scheduleMarkFileAsDirtyAndUpdateTree(psiFile);
      }
    }

    @Override
    public void beforeChildRemoval(@NotNull PsiTreeChangeEvent e) {
      // local modification
      final PsiFile file = e.getFile();
      if (file != null) {
        scheduleMarkFileAsDirtyAndUpdateTree(file);
        return;
      }
      PsiElement child = e.getChild();
      if (child instanceof PsiFile psiFile) { // file will be removed
        scheduleMarkFileAsDirtyAndUpdateTree(psiFile);
        return;
      }
      if (child instanceof PsiDirectory psiDirectory) { // directory will be removed
        List<VirtualFile> files = myFileTree.getFiles(psiDirectory.getVirtualFile());
        myCoroutineHelper.scheduleMarkFilesAsDirtyAndUpdateTree(files);
      }
      else {
        if (PsiTreeUtil.getParentOfType(child, PsiComment.class, false) != null) { // change inside comment
          scheduleMarkFileAsDirtyAndUpdateTree(child.getContainingFile());
        }
      }
    }

    @Override
    public void childMoved(@NotNull PsiTreeChangeEvent e) {
      PsiFile file = e.getFile();
      if (file != null) { // local change
        scheduleMarkFileAsDirtyAndUpdateTree(file);
        return;
      }
      PsiElement child = e.getChild();
      if (child instanceof PsiFile psiFile) { // file was moved
        if (canContainTodoItems(psiFile)) { // moved file contains TODOs
          scheduleMarkFileAsDirtyAndUpdateTree(psiFile);
        }
        return;
      }
      if (child instanceof PsiDirectory psiDirectory) { // directory was moved. mark all its files as dirty.
        ArrayList<VirtualFile> files = new ArrayList<>();
        for (Iterator<? extends PsiFile> i = getAllFiles(); i.hasNext(); ) {
          PsiFile psiFile = i.next();
          if (psiFile == null ||  // skip invalid PSI files
              !psiFile.isValid() ||
              !PsiTreeUtil.isAncestor(psiDirectory, psiFile, true)) {
            continue;
          }
          VirtualFile virtualFile = psiFile.getVirtualFile();
          if (virtualFile != null) {
            files.add(virtualFile);
          }
        }

        myCoroutineHelper.scheduleMarkFilesAsDirtyAndUpdateTree(files);
      }
    }

    @Override
    public void childReplaced(@NotNull PsiTreeChangeEvent e) {
      scheduleMarkFileAsDirtyAndUpdateTree(e.getFile());
    }

    @Override
    public void childrenChanged(@NotNull PsiTreeChangeEvent e) {
      scheduleMarkFileAsDirtyAndUpdateTree(e.getFile());
    }

    @Override
    public void propertyChanged(@NotNull PsiTreeChangeEvent e) {
      switch (e.getPropertyName()) {
        case PsiTreeChangeEvent.PROP_ROOTS ->  // rebuild all tree when source roots were changed
          rebuildCache();
        case PsiTreeChangeEvent.PROP_WRITABLE, PsiTreeChangeEvent.PROP_FILE_NAME -> {
          PsiFile psiFile = (PsiFile)e.getElement();
          if (!canContainTodoItems(psiFile)) { // don't do anything if file cannot contain to-do items
            return;
          }
          updateTree();
        }
        case PsiTreeChangeEvent.PROP_DIRECTORY_NAME -> {
          PsiDirectory psiDirectory = (PsiDirectory)e.getElement();
          Iterator<PsiFile> iterator = getFiles(psiDirectory);
          if (iterator.hasNext()) {
            updateTree();
          }
        }
      }
    }

    private void scheduleMarkFileAsDirtyAndUpdateTree(@Nullable PsiFile file) {
      VirtualFile virtualFile = file != null ? file.getVirtualFile() : null;
      if (virtualFile != null) {
        myCoroutineHelper.scheduleMarkFilesAsDirtyAndUpdateTree(List.of(virtualFile));
      }
    }
  }

  private final class MyFileStatusListener implements FileStatusListener {
    @Override
    public void fileStatusesChanged() {
      updateTree();
    }

    @Override
    public void fileStatusChanged(@NotNull VirtualFile virtualFile) {
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
      if (psiFile != null && canContainTodoItems(psiFile)) {
        updateTree();
      }
    }
  }
}
