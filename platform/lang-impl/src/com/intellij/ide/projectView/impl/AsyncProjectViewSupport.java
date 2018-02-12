/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.projectView.impl;

import com.intellij.ProjectTopics;
import com.intellij.ide.CopyPasteUtil;
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarksListener;
import com.intellij.ide.projectView.ProjectViewPsiTreeChangeListener;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUpdater;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FileStatusListener;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.RestoreSelectionListener;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static com.intellij.ide.util.treeView.TreeState.expand;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.jetbrains.concurrency.Promises.collectResults;

class AsyncProjectViewSupport {
  private static final Logger LOG = Logger.getInstance(AsyncProjectViewSupport.class);
  private final StructureTreeModel myStructureTreeModel;
  private final AsyncTreeModel myAsyncTreeModel;

  public AsyncProjectViewSupport(Disposable parent,
                                 Project project,
                                 JTree tree,
                                 AbstractTreeStructure structure,
                                 Comparator<NodeDescriptor> comparator) {
    myStructureTreeModel = new StructureTreeModel(true);
    myStructureTreeModel.setStructure(structure);
    myStructureTreeModel.setComparator(comparator);
    myAsyncTreeModel = new AsyncTreeModel(myStructureTreeModel, true);
    myAsyncTreeModel.setRootImmediately(myStructureTreeModel.getRootImmediately());
    setModel(tree, myAsyncTreeModel);
    Disposer.register(parent, myAsyncTreeModel);
    MessageBusConnection connection = project.getMessageBus().connect(parent);
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        updateAll(null);
      }
    });
    connection.subscribe(BookmarksListener.TOPIC, new BookmarksListener() {
      @Override
      public void bookmarkAdded(@NotNull Bookmark bookmark) {
        updateByFile(bookmark.getFile(), false);
      }

      @Override
      public void bookmarkRemoved(@NotNull Bookmark bookmark) {
        updateByFile(bookmark.getFile(), false);
      }

      @Override
      public void bookmarkChanged(@NotNull Bookmark bookmark) {
        updateByFile(bookmark.getFile(), false);
      }
    });
    PsiManager.getInstance(project).addPsiTreeChangeListener(new ProjectViewPsiTreeChangeListener(project) {
      @Override
      protected boolean isFlattenPackages() {
        return structure instanceof AbstractProjectTreeStructure && ((AbstractProjectTreeStructure)structure).isFlattenPackages();
      }

      @Override
      protected AbstractTreeUpdater getUpdater() {
        return null;
      }

      @Override
      protected DefaultMutableTreeNode getRootNode() {
        return null;
      }

      @Override
      protected void addSubtreeToUpdateByRoot() {
        updateAll(null);
      }

      @Override
      protected boolean addSubtreeToUpdateByElement(PsiElement element) {
        updateByElement(element, true);
        return true;
      }
    }, parent);
    FileStatusManager.getInstance(project).addFileStatusListener(new FileStatusListener() {
      @Override
      public void fileStatusesChanged() {
        updateAllPresentations();
      }

      @Override
      public void fileStatusChanged(@NotNull VirtualFile file) {
        updateByFile(file, false);
      }
    }, parent);
    CopyPasteManager.getInstance().addContentChangedListener(new CopyPasteUtil.DefaultCopyPasteListener(element -> updateByElement(element, true)), parent);
    WolfTheProblemSolver.getInstance(project).addProblemListener(new WolfTheProblemSolver.ProblemListener() {
      @Override
      public void problemsAppeared(@NotNull VirtualFile file) {
        updatePresentationsFromRootTo(file);
      }

      @Override
      public void problemsDisappeared(@NotNull VirtualFile file) {
        updatePresentationsFromRootTo(file);
      }
    }, parent);
  }

  public void setComparator(Comparator<NodeDescriptor> comparator) {
    myStructureTreeModel.setComparator(comparator);
  }

  public void select(JTree tree, Object object, VirtualFile file) {
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      object = node.getValue();
      LOG.debug("select AbstractTreeNode");
    }
    PsiElement element = object instanceof PsiElement ? (PsiElement)object : null;
    TreeVisitor visitor = createVisitor(element, file, null);
    if (visitor != null) {
      expand(tree, promise -> myAsyncTreeModel.accept(visitor).processed(path -> {
        if (selectPath(tree, path) || element == null || file == null || Registry.is("async.project.view.support.extra.select.disabled")) {
          promise.setResult(null);
        }
        else {
          // try to search the specified file instead of element,
          // because Kotlin files cannot represent containing functions
          myAsyncTreeModel.accept(createVisitor(null, file, null)).processed(path2 -> {
            selectPath(tree, path2);
            promise.setResult(null);
          });
        }
      }));
    }
  }

  private static boolean selectPath(@NotNull JTree tree, TreePath path) {
    if (path == null) return false;
    tree.expandPath(path); // request to expand found path
    TreeUtil.selectPath(tree, path); // select and scroll to center
    return true;
  }

  public void updateAll(Runnable onDone) {
    LOG.debug(new RuntimeException("reload a whole tree"));
    myStructureTreeModel.invalidate(onDone == null ? null : () -> myAsyncTreeModel.onValidThread(onDone));
  }

  public void update(@NotNull TreePath path, boolean structure) {
    myStructureTreeModel.invalidate(path, structure);
  }

  public void update(@NotNull List<TreePath> list, boolean structure) {
    for (TreePath path : list) update(path, structure);
  }

  public void updateByFile(@NotNull VirtualFile file, boolean structure) {
    LOG.debug(structure ? "updateChildrenByFile: " : "updatePresentationByFile: ", file);
    update(null, file, structure);
  }

  public void updateByElement(@NotNull PsiElement element, boolean structure) {
    LOG.debug(structure ? "updateChildrenByElement: " : "updatePresentationByElement: ", element);
    update(element, null, structure);
  }

  private void update(PsiElement element, VirtualFile file, boolean structure) {
    SmartList<TreePath> list = new SmartList<>();
    acceptAndUpdate(createVisitor(element, file, path -> !list.add(path)), list, structure);
  }

  private void acceptAndUpdate(TreeVisitor visitor, List<TreePath> list, boolean structure) {
    if (visitor != null) myAsyncTreeModel.accept(visitor, false).done(path -> update(list, structure));
  }

  private void updatePresentationsFromRootTo(@NotNull VirtualFile file) {
    SmartList<TreePath> list = new SmartList<>();
    acceptAndUpdate(new ProjectViewFileVisitor(file, null) {
      @NotNull
      @Override
      protected Action visit(@NotNull TreePath path, @NotNull AbstractTreeNode node, @NotNull VirtualFile element) {
        Action action = super.visit(path, node, element);
        if (action != Action.SKIP_CHILDREN) list.add(path);
        return action;
      }
    }, list, false);
  }

  private void updateAllPresentations() {
    SmartList<TreePath> list = new SmartList<>();
    acceptAndUpdate(new TreeVisitor() {
      @NotNull
      @Override
      public Action visit(@NotNull TreePath path) {
        list.add(path);
        return Action.CONTINUE;
      }
    }, list, false);
  }

  void accept(List<TreeVisitor> visitors, Consumer<List<TreePath>> consumer) {
    if (visitors != null && !visitors.isEmpty()) {
      if (1 == visitors.size()) {
        myAsyncTreeModel.accept(visitors.get(0)).done(path -> {
          if (path != null) consumer.consume(singletonList(path));
        });
      }
      else {
        List<Promise<TreePath>> promises = visitors.stream().map(visitor -> myAsyncTreeModel.accept(visitor)).collect(toList());
        collectResults(promises, true).done(list -> {
          if (list != null && !list.isEmpty()) consumer.consume(list);
        });
      }
    }
  }

  static TreeVisitor createVisitor(Object object) {
    if (object instanceof AbstractTreeNode) {
      AbstractTreeNode node = (AbstractTreeNode)object;
      object = node.getValue();
    }
    return object instanceof PsiElement
           ? createVisitor((PsiElement)object, null, null)
           : null;
  }

  static List<TreeVisitor> createVisitors(Iterable<Object> iterable) {
    if (iterable == null) return Collections.emptyList();
    List<TreeVisitor> visitors = new SmartList<>();
    for (Object object : iterable) {
      TreeVisitor visitor = createVisitor(object);
      if (visitor != null) visitors.add(visitor);
    }
    return visitors;
  }

  private static TreeVisitor createVisitor(PsiElement element, VirtualFile file, Predicate<TreePath> predicate) {
    if (element != null && element.isValid()) return new ProjectViewNodeVisitor(element, file, predicate);
    if (file != null) return new ProjectViewFileVisitor(file, predicate);
    LOG.warn(element != null ? "element invalidated: " + element : "cannot create visitor without element and/or file");
    return null;
  }

  private static void setModel(@NotNull JTree tree, @NotNull AsyncTreeModel model) {
    RestoreSelectionListener listener = new RestoreSelectionListener();
    tree.addTreeSelectionListener(listener);
    tree.setModel(model);
    Disposer.register(model, () -> {
      tree.setModel(null);
      tree.removeTreeSelectionListener(listener);
    });
  }
}