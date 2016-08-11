/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.util;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author ven
 */
public class CommonRefactoringUtil {
  private CommonRefactoringUtil() { }

  public static void showErrorMessage(String title, String message, @Nullable String helpId, @NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
    RefactoringMessageDialog dialog = new RefactoringMessageDialog(title, message, helpId, "OptionPane.errorIcon", false, project);
    dialog.show();
  }

  // order of usages across different files is irrelevant
  public static void sortDepthFirstRightLeftOrder(final UsageInfo[] usages) {
    Arrays.sort(usages, (usage1, usage2) -> {
      PsiElement element1 = usage1.getElement(), element2 = usage2.getElement();
      if (element1 == element2) return 0;
      if (element1 == null) return 1;
      if (element2 == null) return -1;
      return element2.getTextRange().getStartOffset() - element1.getTextRange().getStartOffset();
    });
  }

  /**
   * Fatal refactoring problem during unit test run. Corresponds to message of modal dialog shown during user driven refactoring.
   */
  public static class RefactoringErrorHintException extends RuntimeException {
    public RefactoringErrorHintException(String message) {
      super(message);
    }
  }

  public static void showErrorHint(@NotNull Project project,
                                   @Nullable Editor editor,
                                   @NotNull @Nls String message,
                                   @NotNull @Nls String title,
                                   @Nullable String helpId) {
    if (ApplicationManager.getApplication().isUnitTestMode()) throw new RefactoringErrorHintException(message);

    ApplicationManager.getApplication().invokeLater(() -> {
      if (editor == null || editor.getComponent().getRootPane() == null) {
        showErrorMessage(title, message, helpId, project);
      }
      else {
        HintManager.getInstance().showErrorHint(editor, message);
      }
    });
  }

  public static String htmlEmphasize(@NotNull String text) {
    return StringUtil.htmlEmphasize(text);
  }

  public static boolean checkReadOnlyStatus(@NotNull PsiElement element) {
    final VirtualFile file = element.getContainingFile().getVirtualFile();
    return file != null && !ReadonlyStatusHandler.getInstance(element.getProject()).ensureFilesWritable(file).hasReadonlyFiles();
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull PsiElement element) {
    return checkReadOnlyStatus(element, project, RefactoringBundle.message("refactoring.cannot.be.performed"));
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull PsiElement... elements) {
    return checkReadOnlyStatus(project, Collections.<PsiElement>emptySet(), Arrays.asList(elements), RefactoringBundle.message("refactoring.cannot.be.performed"), true);
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements, boolean notifyOnFail) {
    return checkReadOnlyStatus(project, Collections.<PsiElement>emptySet(), elements, RefactoringBundle.message("refactoring.cannot.be.performed"), notifyOnFail);
  }

