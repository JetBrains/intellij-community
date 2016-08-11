/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.refactoring.lang;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.find.FindManager;
import com.intellij.ide.TitledHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileSystemItemUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public abstract class ExtractIncludeFileBase<T extends PsiElement> implements RefactoringActionHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.lang.ExtractIncludeFileBase");
  private static final String REFACTORING_NAME = RefactoringBundle.message("extract.include.file.title");
  protected PsiFile myIncludingFile;
  public static final String HELP_ID = "refactoring.extractInclude";

  private static class IncludeDuplicate<E extends PsiElement> {
    private final SmartPsiElementPointer<E> myStart;
    private final SmartPsiElementPointer<E> myEnd;

    private IncludeDuplicate(E start, E end) {
      myStart = SmartPointerManager.getInstance(start.getProject()).createSmartPsiElementPointer(start);
      myEnd = SmartPointerManager.getInstance(start.getProject()).createSmartPsiElementPointer(end);
    }

    E getStart() {
      return myStart.getElement();
    }

    E getEnd() {
      return myEnd.getElement();
    }
  }


  protected abstract void doReplaceRange(final String includePath, final T first, final T last);

  @NotNull
  protected String doExtract(final PsiDirectory targetDirectory,
                             final String targetfileName,
                             final T first,
                             final T last,
                             final Language includingLanguage) throws IncorrectOperationException {
    final PsiFile file = targetDirectory.createFile(targetfileName);
    Project project = targetDirectory.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = documentManager.getDocument(file);
    document.replaceString(0, document.getTextLength(), first.getText().trim());
    documentManager.commitDocument(document);
    CodeStyleManager.getInstance(PsiManager.getInstance(project).getProject()).reformat(file);  //TODO: adjustLineIndent

    final String relativePath = PsiFileSystemItemUtil.getRelativePath(first.getContainingFile(), file);
    if (relativePath == null) throw new IncorrectOperationException("Cannot extract!");
    return relativePath;
  }

  protected abstract boolean verifyChildRange (final T first, final T last);

  private void replaceDuplicates(final String includePath,
                                   final List<IncludeDuplicate<T>> duplicates,
                                   final Editor editor,
                                   final Project project) {
    if (duplicates.size() > 0) {
      final String message = RefactoringBundle.message("idea.has.found.fragments.that.can.be.replaced.with.include.directive",
                                                  ApplicationNamesInfo.getInstance().getProductName());
      final int exitCode = Messages.showYesNoDialog(project, message, getRefactoringName(), Messages.getInformationIcon());
      if (exitCode == Messages.YES) {
        CommandProcessor.getInstance().executeCommand(project, () -> {
          boolean replaceAll = false;
          for (IncludeDuplicate<T> pair : duplicates) {
            if (!replaceAll) {

              highlightInEditor(project, pair, editor);

              ReplacePromptDialog promptDialog = new ReplacePromptDialog(false, RefactoringBundle.message("replace.fragment"), project);
              promptDialog.show();
              final int promptResult = promptDialog.getExitCode();
              if (promptResult == FindManager.PromptResult.SKIP) continue;
              if (promptResult == FindManager.PromptResult.CANCEL) break;

              if (promptResult == FindManager.PromptResult.OK) {
                doReplaceRange(includePath, pair.getStart(), pair.getEnd());
              }
              else if (promptResult == FindManager.PromptResult.ALL) {
                doReplaceRange(includePath, pair.getStart(), pair.getEnd());
                replaceAll = true;
              }
              else {
                LOG.error("Unknown return status");
              }
            }
            else {
              doReplaceRange(includePath, pair.getStart(), pair.getEnd());
            }
          }
        }, RefactoringBundle.message("remove.duplicates.command"), null);
      }
    }
  }

  private static void highlightInEditor(final Project project, final IncludeDuplicate pair, final Editor editor) {
    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final int startOffset = pair.getStart().getTextRange().getStartOffset();
    final int endOffset = pair.getEnd().getTextRange().getEndOffset();
    highlightManager.addRangeHighlight(editor, startOffset, endOffset, attributes, true, null);
    final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(startOffset);
    editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
  }

  @NotNull
  protected Language getLanguageForExtract(PsiElement firstExtracted) {
    return firstExtracted.getLanguage();
  }

  @Nullable
  private static FileType getFileType(final Language language) {
    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      if (fileType instanceof LanguageFileType && language.equals(((LanguageFileType)fileType).getLanguage())) return fileType;
    }

    return null;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    try {
      myIncludingFile = file;
      doInvoke(project, editor, file);
    }
    finally {
      myIncludingFile = null;
    }
  }

  protected void doInvoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!editor.getSelectionModel().hasSelection()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("no.selection"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }
    final int start = editor.getSelectionModel().getSelectionStart();
    final int end = editor.getSelectionModel().getSelectionEnd();

    final Pair<T, T> children = findPairToExtract(start, end);
    if (children == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selection.does.not.form.a.fragment.for.extraction"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }

    if (!verifyChildRange(children.getFirst(), children.getSecond())) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.extract.selected.elements.into.include.file"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }

    final FileType fileType = getFileType(getLanguageForExtract(children.getFirst()));
    if (!(fileType instanceof LanguageFileType)) {
      String message = RefactoringBundle.message("the.language.for.selected.elements.has.no.associated.file.type");
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HELP_ID);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file) || ApplicationManager.getApplication().isUnitTestMode()) return;

    ExtractIncludeDialog dialog = createDialog(file.getContainingDirectory(), getExtractExtension(fileType, children.first));
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final PsiDirectory targetDirectory = dialog.getTargetDirectory();
      LOG.assertTrue(targetDirectory != null);
      final String targetfileName = dialog.getTargetFileName();
      CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
        try {
          final List<IncludeDuplicate<T>> duplicates = new ArrayList<>();
          final T first = children.getFirst();
          final T second = children.getSecond();
          PsiEquivalenceUtil.findChildRangeDuplicates(first, second, file, (start1, end1) -> duplicates.add(
            new IncludeDuplicate<>((T)start1, (T)end1)));
          final String includePath = processPrimaryFragment(first, second, targetDirectory, targetfileName, file);
          editor.getCaretModel().moveToOffset(first.getTextRange().getStartOffset());

          ApplicationManager.getApplication().invokeLater(() -> replaceDuplicates(includePath, duplicates, editor, project));
        }
        catch (IncorrectOperationException e) {
          CommonRefactoringUtil.showErrorMessage(getRefactoringName(), e.getMessage(), null, project);
        }

        editor.getSelectionModel().removeSelection();
      }), getRefactoringName(), null);

    }
  }

  protected ExtractIncludeDialog createDialog(final PsiDirectory containingDirectory, final String extractExtension) {
    return new ExtractIncludeDialog(containingDirectory, extractExtension);
  }

  @Nullable
  protected abstract Pair<T, T> findPairToExtract(int start, int end);

  @NonNls
  protected String getExtractExtension(final FileType extractFileType, final T first) {
    return extractFileType.getDefaultExtension();
  }

  @Deprecated
  @TestOnly
  public boolean isValidRange(final T firstToExtract, final T lastToExtract) {
    return verifyChildRange(firstToExtract, lastToExtract);
  }

  public String processPrimaryFragment(final T firstToExtract,
                                       final T lastToExtract,
                                       final PsiDirectory targetDirectory,
                                       final String targetfileName,
                                       final PsiFile srcFile) throws IncorrectOperationException {
    final String includePath = doExtract(targetDirectory, targetfileName, firstToExtract, lastToExtract,
                                         srcFile.getLanguage());

    doReplaceRange(includePath, firstToExtract, lastToExtract);
    return includePath;
  }

  @Override
  public String getActionTitle() {
    return "Extract Include File...";
  }

  protected String getRefactoringName() {
    return REFACTORING_NAME;
  }
}
