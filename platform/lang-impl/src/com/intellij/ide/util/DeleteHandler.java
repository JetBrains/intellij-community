// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DataManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.safeDelete.SafeDeleteDialog;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import com.intellij.util.ui.IoErrorText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class DeleteHandler {

  private static Boolean ourOverrideNeedsConfirmation;

  private DeleteHandler() { }

  public static class DefaultDeleteProvider implements DeleteProvider {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      if (CommonDataKeys.PROJECT.getData(dataContext) == null) {
        return false;
      }
      final PsiElement[] elements = getPsiElements(dataContext);
      return shouldEnableDeleteAction(elements);
    }

    private static PsiElement @Nullable [] getPsiElements(DataContext dataContext) {
      PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      if (elements == null) {
        final PsiElement data = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
        if (data != null) {
          elements = new PsiElement[]{data};
        }
        else {
          final PsiFile data1 = CommonDataKeys.PSI_FILE.getData(dataContext);
          if (data1 != null) {
            elements = new PsiElement[]{data1};
          }
        }
      }
      return elements;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      PsiElement[] elements = getPsiElements(dataContext);
      if (elements == null) return;
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project == null) return;
      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        deletePsiElement(elements, project);
      }
      finally {
        a.finish();
      }
    }
  }

  public static void deletePsiElement(final PsiElement[] elementsToDelete, final Project project) {
    deletePsiElement(elementsToDelete, project, true);
  }

  public static void deletePsiElement(final PsiElement[] elementsToDelete, final Project project, boolean needConfirmation) {
    if (elementsToDelete == null || elementsToDelete.length == 0) return;
    needConfirmation = ourOverrideNeedsConfirmation != null ? ourOverrideNeedsConfirmation : needConfirmation;

    final PsiElement[] elements = PsiTreeUtil.filterAncestors(elementsToDelete);

    boolean safeDeleteApplicable = ContainerUtil.and(elements, SafeDeleteProcessor::validElement);

    final boolean dumb = DumbService.getInstance(project).isDumb();
    if (safeDeleteApplicable && !dumb) {
      final Ref<Boolean> exit = Ref.create(false);
      final SafeDeleteDialog dialog = new SafeDeleteDialog(project, elements, new SafeDeleteDialog.Callback() {
        @Override
        public void run(final SafeDeleteDialog dialog) {
          if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(elements), true)) return;

          SafeDeleteProcessor processor = SafeDeleteProcessor.createInstance(project, () -> {
            exit.set(true);
            dialog.close(DialogWrapper.OK_EXIT_CODE);
          }, elements, dialog.isSearchInComments(), dialog.isSearchForTextOccurences(), true);

          processor.run();
        }
      }) {
        @Override
        protected boolean isDelete() {
          return true;
        }
      };
      if (needConfirmation) {
        dialog.setTitle(RefactoringBundle.message("delete.title"));
        if (!dialog.showAndGet() || exit.get()) {
          return;
        }
      }
    }
    else {
      String warningMessage = DeleteUtil.generateWarningMessage("prompt.delete.elements", elements);

      boolean anyDirectories = false;
      String directoryName = null;
      for (PsiElement psiElement : elementsToDelete) {
        if (psiElement instanceof PsiDirectory && !PsiUtilBase.isSymLink((PsiDirectory)psiElement)) {
          anyDirectories = true;
          directoryName = ((PsiDirectory)psiElement).getName();
          break;
        }
      }
      if (anyDirectories) {
        if (elements.length == 1) {
          warningMessage += IdeBundle.message("warning.delete.all.files.and.subdirectories", directoryName);
        }
        else {
          warningMessage += IdeBundle.message("warning.delete.all.files.and.subdirectories.in.the.selected.directory");
        }
      }

      if (safeDeleteApplicable) {
        warningMessage +=
          LangBundle.message("dialog.message.warning.safe.delete.not.available.while.updates.indices.no.usages.will.be.checked", ApplicationNamesInfo.getInstance().getFullProductName());
      }

      if (needConfirmation) {
        int result = Messages.showOkCancelDialog(project, warningMessage, IdeBundle.message("title.delete"),
                                                 ApplicationBundle.message("button.delete"), CommonBundle.getCancelButtonText(),
                                                 Messages.getQuestionIcon());
        if (result != Messages.OK) return;
      }
    }

    deleteInCommand(project, elements);
  }

  private static boolean makeWritable(Project project, PsiElement[] elements) {
    Collection<PsiElement> directories = new SmartList<>();
    for (PsiElement e : elements) {
      if (e instanceof PsiFileSystemItem && e.getParent() != null) {
        directories.add(e.getParent());
      }
    }

    return CommonRefactoringUtil.checkReadOnlyStatus(project, Arrays.asList(elements), directories, false);
  }

  private static void deleteInCommand(Project project, PsiElement[] elements) {
    CommandProcessor.getInstance().executeCommand(project, () -> NonProjectFileWritingAccessProvider.disableChecksDuring(() -> {
      SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
      List<SmartPsiElementPointer<?>> pointers = ContainerUtil.map(elements, smartPointerManager::createSmartPsiElementPointer);

      if (!makeWritable(project, elements)) return;

      // deleted from project view or something like that.
      @SuppressWarnings("deprecation") DataContext context = DataManager.getInstance().getDataContext();
      if (CommonDataKeys.EDITOR.getData(context) == null) {
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
      }

      if (ContainerUtil.and(elements, DeleteHandler::isLocalFile)) {
        doDeleteFiles(project, elements);
      }
      else {
        for (SmartPsiElementPointer<?> pointer : pointers) {
          PsiElement elementToDelete = pointer.getElement();
          if (elementToDelete == null) continue; //was already deleted
          doDelete(project, elementToDelete);
        }
      }
    }), RefactoringBundle.message("safe.delete.command", RefactoringUIUtil.calculatePsiElementDescriptionList(elements)), null);
  }

  private static boolean isLocalFile(PsiElement e) {
    if (e instanceof PsiFileSystemItem) {
      VirtualFile file = ((PsiFileSystemItem)e).getVirtualFile();
      if (file != null && file.isInLocalFileSystem()) return true;
    }
    return false;
  }

  private static boolean clearFileReadOnlyFlags(Project project, PsiElement elementToDelete) {
    if (elementToDelete instanceof PsiDirectory) {
      VirtualFile virtualFile = ((PsiDirectory)elementToDelete).getVirtualFile();
      if (virtualFile.isInLocalFileSystem() && !virtualFile.is(VFileProperty.SYMLINK)) {
        List<VirtualFile> readOnlyFiles = new ArrayList<>();
        CommonRefactoringUtil.collectReadOnlyFiles(virtualFile, readOnlyFiles);

        if (!readOnlyFiles.isEmpty()) {
          String message = IdeBundle.message("prompt.directory.contains.read.only.files", virtualFile.getPresentableUrl());
          int _result = Messages.showYesNoDialog(project, message, IdeBundle.message("title.delete"), Messages.getQuestionIcon());
          if (_result != Messages.YES) return false;

          boolean success = true;
          for (VirtualFile file : readOnlyFiles) {
            success = clearReadOnlyFlag(file, project);
            if (!success) break;
          }
          if (!success) return false;
        }
      }
    }
    else if (!elementToDelete.isWritable() &&
             !(elementToDelete instanceof PsiFileSystemItem && PsiUtilBase.isSymLink((PsiFileSystemItem)elementToDelete))) {
      final PsiFile file = elementToDelete.getContainingFile();
      if (file != null) {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile.isInLocalFileSystem()) {
          int _result = MessagesEx.fileIsReadOnly(project, virtualFile)
            .setTitle(IdeBundle.message("title.delete"))
            .appendMessage(" " + IdeBundle.message("prompt.delete.it.anyway"))
            .askYesNo();
          if (_result != Messages.YES) return false;

          boolean success = clearReadOnlyFlag(virtualFile, project);
          if (!success) return false;
        }
      }
    }
    return true;
  }

  private static void doDelete(Project project, PsiElement element) {
    if (!clearFileReadOnlyFlags(project, element)) return;

    try {
      //noinspection deprecation
      element.checkDelete();
    }
    catch (IncorrectOperationException e) {
      Messages.showMessageDialog(project, e.getMessage(), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        element.delete();
      }
      catch (IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMessageDialog(project, e.getMessage(), CommonBundle.getErrorTitle(), Messages.getErrorIcon()));
      }
    });
  }

  private static void doDeleteFiles(Project project, PsiElement[] fileElements) {
    for (PsiElement file : fileElements) {
      if (!clearFileReadOnlyFlags(project, file)) return;
    }

    LocalFilesDeleteTask task = new LocalFilesDeleteTask(project, fileElements);
    ProgressManager.getInstance().run(task);
    if (task.error != null) {
      String file = task.error instanceof FileSystemException ? ((FileSystemException)task.error).getFile() : null;
      if (file != null) {
        String message = IoErrorText.message(task.error), yes = RevealFileAction.getActionName(), no = CommonBundle.getCloseButtonText();
        if (Messages.showYesNoDialog(project, message, CommonBundle.getErrorTitle(), yes, no, Messages.getErrorIcon()) == Messages.YES) {
          RevealFileAction.openFile(Paths.get(file));
        }
      }
      else {
        Messages.showMessageDialog(project, IoErrorText.message(task.error), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      }
    }
    if (task.aborted != null) {
      VfsUtil.markDirtyAndRefresh(true, true, false, task.aborted);
    }
    if (!task.processed.isEmpty()) {
      ApplicationManager.getApplication().runWriteAction(() -> {
        for (PsiElement fileElement : task.processed) {
          try {
            fileElement.delete();
          }
          catch (IncorrectOperationException e) {
            ApplicationManager.getApplication().invokeLater(
              () -> Messages.showMessageDialog(project, e.getMessage(), CommonBundle.getErrorTitle(), Messages.getErrorIcon()));
          }
        }
      });
    }
  }

  private static boolean clearReadOnlyFlag(final VirtualFile virtualFile, final Project project) {
    final boolean[] success = new boolean[1];
    CommandProcessor.getInstance().executeCommand(project, () -> {
      Runnable action = () -> {
        try {
          ReadOnlyAttributeUtil.setReadOnlyAttribute(virtualFile, false);
          success[0] = true;
        }
        catch (IOException e1) {
          Messages.showMessageDialog(project, e1.getMessage(), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        }
      };
      ApplicationManager.getApplication().runWriteAction(action);
    }, "", null);
    return success[0];
  }

  public static boolean shouldEnableDeleteAction(PsiElement[] elements) {
    if (elements == null || elements.length == 0) return false;
    for (PsiElement element : elements) {
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
      if (virtualFile == null || virtualFile instanceof LightVirtualFile) {
        return false;
      }
      if (!WritingAccessProvider.isPotentiallyWritable(virtualFile, element.getProject())) {
        return false;
      }
    }
    return true;
  }

  @TestOnly
  public static void overrideNeedsConfirmationInTests(boolean needsConfirmation, @NotNull Disposable disposable) {
    ourOverrideNeedsConfirmation = needsConfirmation;
    Disposer.register(disposable, () -> ourOverrideNeedsConfirmation = null);
  }

  private static class LocalFilesDeleteTask extends Task.Modal {
    private final PsiElement[] myFileElements;

    List<PsiElement> processed = new ArrayList<>();
    VirtualFile aborted = null;
    Throwable error = null;

    LocalFilesDeleteTask(Project project, PsiElement[] fileElements) {
      super(project, IdeBundle.message("progress.deleting"), true);
      myFileElements = fileElements;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);

      try {
        for (PsiElement element : myFileElements) {
          if (indicator.isCanceled()) break;

          VirtualFile file = ((PsiFileSystemItem)element).getVirtualFile();
          aborted = file;
          Path path = file.toNioPath();
          indicator.setText(path.toString());

          try {
            NioFiles.deleteRecursively(path, p -> {
              indicator.checkCanceled();
              indicator.setText2(path.relativize(p).toString());
            });
            processed.add(element);
            aborted = null;
          }
          catch (ProcessCanceledException ignored) { }
        }
      }
      catch (Throwable t) {
        Logger.getInstance(getClass()).info(t);
        error = t;
      }
    }
  }
}