  public static boolean checkReadOnlyStatus(@NotNull PsiElement element, @NotNull Project project, @NotNull String messagePrefix) {
    return element.isWritable() || checkReadOnlyStatus(project, Collections.<PsiElement>emptySet(), Collections.singleton(element), messagePrefix, true);
  }

  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements) {
    return checkReadOnlyStatus(project, elements, Collections.<PsiElement>emptySet(), RefactoringBundle.message("refactoring.cannot.be.performed"), false);
  }

  public static boolean checkReadOnlyStatusRecursively(@NotNull Project project, @NotNull Collection<? extends PsiElement> elements, boolean notifyOnFail) {
    return checkReadOnlyStatus(project, elements, Collections.<PsiElement>emptySet(), RefactoringBundle.message("refactoring.cannot.be.performed"), notifyOnFail);
  }

  public static boolean checkReadOnlyStatus(@NotNull Project project,
                                            @NotNull Collection<? extends PsiElement> recursive,
                                            @NotNull Collection<? extends PsiElement> flat,
                                            boolean notifyOnFail) {
    return checkReadOnlyStatus(project, recursive, flat, RefactoringBundle.message("refactoring.cannot.be.performed"), notifyOnFail);
  }

  private static boolean checkReadOnlyStatus(@NotNull Project project,
                                             @NotNull Collection<? extends PsiElement> recursive,
                                             @NotNull Collection<? extends PsiElement> flat,
                                             @NotNull String messagePrefix,
                                             boolean notifyOnFail) {
    Collection<VirtualFile> readonly = new THashSet<>();  // not writable, but could be checked out
    Collection<VirtualFile> failed = new THashSet<>();  // those located in read-only filesystem

    boolean seenNonWritablePsiFilesWithoutVirtualFile =
      checkReadOnlyStatus(flat, false, readonly, failed) || checkReadOnlyStatus(recursive, true, readonly, failed);

    ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(readonly);
    ContainerUtil.addAll(failed, status.getReadonlyFiles());

    if (notifyOnFail && (!failed.isEmpty() || seenNonWritablePsiFilesWithoutVirtualFile && readonly.isEmpty())) {
      StringBuilder message = new StringBuilder(messagePrefix).append('\n');
      int i = 0;
      for (VirtualFile virtualFile : failed) {
        String subj = RefactoringBundle.message(virtualFile.isDirectory() ? "directory.description" : "file.description", virtualFile.getPresentableUrl());
        if (virtualFile.getFileSystem().isReadOnly()) {
          message.append(RefactoringBundle.message("0.is.located.in.a.jar.file", subj)).append('\n');
        }
        else {
          message.append(RefactoringBundle.message("0.is.read.only", subj)).append('\n');
        }
        if (i++ > 20) {
          message.append("...\n");
          break;
        }
      }
      showErrorMessage(RefactoringBundle.message("error.title"), message.toString(), null, project);
      return false;
    }

    return failed.isEmpty();
  }

  private static boolean checkReadOnlyStatus(Collection<? extends PsiElement> elements,
                                             boolean recursively,
                                             Collection<VirtualFile> readonly,
                                             Collection<VirtualFile> failed) {
    boolean seenNonWritablePsiFilesWithoutVirtualFile = false;

    for (PsiElement element : elements) {
      if (element instanceof PsiDirectory) {
        final PsiDirectory dir = (PsiDirectory)element;
        final VirtualFile vFile = dir.getVirtualFile();
        if (vFile.getFileSystem().isReadOnly()) {
          failed.add(vFile);
        }
        else if (recursively) {
          collectReadOnlyFiles(vFile, readonly);
        }
        else {
          readonly.add(vFile);
        }
      }
      else if (element instanceof PsiDirectoryContainer) {
        final PsiDirectory[] directories = ((PsiDirectoryContainer)element).getDirectories();
        for (PsiDirectory directory : directories) {
          VirtualFile virtualFile = directory.getVirtualFile();
          if (recursively) {
            if (virtualFile.getFileSystem().isReadOnly()) {
              failed.add(virtualFile);
            }
            else {
              collectReadOnlyFiles(virtualFile, readonly);
            }
          }
          else if (virtualFile.getFileSystem().isReadOnly()) {
            failed.add(virtualFile);
          }
          else {
            readonly.add(virtualFile);
          }
        }
      }
      else {
        PsiFile file = element.getContainingFile();
        if (file == null) {
          if (!element.isWritable()) {
            seenNonWritablePsiFilesWithoutVirtualFile = true;
          }
        }
        else {
          final VirtualFile vFile = file.getVirtualFile();
          if (vFile != null) {
            readonly.add(vFile);
          }
          else if (!element.isWritable()) {
            seenNonWritablePsiFilesWithoutVirtualFile = true;
          }
        }
      }
    }

    return seenNonWritablePsiFilesWithoutVirtualFile;
  }

  public static void collectReadOnlyFiles(@NotNull VirtualFile vFile, @NotNull final Collection<VirtualFile> list) {
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();

    VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor(VirtualFileVisitor.NO_FOLLOW_SYMLINKS) {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        final boolean ignored = fileTypeManager.isFileIgnored(file);
        if (!file.isWritable() && !ignored) {
          list.add(file);
        }
        return !ignored;
      }
    });
  }

  public static String capitalize(@NotNull String text) {
    return StringUtil.capitalize(text);
  }

  public static boolean isAncestor(@NotNull PsiElement resolved, @NotNull Collection<? extends PsiElement> scopes) {
    for (final PsiElement scope : scopes) {
      if (PsiTreeUtil.isAncestor(scope, resolved, false)) return true;
    }
    return false;
  }
}