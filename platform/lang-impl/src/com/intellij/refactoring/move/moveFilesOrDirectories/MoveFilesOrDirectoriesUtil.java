// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.ide.util.EditorHelper;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.paths.PsiDynaReference;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.move.FileReferenceContextUtil;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.refactoring.move.moveClassesOrPackages.MovedFileProvider;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.IoErrorText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public final class MoveFilesOrDirectoriesUtil {
  private MoveFilesOrDirectoriesUtil() { }

  /**
   * Moves the specified directory to the specified parent directory. Does not process non-code usages!
   *
   * @param aDirectory          the directory to move.
   * @param destDirectory the directory to move {@code dir} into.
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  public static void doMoveDirectory(@NotNull PsiDirectory aDirectory, @NotNull PsiDirectory destDirectory) throws IncorrectOperationException {
    var manager = aDirectory.getManager();
    doJustMoveDirectory(aDirectory, destDirectory, manager);
    DumbService.getInstance(manager.getProject()).completeJustSubmittedTasks();
  }

  private static void doJustMoveDirectory(PsiDirectory aDirectory, PsiDirectory destDirectory, @Nullable Object requestor) {
    checkMove(aDirectory, destDirectory);
    try {
      aDirectory.getVirtualFile().move(requestor, destDirectory.getVirtualFile());
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e);
    }
  }

  /**
   * Moves the specified file to the specified directory. Does not process non-code usages!
   * The file may be invalidated, need to be refreshed before use, like {@code newDirectory.findFile(file.getName())}.
   *
   * @param file         the file to move.
   * @param newDirectory the directory to move the file into.
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  public static void doMoveFile(@NotNull PsiFile file, @NotNull PsiDirectory newDirectory) throws IncorrectOperationException {
    // the class is already there, this is true when multiple classes are defined in the same file
    if (!newDirectory.equals(file.getContainingDirectory())) {
      // do actual move
      checkMove(file, newDirectory);

      var vFile = file.getViewProvider().getVirtualFile();

      try {
        vFile.move(file.getManager(), newDirectory.getVirtualFile());
      }
      catch (IOException e) {
        throw new IncorrectOperationException(e);
      }
    }
  }

  /**
   * @param elements should contain PsiDirectories or PsiFiles only
   */
  public static void doMove(
    @NotNull Project project,
    PsiElement @NotNull [] elements,
    PsiElement @NotNull [] targetElement,
    @Nullable MoveCallback moveCallback
  ) {
    doMove(project, elements, targetElement, moveCallback, null);
  }

  /**
   * @param elements should contain PsiDirectories or PsiFiles only if adjustElements == null
   */
  public static void doMove(
    @NotNull Project project,
    PsiElement @NotNull [] elements,
    PsiElement @NotNull [] targetElement,
    @Nullable MoveCallback moveCallback,
    @Nullable Function<? super PsiElement[], ? extends PsiElement[]> adjustElements
  ) {
    if (adjustElements == null) {
      for (var element : elements) {
        if (!(element instanceof PsiFile) && !(element instanceof PsiDirectory)) {
          throw new IllegalArgumentException("unexpected element type: " + element);
        }
      }
    }

    var targetDirectory = resolveToDirectory(project, targetElement[0]);
    if (targetElement[0] != null && targetDirectory == null) return;

    var adjustedElements = adjustElements != null ? adjustElements.apply(elements) : elements;

    var initialTargetDirectory = getInitialTargetDirectory(targetDirectory, elements);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      Objects.requireNonNull(initialTargetDirectory, "It is null! The target directory, it is null!");
      doMove(project, elements, adjustedElements, initialTargetDirectory, moveCallback, EmptyRunnable.INSTANCE);
    }
    else {
      new MoveFilesOrDirectoriesDialog(project, adjustedElements, initialTargetDirectory) {
        @Override
        protected void performMove(@NotNull PsiDirectory targetDirectory) {
          var doneCallback = (Runnable)this::closeOKAction;
          doMove(project, elements, adjustedElements, targetDirectory, moveCallback, doneCallback);
        }
      }.show();
    }
  }

  private static void doMove(
    Project project,
    PsiElement[] elements,
    PsiElement[] adjustedElements,
    PsiDirectory targetDirectory,
    @Nullable MoveCallback moveCallback,
    Runnable doneCallback
  ) {
    CommandProcessor.getInstance().executeCommand(project, () -> {
      Collection<PsiElement> toCheck = new SmartList<>(targetDirectory);
      for (var e : adjustedElements) {
        toCheck.add(e instanceof PsiFileSystemItem && e.getParent() != null ? e.getParent() : e);
      }
      if (!CommonRefactoringUtil.checkReadOnlyStatus(project, toCheck, false)) {
        return;
      }

      try {
        var choice = elements.length > 1 || elements[0] instanceof PsiDirectory ? new int[]{-1} : null;
        var els = new ArrayList<PsiElement>();
        for (var psiElement : adjustedElements) {
          if (psiElement instanceof PsiFile file) {
            if (CopyFilesOrDirectoriesHandler.checkFileExist(targetDirectory, choice, file, file.getName(), RefactoringBundle.message("command.name.move"))) {
              continue;
            }
          }
          checkMove(psiElement, targetDirectory);
          els.add(psiElement);
        }

        if (els.isEmpty()) {
          doneCallback.run();
        }
        else if (DumbService.isDumb(project)) {
          ApplicationManager.getApplication().invokeAndWait(doneCallback);
          var filePointers = new HashSet<SmartPsiElementPointer<PsiFile>>();
          if (MoveFilesOrDirectoriesDialog.isOpenInEditorProperty()) {
            var manager = SmartPointerManager.getInstance(project);
            for (var element : elements) {
              addFilePointers(filePointers, element, manager);
            }
          }
          WriteCommandAction.runWriteCommandAction(project, RefactoringBundle.message("move.title"), null, () -> {
            try {
              for (var element : elements) {
                if (element instanceof PsiDirectory) {
                  doJustMoveDirectory((PsiDirectory)element, targetDirectory, MoveFilesOrDirectoriesUtil.class);
                }
                else if (element instanceof PsiFile movedFile) {
                  var moving = targetDirectory.findFile(movedFile.getName());
                  if (moving == null) {
                    doMoveFile(movedFile, targetDirectory);
                  }
                }
              }
            }
            finally {
              if (moveCallback != null) {
                moveCallback.refactoringCompleted();
              }
              if (MoveFilesOrDirectoriesDialog.isOpenInEditorProperty()) {
                ApplicationManager.getApplication().invokeLater(
                  () -> EditorHelper.openFilesInEditor(
                    filePointers.stream()
                      .map(SmartPsiElementPointer::getContainingFile).filter(file -> file != null && file.isValid())
                      .toArray(PsiFile[]::new)),
                  project.getDisposed()
                );
              }
            }
          });
        }
        else {
          new MoveFilesOrDirectoriesProcessor(
            project, els.toArray(PsiElement.EMPTY_ARRAY), targetDirectory,
            RefactoringSettings.getInstance().MOVE_SEARCH_FOR_REFERENCES_FOR_FILE,
            false, false, moveCallback, doneCallback
          ).run();
        }
      }
      catch (IncorrectOperationException e) {
        var cause = e.getCause();
        if (cause == null) throw e;
        var message = IoErrorText.message(cause);
        CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("error.title"), message, "refactoring.moveFile", project);
      }
    }, MoveHandler.getRefactoringName(), null);
  }

  private static void addFilePointers(Set<SmartPsiElementPointer<PsiFile>> pointers, PsiElement element, SmartPointerManager manager) {
    if (element instanceof PsiFile) {
      pointers.add(manager.createSmartPsiElementPointer((PsiFile)element, (PsiFile)element));
    }
    else if (element instanceof PsiDirectory) {
      for (var child : element.getChildren()) {
        addFilePointers(pointers, child, manager);
      }
    }
  }

  public static @Nullable PsiDirectory resolveToDirectory(@NotNull Project project, PsiElement element) {
    if (!(element instanceof PsiDirectoryContainer container)) {
      return (PsiDirectory)element;
    }

    var directories = container.getDirectories();
    return switch (directories.length) {
      case 0 -> null;
      case 1 -> directories[0];
      default -> DirectoryChooserUtil.chooseDirectory(directories, directories[0], project, new HashMap<>());
    };
  }

  private static @Nullable PsiDirectory getCommonDirectory(PsiElement @NotNull [] movedElements) {
    var commonDirectory = (PsiDirectory)null;

    for (var movedElement : movedElements) {
      PsiDirectory containingDirectory;
      if (movedElement instanceof PsiDirectory directory) {
        containingDirectory = directory.getParentDirectory();
      }
      else {
        var containingFile = movedElement.getContainingFile();
        containingDirectory = containingFile == null ? null : containingFile.getContainingDirectory();
      }

      if (containingDirectory != null) {
        if (commonDirectory == null) {
          commonDirectory = containingDirectory;
        }
        else {
          if (commonDirectory != containingDirectory) {
            return null;
          }
        }
      }
    }
    return commonDirectory;
  }

  public static @Nullable PsiDirectory getInitialTargetDirectory(@Nullable PsiDirectory initialTargetElement, PsiElement[] movedElements) {
    var initialTargetDirectory = initialTargetElement;
    if (initialTargetDirectory == null) {
      if (movedElements != null) {
        var commonDirectory = getCommonDirectory(movedElements);
        if (commonDirectory != null) {
          initialTargetDirectory = commonDirectory;
        }
        else {
          initialTargetDirectory = getContainerDirectory(movedElements[0]);
        }
      }
    }
    return initialTargetDirectory;
  }

  private static @Nullable PsiDirectory getContainerDirectory(PsiElement psiElement) {
    if (psiElement instanceof PsiDirectory) {
      return (PsiDirectory)psiElement;
    }
    else if (psiElement != null) {
      var containingFile = psiElement.getContainingFile();
      if (containingFile != null) {
        return containingFile.getContainingDirectory();
      }
    }

    return null;
  }

  /**
   * Checks if it is possible to move the specified PSI element under the specified container,
   * and throws an exception if the move is not possible. Does not actually modify anything.
   *
   * @param element      the element to check the move possibility.
   * @param newContainer the target container element to move into.
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  public static void checkMove(@NotNull PsiElement element, @NotNull PsiElement newContainer) throws IncorrectOperationException {
    if (element instanceof PsiDirectoryContainer) {
      var dirs = ((PsiDirectoryContainer)element).getDirectories();
      if (dirs.length == 0) {
        throw new IncorrectOperationException();
      }
      else if (dirs.length > 1) {
        throw new IncorrectOperationException("Moving of packages represented by more than one physical directory is not supported.");
      }
      checkMove(dirs[0], newContainer);
      return;
    }

    //element.checkDelete(); //move != delete + add
    newContainer.checkAdd(element);
    checkIfMoveIntoSelf(element, newContainer);
  }

  public static void checkIfMoveIntoSelf(PsiElement element, PsiElement newContainer) throws IncorrectOperationException {
    var container = newContainer;
    while (container != null) {
      if (container == element) {
        if (element instanceof PsiDirectory) {
          if (element == newContainer) {
            throw new IncorrectOperationException("Cannot place directory into itself.");
          }
          else {
            throw new IncorrectOperationException("Cannot place directory into its subdirectory.");
          }
        }
        else {
          throw new IncorrectOperationException();
        }
      }
      container = container.getParent();
    }
  }

  /**
   * Searches for usages of the elements to move in the target directory.
   */
  @ApiStatus.Internal
  public static @NotNull UsagesContext findUsages(@NotNull Project project,
                                                  @NotNull PsiElement @NotNull [] elementsToMove,
                                                  @NotNull PsiDirectory target,
                                                  boolean isSearchForReference,
                                                  boolean isSearchForComments,
                                                  boolean isSearchForNonJavaFiles) {
    UsagesContext context = new UsagesContext(
      new ArrayList<>(),
      new HashMap<>()
    );
    for (PsiElement element : elementsToMove) {
      if (isSearchForReference) {
        for (PsiReference reference : ReferencesSearch.search(element, GlobalSearchScope.projectScope(project)).asIterable()) {
          context.allUsages().add(new MovedFileOrDirectoryUsageInfo(reference, element));
        }
      }
      findElementUsages(context, element, target, isSearchForReference, isSearchForComments, isSearchForNonJavaFiles);
    }
    return context;
  }

  private static void findElementUsages(UsagesContext context,
                                        @NotNull PsiElement element,
                                        @NotNull PsiDirectory target,
                                        boolean isSearchForReference,
                                        boolean isSearchForComments,
                                        boolean isSearchForNonJavaFiles) {
    if (!isSearchForReference) {
      return;
    }
    if (element instanceof PsiFile) {
      final List<UsageInfo> usages = MoveFileHandler.forElement((PsiFile)element)
        .findUsages((PsiFile)element, target, isSearchForComments, isSearchForNonJavaFiles);
      if (usages != null) {
        context.allUsages().addAll(usages);
        context.classifiedUsages().put((PsiFile)element, usages);
      }
    }
    else if (element instanceof PsiDirectory) {
      for (PsiElement childElement : element.getChildren()) {
        findElementUsages(context, childElement, target, isSearchForReference, isSearchForComments, isSearchForNonJavaFiles);
      }
    }
  }

  /**
   * Represents usages collected in {@link findUsages}.
   * @param allUsages all usages that participate in conflict detection
   * @param classifiedUsages code usages grouped by the file that contains them
   */
  @ApiStatus.Internal
  public record UsagesContext(@NotNull List<UsageInfo> allUsages, @NotNull Map<PsiFile, @Unmodifiable List<UsageInfo>> classifiedUsages) {
  }

  /**
   * Result of {@link #moveElements}.
   *
   * @param movedFilePointers    pointers to all the moved files, including the files from the moved directories
   * @param movedElementPointers pointers to the moved elements, in the same order as the elements passed to {@link #moveElements};
   *                             an element which became invalid during the move is represented by {@code null}
   * @param oldToNewMap          mapping from the elements before the move to the elements after the move
   */
  @ApiStatus.Internal
  public record MoveElementsResult(@NotNull List<@NotNull SmartPsiElementPointer<PsiFile>> movedFilePointers,
                                   @NotNull List<@Nullable SmartPsiElementPointer<PsiElement>> movedElementPointers,
                                   @NotNull Map<PsiElement, PsiElement> oldToNewMap) {
  }

  /**
   * Performs the actual move of {@code elementsToMove} into {@code newParent} with {@link #doMoveFile} and {@link #doMoveDirectory},
   * and updates the references from the moved files to the outer files.
   * <p>
   * Does not modify the input {@code elementsToMove}; the moved elements are returned in the resulting {@link MoveElementsResult}.
   *
   * @param progressIndicator     indicator to report the progress of the move to
   * @param isSearchForReferences whether the references of the moved files have to be preserved across the move
   */
  @ApiStatus.Internal
  public static @NotNull MoveElementsResult moveElements(@NotNull Project project,
                                                         @NotNull PsiElement @NotNull [] elementsToMove,
                                                         @NotNull PsiDirectory newParent,
                                                         @NotNull ProgressIndicator progressIndicator,
                                                         boolean isSearchForReferences) {
    boolean showProgression = elementsToMove.length > 1; // only show progression when moving multiple elements
    progressIndicator.setIndeterminate(!showProgression);
    if (showProgression) progressIndicator.setFraction(0.0);

    //keep hard references to PSI and AST to prevent collecting the object between saving references and restoring
    Map<PsiFile, FileASTNode> movingFiles = new HashMap<>();

    if (isSearchForReferences) {
      for (PsiElement element : elementsToMove) {
        if (element instanceof PsiDirectory) {
          encodeDirectoryFiles(element, movingFiles);
        }
        else if (element instanceof PsiFile file) {
          movingFiles.put(file, file.getNode());
          FileReferenceContextUtil.encodeFileReferences(element);
        }
      }
    }

    Map<PsiElement, PsiElement> oldToNewMap = new HashMap<>();
    List<SmartPsiElementPointer<PsiFile>> movedFiles = new ArrayList<>();
    List<SmartPsiElementPointer<PsiElement>> movedElements = new ArrayList<>(elementsToMove.length);
    for (int i = 0; i < elementsToMove.length; i++) {
      PsiElement element = elementsToMove[i];
      if (showProgression) progressIndicator.setFraction((double)i / elementsToMove.length);
      if (element instanceof PsiDirectory directory) {
        progressIndicator.setText2(directory.getVirtualFile().getPresentableUrl());
        doMoveDirectory(directory, newParent);
        for (PsiElement psiElement : directory.getChildren()) {
          processDirectoryFiles(movedFiles, oldToNewMap, psiElement);
        }
      }
      else if (element instanceof PsiFile movedFile) {
        progressIndicator.setText2(movedFile.getVirtualFile().getPresentableUrl());
        MoveFileHandler.forElement(movedFile).prepareMovedFile(movedFile, newParent, oldToNewMap);

        PsiFile moving = newParent.findFile(movedFile.getName());
        if (moving == null) {
          doMoveFile(movedFile, newParent);
        }
        moving = MovedFileProvider.getInstance().getUpdatedFile(newParent, movedFile);
        if (moving != null) {
          movedFiles.add(SmartPointerManager.createPointer(moving));
        }
      }

      movedElements.add(element.isValid() ? SmartPointerManager.createPointer(element) : null);
    }
    progressIndicator.setText2("");
    if (showProgression) progressIndicator.setFraction(1.0);

    DumbService.getInstance(project).completeJustSubmittedTasks();

    // fix references in moved files to outer files
    for (SmartPsiElementPointer<PsiFile> pointer : movedFiles) {
      PsiFile movedFile = pointer.getElement();
      if (movedFile != null) {
        MoveFileHandler.forElement(movedFile).updateMovedFile(movedFile);
        if (isSearchForReferences) FileReferenceContextUtil.decodeFileReferences(movedFile);
      }
    }

    Reference.reachabilityFence(movingFiles);
    return new MoveElementsResult(movedFiles, movedElements, oldToNewMap);
  }

  private static void encodeDirectoryFiles(@NotNull PsiElement psiElement, @NotNull Map<PsiFile, FileASTNode> movedFiles) {
    if (psiElement instanceof PsiFile) {
      movedFiles.put((PsiFile)psiElement, ((PsiFile)psiElement).getNode());
      FileReferenceContextUtil.encodeFileReferences(psiElement);
    }
    else if (psiElement instanceof PsiDirectory) {
      for (PsiElement element : psiElement.getChildren()) {
        encodeDirectoryFiles(element, movedFiles);
      }
    }
  }

  private static void processDirectoryFiles(@NotNull List<? super SmartPsiElementPointer<PsiFile>> movedFiles,
                                            @NotNull Map<PsiElement, PsiElement> oldToNewMap,
                                            @NotNull PsiElement psiElement) {
    if (psiElement instanceof PsiFile movedFile) {
      movedFiles.add(SmartPointerManager.createPointer(movedFile));
      MoveFileHandler.forElement(movedFile).prepareMovedFile(movedFile, movedFile.getParent(), oldToNewMap);
    }
    else if (psiElement instanceof PsiDirectory) {
      for (PsiElement element : psiElement.getChildren()) {
        processDirectoryFiles(movedFiles, oldToNewMap, element);
      }
    }
  }

  /**
   * Binds the code usages collected by {@link #findUsages} directly to the moved elements.
   * <p>
   * {@code codeUsages} is sorted in place before the retargeting.
   *
   * @param codeUsages usages to bind to the moved elements
   */
  @ApiStatus.Internal
  public static void retargetCodeUsages(UsageInfo @NotNull [] codeUsages) {
    // sort by offset descending to process correctly several usages in one PsiElement [IDEADEV-33013]
    CommonRefactoringUtil.sortDepthFirstRightLeftOrder(codeUsages);
    for (UsageInfo usageInfo : codeUsages) {
      if (usageInfo instanceof MovedFileOrDirectoryUsageInfo info) {
        PsiElement element = info.myTarget;

        if (info.getReference() instanceof FileReference || info.getReference() instanceof PsiDynaReference) {
          final PsiElement usageElement = info.getElement();
          if (usageElement != null) {
            final PsiFile usageFile = usageElement.getContainingFile();
            final PsiFile psiFile = usageFile.getViewProvider().getPsi(usageFile.getViewProvider().getBaseLanguage());
            if (psiFile != null && psiFile.equals(element)) {
              continue;  // already processed in MoveFilesOrDirectoriesUtil.doMoveFile
            }
          }
        }
        final PsiElement refElement = info.myReference.getElement();
        if (refElement.isValid()) {
          info.myReference.bindToElement(element);
        }
      }
    }
  }

  /**
   * Retargets the usages inside the moved files with the language-specific {@link MoveFileHandler}s.
   *
   * @param classifiedUsages code usages grouped by the file that contains them, see {@link UsagesContext#classifiedUsages()}
   * @param oldToNewMap      mapping from the elements before the move to the elements after the move,
   *                         see {@link MoveElementsResult#oldToNewMap()}
   */
  @ApiStatus.Internal
  public static void retargetClassifiedUsages(@NotNull Map<PsiFile, ? extends @Unmodifiable List<UsageInfo>> classifiedUsages,
                                              @NotNull Map<PsiElement, PsiElement> oldToNewMap) {
    for (Map.Entry<PsiFile, ? extends List<UsageInfo>> entry : classifiedUsages.entrySet()) {
      // Before retargeting sort usages by start offset to get consistent results
      List<UsageInfo> sorted = ContainerUtil.sorted(entry.getValue(), Comparator.comparingInt(o -> {
        PsiElement element = o.getElement();
        if (element == null) return -1;
        return element.getTextRange().getStartOffset();
      }));
      MoveFileHandler.forElement(entry.getKey()).retargetUsages(sorted, oldToNewMap);
    }
  }

  /**
   * Notifies {@code listeners} that the elements were moved.
   *
   * @param listeners            listeners to notify, one per index of {@code movedElementPointers}
   * @param movedElementPointers pointers to the moved elements, see {@link MoveElementsResult#movedElementPointers()}
   */
  @ApiStatus.Internal
  public static void afterMovement(@NotNull List<? extends RefactoringElementListener> listeners,
                                   @NotNull List<? extends @Nullable SmartPsiElementPointer<PsiElement>> movedElementPointers) {
    for (int i = 0; i < movedElementPointers.size(); i++) {
      SmartPsiElementPointer<PsiElement> pointer = movedElementPointers.get(i);
      if (pointer == null) continue;
      PsiElement moved = pointer.getElement();
      if (moved != null) {
        listeners.get(i).elementMoved(moved);
      }
    }
  }

  @ApiStatus.Internal
  private static final class MovedFileOrDirectoryUsageInfo extends UsageInfo {
    private final PsiElement myTarget;
    private final PsiReference myReference;

    MovedFileOrDirectoryUsageInfo(@NotNull PsiReference reference, @NotNull PsiElement target) {
      super(reference);
      myReference = reference;
      myTarget = target;
    }

    public PsiElement getTarget() { return myTarget; }
    public PsiReference getUpdatedReference() { return myReference; }
  }
}
