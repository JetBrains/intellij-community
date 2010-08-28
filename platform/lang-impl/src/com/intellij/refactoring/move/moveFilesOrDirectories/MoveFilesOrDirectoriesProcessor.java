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

package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.FileReferenceContextUtil;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MoveFilesOrDirectoriesProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor");

  private final PsiElement[] myElementsToMove;
  private final boolean mySearchInComments;
  private final boolean mySearchInNonJavaFiles;
  private final PsiDirectory myNewParent;
  private final MoveCallback myMoveCallback;
  private NonCodeUsageInfo[] myNonCodeUsages;
  private final Map<PsiFile, List<UsageInfo>> myFoundUsages = new HashMap<PsiFile, List<UsageInfo>>();

  public MoveFilesOrDirectoriesProcessor(
    Project project,
    PsiElement[] elements,
    PsiDirectory newParent,
    boolean searchInComments,
    boolean searchInNonJavaFiles,
    MoveCallback moveCallback,
    Runnable prepareSuccessfulCallback) {
    super(project, prepareSuccessfulCallback);
    myElementsToMove = elements;
    myNewParent = newParent;
    mySearchInComments = searchInComments;
    mySearchInNonJavaFiles = searchInNonJavaFiles;
    myMoveCallback = moveCallback;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MoveFilesOrDirectoriesViewDescriptor(myElementsToMove, myNewParent);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    for (int i = 0; i < myElementsToMove.length; i++) {
      PsiElement element = myElementsToMove[i];
      for (PsiReference reference : ReferencesSearch.search(element)) {
        result.add(new MyUsageInfo(reference.getElement(), i, reference));
      }
      findElementUsages(result, element);
    }

    return result.toArray(new UsageInfo[result.size()]);
  }

  private void findElementUsages(ArrayList<UsageInfo> result, PsiElement element) {
    if (element instanceof PsiFile) {
      final List<UsageInfo> usages = MoveFileHandler.forElement((PsiFile)element)
        .findUsages(((PsiFile)element), myNewParent, mySearchInComments, mySearchInNonJavaFiles);
      if (usages != null) {
        result.addAll(usages);
        myFoundUsages.put((PsiFile)element, usages);
      }
    } else if (element instanceof PsiDirectory) {
      for (PsiElement childElement : element.getChildren()) {
        findElementUsages(result, childElement);
      }
    }
  }


  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == myElementsToMove.length);
    System.arraycopy(elements, 0, myElementsToMove, 0, elements.length);
  }

  @Override
  protected void performPsiSpoilingRefactoring() {
    RenameUtil.renameNonCodeUsages(myProject, myNonCodeUsages);
  }

  protected void performRefactoring(UsageInfo[] usages) {
    // If files are being moved then I need to collect some information to delete these
    // filese from CVS. I need to know all common parents of the moved files and releative
    // paths.

    // Move files with correction of references.

    try {

      final List<PsiFile> movedFiles = new ArrayList<PsiFile>();
      final Map<PsiElement, PsiElement> oldToNewMap = new HashMap<PsiElement, PsiElement>();
      for (final PsiElement element : myElementsToMove) {
        if (element instanceof PsiDirectory) {
          MoveFilesOrDirectoriesUtil.doMoveDirectory((PsiDirectory)element, myNewParent);
          for (PsiElement psiElement : element.getChildren()) {
            processDirectoryFiles(movedFiles, oldToNewMap, psiElement);
          }
        }
        else if (element instanceof PsiFile) {
          final PsiFile movedFile = (PsiFile)element;
          FileReferenceContextUtil.encodeFileReferences(element);
          MoveFileHandler.forElement(movedFile).prepareMovedFile(movedFile, myNewParent, oldToNewMap);

          PsiFile moving = myNewParent.findFile(movedFile.getName());
          if (moving == null) {
            MoveFilesOrDirectoriesUtil.doMoveFile(movedFile, myNewParent);
          }
          moving = myNewParent.findFile(movedFile.getName());
          movedFiles.add(moving);
        }

        getTransaction().getElementListener(element).elementMoved(element);
      }
      // sort by offset descending to process correctly several usages in one PsiElement [IDEADEV-33013]
      Arrays.sort(usages, new Comparator<UsageInfo>() {
        public int compare(final UsageInfo o1, final UsageInfo o2) {
          return o1.getElement() == o2.getElement() ? o2.startOffset - o1.startOffset : 0;
        }
      });

      retargetUsages(usages, oldToNewMap);

      // fix references in moved files to outer files
      for (PsiFile movedFile : movedFiles) {
        MoveFileHandler.forElement(movedFile).updateMovedFile(movedFile);
        FileReferenceContextUtil.decodeFileReferences(movedFile);
      }

      // Perform CVS "add", "remove" commands on moved files.

      if (myMoveCallback != null) {
        myMoveCallback.refactoringCompleted();
      }

    }
    catch (IncorrectOperationException e) {
      @NonNls final String message = e.getMessage();
      final int index = message != null ? message.indexOf("java.io.IOException") : -1;
      if (index >= 0) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                Messages.showMessageDialog(myProject, message.substring(index + "java.io.IOException".length()),
                                           RefactoringBundle.message("error.title"),
                                           Messages.getErrorIcon());
              }
            });
      }
      else {
        LOG.error(e);
      }
    }
  }

  private static void processDirectoryFiles(List<PsiFile> movedFiles, Map<PsiElement, PsiElement> oldToNewMap, PsiElement psiElement) {
    if (psiElement instanceof PsiFile) {
      final PsiFile movedFile = (PsiFile)psiElement;
      FileReferenceContextUtil.encodeFileReferences(psiElement);
      MoveFileHandler.forElement(movedFile).prepareMovedFile(movedFile, movedFile.getParent(), oldToNewMap);
      movedFiles.add(movedFile);
    }
    else if (psiElement instanceof PsiDirectory) {
      for (PsiElement element : psiElement.getChildren()) {
        processDirectoryFiles(movedFiles, oldToNewMap, element);
      }
    }
  }

  private void retargetUsages(UsageInfo[] usages, Map<PsiElement, PsiElement> oldToNewMap) {
    final List<NonCodeUsageInfo> nonCodeUsages = new ArrayList<NonCodeUsageInfo>();
    for (UsageInfo usageInfo : usages) {
      if (usageInfo instanceof MyUsageInfo) {
        final MyUsageInfo info = (MyUsageInfo)usageInfo;
        final PsiElement element = myElementsToMove[info.myIndex];

        if (info.getReference() instanceof FileReference) {
          final PsiElement usageElement = info.getElement();
          if (usageElement != null) {
            final PsiFile usageFile = usageElement.getContainingFile();
            final PsiFile psiFile = usageFile.getViewProvider().getPsi(usageFile.getViewProvider().getBaseLanguage());
            if (psiFile != null && psiFile.equals(element)) {
              continue;  // already processed in MoveFilesOrDirectoriesUtil.doMoveFile
            }
          }
        }

        info.myReference.bindToElement(element);
      } else if (usageInfo instanceof NonCodeUsageInfo) {
        nonCodeUsages.add((NonCodeUsageInfo)usageInfo);
      }
    }

    for (PsiFile movedFile : myFoundUsages.keySet()) {
      MoveFileHandler.forElement(movedFile).retargetUsages(myFoundUsages.get(movedFile), oldToNewMap);
    }

    myNonCodeUsages = nonCodeUsages.toArray(new NonCodeUsageInfo[nonCodeUsages.size()]);
  }

  protected String getCommandName() {
    return RefactoringBundle.message("move.title"); //TODO!!
  }

  static class MyUsageInfo extends UsageInfo {
    int myIndex;
    PsiReference myReference;

    public MyUsageInfo(PsiElement element, final int index, PsiReference reference) {
      super(element);
      myIndex = index;
      myReference = reference;
    }
  }
}
