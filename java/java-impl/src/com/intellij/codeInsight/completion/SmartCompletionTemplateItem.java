package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.LiveTemplateLookupElementImpl;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public class SmartCompletionTemplateItem extends LiveTemplateLookupElementImpl implements TypedLookupItem {
  private final PsiElement myContext;

  public SmartCompletionTemplateItem(TemplateImpl template, PsiElement context) {
    super(template, false);
    myContext = context;
  }

  @Override
  public PsiType getType() {
    final Template template = getTemplate();
    String text = template.getTemplateText();
    StringBuilder resultingText = new StringBuilder(text);

    int segmentsCount = template.getSegmentsCount();

    for (int j = segmentsCount - 1; j >= 0; j--) {
      if (template.getSegmentName(j).equals(TemplateImpl.END)) {
        continue;
      }

      resultingText.insert(template.getSegmentOffset(j), "xxx");
    }

    try {
      final PsiExpression templateExpression = JavaPsiFacade.getElementFactory(myContext.getProject()).createExpressionFromText(resultingText.toString(), myContext);
      return templateExpression.getType();
    }
    catch (IncorrectOperationException e) { // can happen when text of the template does not form an expression
      return null;
    }
  }
}
