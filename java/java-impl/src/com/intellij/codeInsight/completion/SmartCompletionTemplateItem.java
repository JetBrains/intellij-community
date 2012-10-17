package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class SmartCompletionTemplateItem extends LookupItem<Template> implements TypedLookupItem {
  @NonNls private static final String PLACEHOLDER = "xxx";
  private final PsiElement myContext;

  public SmartCompletionTemplateItem(Template o, PsiElement context) {
    super(o, o.getKey());
    myContext = context;
  }


  @Override
  public PsiType getType() {
    final Template template = getObject();
    String text = template.getTemplateText();
    StringBuilder resultingText = new StringBuilder(text);

    int segmentsCount = template.getSegmentsCount();

    for (int j = segmentsCount - 1; j >= 0; j--) {
      if (template.getSegmentName(j).equals(TemplateImpl.END)) {
        continue;
      }

      int segmentOffset = template.getSegmentOffset(j);

      resultingText.insert(segmentOffset, PLACEHOLDER);
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
