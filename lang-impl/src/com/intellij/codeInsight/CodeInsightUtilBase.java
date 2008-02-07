package com.intellij.codeInsight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageDialect;
import com.intellij.util.ReflectionCache;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;

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
    Language language = element.getLanguage();
    LanguageDialect languageDialect = psiFile.getLanguageDialect();
    if (languageDialect != null) {
      language = languageDialect;
    }
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
    final RangeMarker rangeMarker = document.createRangeMarker(element.getTextRange());
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    documentManager.commitDocument(document);

    return findElementInRange(psiFile, rangeMarker.getStartOffset(), rangeMarker.getEndOffset(),
                              (Class<? extends T>)element.getClass(),
                              language);
  }
}
