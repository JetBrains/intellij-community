package com.intellij.codeInsight;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ReflectionCache;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.Set;

public class CodeInsightUtilBase {
  private CodeInsightUtilBase() {
  }

  static <T extends PsiElement> T findElementInRange(final PsiFile file,
                                                             int startOffset,
                                                             int endOffset,
                                                             final Class<T> klass,
                                                             final Language language) {
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, language);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, language);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.getViewProvider().findElementAt(startOffset, language);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.getViewProvider().findElementAt(endOffset - 1, language);
    }
    if (element2 == null || element1 == null) return null;
    final PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    final T element =
      ReflectionCache.isAssignable(klass, commonParent.getClass())
      ? (T)commonParent : PsiTreeUtil.getParentOfType(commonParent, klass);
    if (element == null || element.getTextRange().getStartOffset() != startOffset || element.getTextRange().getEndOffset() != endOffset) {
      return null;
    }
    return element;
  }

  public static <T extends PsiElement> T forcePsiPostprocessAndRestoreElement(final T element) {
    final PsiFile psiFile = element.getContainingFile();
    final Document document = psiFile.getViewProvider().getDocument();
    if (document == null) return element;
    final Language language = PsiUtilBase.getDialect(element);
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
    final RangeMarker rangeMarker = document.createRangeMarker(element.getTextRange());
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    documentManager.commitDocument(document);

    return findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(),
                              (Class<? extends T>)element.getClass(),
                              language);
  }

  public static boolean prepareFileForWrite(final PsiFile file) {
    if (file == null) return false;

    if (!file.isWritable()) {
      final Project project = file.getProject();

      final Editor editor =
        FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file.getVirtualFile()), true);

      final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
      if (!FileDocumentManager.fileForDocumentCheckedOutSuccessfully(document, project)) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (editor != null && editor.getComponent().isDisplayable()) {
              HintManager.getInstance()
                .showErrorHint(editor, CodeInsightBundle.message("error.hint.file.is.readonly", file.getVirtualFile().getPresentableUrl()));
            }
          }
        });

        return false;
      }
    }

    return true;
  }

  public static boolean preparePsiElementForWrite(PsiElement element) {
    PsiFile file = element == null ? null : element.getContainingFile();
    return prepareFileForWrite(file);
  }

  public static boolean preparePsiElementsForWrite(Collection<? extends PsiElement> elements) {
    if (elements.isEmpty()) return true;
    Set<VirtualFile> files = new THashSet<VirtualFile>();
    Project project = null;
    for (PsiElement element : elements) {
      project = element.getProject();
      PsiFile file = element.getContainingFile();
      if (file == null) continue;
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) continue;
      files.add(virtualFile);
    }
    if (!files.isEmpty()) {
      VirtualFile[] virtualFiles = files.toArray(new VirtualFile[files.size()]);
      ReadonlyStatusHandler.OperationStatus status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(virtualFiles);
      return !status.hasReadonlyFiles();
    }
    return true;
  }
}
