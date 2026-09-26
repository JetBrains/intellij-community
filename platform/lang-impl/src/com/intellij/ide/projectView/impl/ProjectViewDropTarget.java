// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDNativeTarget;
import com.intellij.ide.dnd.DroppedFileCopy;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.ide.projectView.impl.nodes.DropTargetNode;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringActionHandlerFactory;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.ui.awt.RelativeRectangle;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.dnd.DnDConstants;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.util.ui.tree.TreeUtil.getLastUserObject;

/**
 * @author Anna
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public abstract class ProjectViewDropTarget implements DnDNativeTarget {
  private final JTree myTree;
  private final Project myProject;

  public ProjectViewDropTarget(JTree tree, Project project) {
    myTree = tree;
    myProject = project;
  }

  @Override
  public boolean update(DnDEvent event) {
    event.setDropPossible(false, "");

    Point point = event.getPoint();
    if (point == null) return false;

    TreePath target = myTree.getClosestPathForLocation(point.x, point.y);
    if (target == null) return false;

    Rectangle bounds = myTree.getPathBounds(target);
    if (bounds == null || bounds.y > point.y || point.y >= bounds.y + bounds.height) return false;

    DropHandler handler = getDropHandler(event);
    if (handler == null) return false;

    TreePath[] sources = getSourcePaths(event.getAttachedObject());
    if (sources != null) {
      if (ArrayUtilRt.find(sources, target) != -1) return false;//TODO???? nodes
    }
    else if (!isFileDropPossible(event)) {
      return false;
    }
    event.setHighlighting(new RelativeRectangle(myTree, bounds), DnDEvent.DropTargetHighlightingType.RECTANGLE);
    event.setDropPossible(true);
    return false;
  }

  @Override
  public void drop(DnDEvent event) {
    Point point = event.getPoint();
    if (point == null) return;

    TreePath target = myTree.getClosestPathForLocation(point.x, point.y);
    if (target == null) return;

    Rectangle bounds = myTree.getPathBounds(target);
    if (bounds == null || bounds.y > point.y || point.y >= bounds.y + bounds.height) return;

    doDrop(event, target);
  }

  @ApiStatus.Internal
  public void doDrop(@NotNull DnDEvent event, @NotNull TreePath target) {
    DropHandler handler = getDropHandler(event);
    if (handler == null) return;

    final Object attached = event.getAttachedObject();
    TreePath[] sources = getSourcePaths(event.getAttachedObject());

    if (sources == null) {
      List<Path> paths = FileCopyPasteUtil.getPathListFromAttachedObject(attached);
      if (!paths.isEmpty()) {
        doDropPaths(paths, attached, target, handler);
      }
      else if (FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
        doDropFiles(attached, target, handler);
      }
    }
    else {
      doValidDrop(sources, target, handler);
    }
  }

  /**
   * Returns true when the drop source has a file to give.
   * <p>
   * A source can report a file as a NIO path only, because a file in another environment has no
   * local {@link File} form.
   */
  private static boolean isFileDropPossible(@NotNull DnDEvent event) {
    return !FileCopyPasteUtil.getPathListFromAttachedObject(event.getAttachedObject()).isEmpty()
           || FileCopyPasteUtil.isFileListFlavorAvailable(event);
  }

  /**
   * Drops the files that the source reports as NIO paths.
   * <p>
   * A source inside the IDE, for example the universal file chooser, can drag a file from another
   * environment such as a Docker container or WSL. Such a file has no local {@link File} form, and
   * a move refactoring cannot cross a file system, so the drop copies the file into the target
   * directory with the EEL API. A file in the same environment as the target keeps the standard
   * move or copy behavior.
   */
  private void doDropPaths(@NotNull List<Path> paths, Object attached, @NotNull TreePath target, @NotNull DropHandler handler) {
    record CopyContext(@Nullable Path destination) { }
    ReadAction.nonBlocking(() -> new CopyContext(findCopyDestination(paths, target)))
      .expireWith(myProject)
      .finishOnUiThread(
        ModalityState.defaultModalityState(),
        context -> {
          if (context.destination != null) {
            DroppedFileCopy.copyInBackground(myProject, context.destination, paths);
          }
          else {
            doDropFiles(attached, target, handler);
          }
        })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static void doDropFiles(Object attached, @NotNull TreePath target, @NotNull DropHandler handler) {
    List<File> fileList = FileCopyPasteUtil.getFileListFromAttachedObject(attached);
    if (!fileList.isEmpty()) {
      handler.doDropFiles(fileList, target);
    }
  }

  /**
   * Returns the directory that must hold the copy of {@code paths}, or null when the drop needs no copy.
   */
  private @Nullable Path findCopyDestination(@NotNull List<Path> paths, @NotNull TreePath target) {
    PsiDirectory directory = getTargetDirectory(getPsiElement(target));
    if (directory == null) return null;
    Path destination = VirtualFileUtil.toNioPathOrNull(directory.getVirtualFile());
    if (destination == null) return null;
    return DroppedFileCopy.isAcrossEnvironments(paths, destination) ? destination : null;
  }

  /** Returns the directory that holds {@code element}, or {@code element} itself when it is a directory. */
  private static @Nullable PsiDirectory getTargetDirectory(@Nullable PsiElement element) {
    if (element instanceof PsiDirectoryContainer directoryContainer) {
      PsiDirectory[] psiDirectories = directoryContainer.getDirectories();
      return psiDirectories.length != 0 ? psiDirectories[0] : null;
    }
    if (element instanceof PsiDirectory psiDirectory) {
      return psiDirectory;
    }
    if (element == null) return null;
    PsiFile containingFile = element.getContainingFile();
    LOG.assertTrue(containingFile != null, element);
    return containingFile.getContainingDirectory();
  }

  private static TreePath @Nullable [] getSourcePaths(Object transferData) {
    TransferableWrapper wrapper = transferData instanceof TransferableWrapper ? (TransferableWrapper)transferData : null;
    return wrapper == null ? null : wrapper.getTreePaths();
  }

  private void doValidDrop(TreePath @NotNull [] sources, @NotNull TreePath target, @NotNull DropHandler handler) {
    record ValidDropContext(TreePath @Nullable [] sources, @Nullable TreePath target) { }
    ReadAction.nonBlocking(() -> {
        TreePath validTarget = getValidTarget(sources, target, handler);
        TreePath[] validSources = null;
        if (validTarget != null) {
          validSources = removeRedundant(sources, validTarget, handler);
        }
        return new ValidDropContext(validSources, validTarget);
      })
      .expireWith(myProject)
      .finishOnUiThread(
        ModalityState.defaultModalityState(),
        context -> {
          if (context.sources != null && context.sources.length != 0 && context.target != null) {
            handler.doDrop(context.sources, context.target);
          }
        })
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  private static @Nullable TreePath getValidTarget(TreePath @NotNull [] sources, @NotNull TreePath target, @NotNull DropHandler handler) {
    while (target != null) {
      if (handler.isValidTarget(sources, target)) return target;
      if (!handler.shouldDelegateToParent(sources, target)) break;
      target = target.getParentPath();
    }
    return null;
  }

  private static TreePath @NotNull [] removeRedundant(TreePath @NotNull [] sources, @NotNull TreePath target, @NotNull DropHandler dropHandler) {
    return Stream.of(sources).filter(source -> !dropHandler.isDropRedundant(source, target)).toArray(TreePath[]::new);
  }

  private DropHandler getDropHandler(DnDEvent event) {
    if (event == null) return null;
    DnDAction action = event.getAction();
    if (action == null) return null;
    int id = action.getActionId();
    if (id == DnDConstants.ACTION_COPY) return createCopyHandler();
    if (id != DnDConstants.ACTION_COPY_OR_MOVE && id != DnDConstants.ACTION_MOVE) return null;
    return createMoveHandler();
  }

  public DropHandler createCopyHandler() {
    return new CopyDropHandler();
  }

  public DropHandler createMoveHandler() {
    return new MoveDropHandler();
  }

  public interface DropHandler {
    boolean isValidSource(TreePath @NotNull [] sources, @NotNull TreePath target);

    boolean isValidTarget(TreePath @NotNull [] sources, @NotNull TreePath target);

    boolean shouldDelegateToParent(TreePath @NotNull [] sources, @NotNull TreePath target);

    boolean isDropRedundant(@NotNull TreePath source, @NotNull TreePath target);

    boolean isDropRedundant(@NotNull PsiElement source, @NotNull PsiElement target);

    void doDrop(TreePath @NotNull [] sources, @NotNull TreePath target);

    void doDrop(@NotNull DropContext context);

    void doDropFiles(List<? extends File> files, @NotNull TreePath target);
  }

  protected abstract @Nullable PsiElement getPsiElement(@NotNull TreePath path);

  protected abstract @Nullable Module getModule(@NotNull PsiElement element);

  abstract class MoveCopyDropHandler implements DropHandler {
    @Override
    public boolean isValidSource(TreePath @NotNull [] sources, @NotNull TreePath target) {
      return canDrop(sources, target);
    }

    @Override
    public boolean isValidTarget(TreePath @NotNull [] sources, @NotNull TreePath target) {
      return canDrop(sources, target);
    }

    protected abstract boolean canDrop(TreePath @NotNull [] sources, @NotNull TreePath target);

    protected PsiElement @Nullable [] getPsiElements(@NotNull DropContext dropContext) {
      PsiElement[] elements = dropContext.sourceElements;
      if (elements == null || elements.length == 0) {
        elements = getDataContextPsiElements();
      }
      return elements;
    }

    protected PsiElement @NotNull [] getPsiElements(TreePath @NotNull [] paths) {
      PsiElement[] psiElements = getNonDataContextPsiElements(paths);
      return psiElements != null ? psiElements : getDataContextPsiElements();
    }

    protected PsiElement @Nullable [] getNonDataContextPsiElements(TreePath @NotNull [] paths) {
      List<PsiElement> psiElements = new ArrayList<>(paths.length);
      for (TreePath path : paths) {
        PsiElement psiElement = getPsiElement(path);
        if (psiElement != null) {
          psiElements.add(psiElement);
        }
      }
      if (!psiElements.isEmpty()) {
        return PsiUtilCore.toPsiElementArray(psiElements);
      }
      return null;
    }

    protected PsiElement @NotNull [] getDataContextPsiElements() {
      return getDataContextPsiElements(DataManager.getInstance().getDataContext(myTree));
    }

    protected PsiElement @NotNull [] getDataContextPsiElements(@NotNull DataContext dataContext) {
      return BaseRefactoringAction.getPsiElementArray(dataContext);
    }
  }

  protected PsiFileSystemItem @Nullable [] getPsiFiles(@Nullable List<? extends File> fileList) {
    return psiFilesFromVirtualFiles(getVirtualFiles(fileList));
  }

  private static @Nullable List<VirtualFile> getVirtualFiles(@Nullable List<? extends File> fileList) {
    if (fileList == null) return null;
    List<VirtualFile> virtualFiles = new ArrayList<>();
    for (File file : fileList) {
      final VirtualFile vFile = StandardFileSystems.local().refreshAndFindFileByPath(file.getAbsolutePath());
      if (vFile != null) {
        virtualFiles.add(vFile);
      }
    }
    return virtualFiles;
  }

  private PsiFileSystemItem @Nullable [] psiFilesFromVirtualFiles(@Nullable List<? extends VirtualFile> fileList) {
    if (fileList == null) return null;
    List<PsiFileSystemItem> sourceFiles = new ArrayList<>();
    for (VirtualFile vFile : fileList) {
      PsiFileSystemItem psiFile = PsiUtilCore.findFileSystemItem(myProject, vFile);
      if (psiFile != null) {
        sourceFiles.add(psiFile);
      }
    }
    return sourceFiles.toArray(new PsiFileSystemItem[0]);
  }

  private final class MoveDropHandler extends MoveCopyDropHandler {
    @Override
    protected boolean canDrop(TreePath @NotNull [] sources, @NotNull TreePath target) {
      DropTargetNode node = getLastUserObject(DropTargetNode.class, target);
      if (node != null && node.canDrop(sources)) return true;

      PsiElement[] sourceElements = getPsiElements(sources);
      PsiElement targetElement = getPsiElement(target);
      return sourceElements.length == 0 ||
             targetElement != null && MoveHandler.canMove(sourceElements, targetElement);
    }

    @Override
    public void doDrop(TreePath @NotNull [] sources, @NotNull TreePath target) {
      DropTargetNode node = getLastUserObject(DropTargetNode.class, target);
      if (node != null && node.canDrop(sources)) {
        node.drop(sources, DataManager.getInstance().getDataContext(myTree));
      }
      else {
        DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
        ReadAction.nonBlocking(() -> getDropContext(getDataContextPsiElements(dataContext), target))
          .finishOnUiThread(ModalityState.defaultModalityState(), context -> doDrop(context, false))
          .submit(AppExecutorUtil.getAppExecutorService());
      }
    }

    @Override
    public void doDrop(@NotNull DropContext dropContext) {
      doDrop(dropContext, false);
    }

    private void doDrop(@NotNull DropContext dropContext, boolean externalDrop) {
      @Nullable PsiElement target = dropContext.targetElement();
      PsiElement @Nullable [] sources = getPsiElements(dropContext);
      if (target == null || sources == null) return;

      if (!myProject.isInitialized()) {
        Messages.showMessageDialog(myProject, LangBundle.message("dialog.message.move.refactoring.available"),
                                   LangBundle.message("dialog.title.project.initialization"), null);
        return;
      }

      Module module = dropContext.targetModule();
      final DataContext dataContext = DataManager.getInstance().getDataContext(myTree);
      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      if (!target.isValid()) return;
      for (PsiElement element : sources) {
        if (!element.isValid()) return;
      }

      DataContext context = CustomizedDataContext.withSnapshot(externalDrop ? DataContext.EMPTY_CONTEXT : dataContext, sink -> {
        sink.set(LangDataKeys.TARGET_MODULE, module);
        sink.set(LangDataKeys.TARGET_PSI_ELEMENT, target);
      });
      getActionHandler().invoke(myProject, sources, context);
    }

    private static RefactoringActionHandler getActionHandler() {
      return RefactoringActionHandlerFactory.getInstance().createMoveHandler();
    }

    @Override
    public boolean isDropRedundant(@NotNull TreePath source, @NotNull TreePath target) {
      return target.equals(source.getParentPath()) || MoveHandler.isMoveRedundant(getPsiElement(source), getPsiElement(target));
    }

    @Override
    public boolean isDropRedundant(@NotNull PsiElement source, @NotNull PsiElement target) {
      return target == source.getParent() || MoveHandler.isMoveRedundant(source, target);
    }

    @Override
    public boolean shouldDelegateToParent(TreePath @NotNull [] sources, @NotNull TreePath target) {
      PsiElement psiElement = getPsiElement(target);
      return !MoveHandler.isValidTarget(psiElement, getPsiElements(sources));
    }

    @Override
    public void doDropFiles(List<? extends File> files, @NotNull TreePath target) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        var virtualFiles = getVirtualFiles(files);
        ReadAction.nonBlocking(() -> getDropContext(psiFilesFromVirtualFiles(virtualFiles), target))
          .finishOnUiThread(ModalityState.defaultModalityState(), dropContext -> {
            DropTargetNode node = getLastUserObject(DropTargetNode.class, target);
            if (node != null) {
              node.dropExternalFiles((PsiFileSystemItem[])getPsiElements(dropContext), DataManager.getInstance().getDataContext(myTree));
            }
            else {
              doDrop(dropContext, true);
            }
          })
          .submit(AppExecutorUtil.getAppExecutorService());
      });
    }

    private @NotNull DropContext getDropContext(PsiElement @Nullable [] sourceElements, @NotNull TreePath target) {
      PsiElement targetElement = getPsiElement(target);
      Module targetModule = targetElement == null || !myProject.isInitialized() ? null : getModule(targetElement);
      return new DropContext(sourceElements, targetElement, targetModule);
    }
  }

  private final class CopyDropHandler extends MoveCopyDropHandler {
    @Override
    protected boolean canDrop(TreePath @NotNull [] sources, @NotNull TreePath target) {
      PsiElement[] sourceElements = getPsiElements(sources);
      PsiElement targetElement = getPsiElement(target);
      if (targetElement == null) return false;
      PsiFile containingFile = targetElement.getContainingFile();
      boolean isTargetAcceptable = targetElement instanceof PsiDirectoryContainer ||
                                   targetElement instanceof PsiDirectory ||
                                   containingFile != null && containingFile.getContainingDirectory() != null;
      return isTargetAcceptable && CopyHandler.canCopy(sourceElements);
    }

    @Override
    public void doDrop(TreePath @NotNull [] sources, @NotNull TreePath target) {
      ReadAction.nonBlocking(() -> new DropContext(getNonDataContextPsiElements(sources), getPsiElement(target), null))
        .finishOnUiThread(ModalityState.defaultModalityState(), context -> doDrop(context))
        .submit(AppExecutorUtil.getAppExecutorService());
    }

    @Override
    public void doDrop(@NotNull DropContext context) {
      final PsiElement targetElement = context.targetElement();
      final PsiElement @Nullable [] sources = getPsiElements(context);
      if (targetElement == null || sources == null) return;

      CopyHandler.doCopy(sources, getTargetDirectory(targetElement));
    }

    @Override
    public boolean isDropRedundant(@NotNull TreePath source, @NotNull TreePath target) {
      return false;
    }

    @Override
    public boolean isDropRedundant(@NotNull PsiElement source, @NotNull PsiElement target) {
      return false;
    }

    @Override
    public boolean shouldDelegateToParent(TreePath @NotNull [] sources, @NotNull TreePath target) {
      PsiElement psiElement = getPsiElement(target);
      return !(psiElement instanceof PsiDirectoryContainer) && !(psiElement instanceof PsiDirectory);
    }

    @Override
    public void doDropFiles(List<? extends File> files, @NotNull TreePath target) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        var virtualFiles = getVirtualFiles(files);
        ReadAction.nonBlocking(() -> new DropContext(psiFilesFromVirtualFiles(virtualFiles), getPsiElement(target), null))
          .finishOnUiThread(ModalityState.defaultModalityState(), context -> doDrop(context))
          .submit(AppExecutorUtil.getAppExecutorService());
      });
    }
  }

  public record DropContext(PsiElement @Nullable [] sourceElements, @Nullable PsiElement targetElement, @Nullable Module targetModule) { }
}
