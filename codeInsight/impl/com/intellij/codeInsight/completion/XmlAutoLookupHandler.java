package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 *
 */
public class XmlAutoLookupHandler extends CodeCompletionHandler {
  protected boolean isAutocompleteOnInvocation() {
    return false;
  }

  protected boolean isAutocompleteCommonPrefixOnInvocation() {
    return false;
  }

  protected void handleEmptyLookup(CompletionContext context, LookupData lookupData) {
  }

  protected LookupData getLookupData(CompletionContext context) {
    PsiFile file = context.file;
    int offset = context.startOffset;

    PsiElement lastElement = file.findElementAt(offset - 1);
    if (lastElement == null) return LookupData.EMPTY;

    final Ref<Boolean> isRelevantLanguage = new Ref<Boolean>();
    final Ref<Boolean> isAnt = new Ref<Boolean>();
    String text = lastElement.getText();
    final int len = context.startOffset - lastElement.getTextRange().getStartOffset();
    if (len < text.length()) {
      text = text.substring(0, len);
    }
    if (text.equals("<") && isLanguageRelevant(lastElement, file, isRelevantLanguage, isAnt)) {
      return super.getLookupData(context);
    }
    else if (text.endsWith("${") && isLanguageRelevant(lastElement, file, isRelevantLanguage, isAnt) && isAnt.get().booleanValue()) {
      return super.getLookupData(context);
    }
    else if (text.endsWith("@{") && isLanguageRelevant(lastElement, file, isRelevantLanguage, isAnt) && isAnt.get().booleanValue()) {
      return super.getLookupData(context);
    }
    else if (text.endsWith("</") && isLanguageRelevant(lastElement, file, isRelevantLanguage, isAnt) && isAnt.get().booleanValue()) {
      return super.getLookupData(context);
    }
    //if (lastElement instanceof PsiWhiteSpace && lastElement.getPrevSibling() instanceof XmlTag) {
    //  return super.getLookupData(context);
    //}

    return LookupData.EMPTY;
  }

  private static boolean isLanguageRelevant(final PsiElement element,
                                            final PsiFile file,
                                            final Ref<Boolean> isRelevantLanguage,
                                            final Ref<Boolean> isAnt) {
    Boolean isAntFile = isAnt.get();
    if (isAntFile == null) {
      isAntFile = CodeInsightUtil.isAntFile(file);
      isAnt.set(isAntFile);
    }
    Boolean result = isRelevantLanguage.get();
    if (result == null) {
      result = element.getLanguage() instanceof XMLLanguage || isAntFile.booleanValue();
      isRelevantLanguage.set(result);
    }
    return result.booleanValue();
  }
}
