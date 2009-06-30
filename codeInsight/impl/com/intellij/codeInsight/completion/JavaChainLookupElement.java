/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.MemorizingLookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.DecoratingLookupElementPresentation;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author peter
 */
public class JavaChainLookupElement extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  private final LookupElement myQualifier;

  public JavaChainLookupElement(LookupElement qualifier, LookupElement main) {
    super(main);
    myQualifier = qualifier;
  }

  @NotNull
  @Override
  public String getLookupString() {
    String qualifierText = myQualifier.getLookupString();
    if (myQualifier.getObject() instanceof PsiMethod) {
      qualifierText += "()";
    }
    return qualifierText + "." + getDelegate().getLookupString();
  }

  public LookupElement getQualifier() {
    return myQualifier;
  }

  @Override
  public Set<String> getAllLookupStrings() {
    final Set<String> strings = getDelegate().getAllLookupStrings();
    final THashSet<String> result = new THashSet<String>();
    result.addAll(strings);
    result.add(getLookupString());
    return result;
  }

  @NotNull
  @Override
  public String toString() {
    String qualifierText = myQualifier.toString();
    if (myQualifier.getObject() instanceof PsiMethod) {
      qualifierText += "()";
    }
    return qualifierText + "." + getDelegate();
  }


  @Override
  public void renderElement(LookupElementPresentation presentation) {
    final MemorizingLookupElementPresentation qualifierPresentation = new MemorizingLookupElementPresentation(presentation);
    myQualifier.renderElement(qualifierPresentation);
    final String name = qualifierPresentation.getItemText();
    final String qualifierText = myQualifier instanceof CastingLookupElementDecorator ? "(" + name + ")" : name;

    super.renderElement(new DecoratingLookupElementPresentation(presentation) {
      @Override
      public void setItemText(@Nullable String text) {
        super.setItemText(qualifierText + "." + text);
      }

      @Override
      public void setItemText(@Nullable String text, boolean strikeout, boolean bold) {
        super.setItemText(qualifierText + "." + text, strikeout, bold);
      }
    });
 }

  @Override
  public void handleInsert(InsertionContext context) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN);

    final Document document = context.getEditor().getDocument();
    document.replaceString(context.getStartOffset(), context.getTailOffset(), ";");
    final InsertionContext qualifierContext = CompletionUtil.emulateInsertion(context, context.getStartOffset(), myQualifier, (char)0);

    if (shouldParenthesizeQualifier(qualifierContext.getFile(), context.getStartOffset(), qualifierContext.getTailOffset())) {
      final String space = CodeStyleSettingsManager.getSettings(qualifierContext.getProject()).SPACE_WITHIN_PARENTHESES ? " " : "";
      document.insertString(context.getStartOffset(), "(" + space);
      document.insertString(qualifierContext.getTailOffset(), space + ")");
    }

    final char atTail = document.getCharsSequence().charAt(context.getTailOffset() - 1);
    assert atTail == ';' : atTail;
    document.replaceString(context.getTailOffset() - 1, context.getTailOffset(), ".");

    CompletionUtil.emulateInsertion(getDelegate(), context.getTailOffset(), context);
  }

  private static boolean shouldParenthesizeQualifier(final PsiFile file, final int startOffset, final int endOffset) {
    PsiElement element = file.findElementAt(startOffset);
    if (element == null) {
      return false;
    }

    PsiElement last = element;
    while (element != null && element.getTextRange().getStartOffset() >= startOffset && element.getTextRange().getEndOffset() <= endOffset) {
      last = element;
      element = element.getParent();
    }
    PsiExpression expr = PsiTreeUtil.getParentOfType(last, PsiExpression.class, false);
    if (expr == null || expr.getTextRange().getEndOffset() > endOffset) {
      return true;
    }

    if (expr instanceof PsiReferenceExpression || expr instanceof PsiMethodCallExpression) {
      return false;
    }

    return true;
  }

  @NotNull
  private LookupElement getComparableQualifier() {
    final CastingLookupElementDecorator casting = myQualifier.as(CastingLookupElementDecorator.class);
    return casting == null ? myQualifier : casting.getDelegate();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    return getComparableQualifier().equals(((JavaChainLookupElement)o).getComparableQualifier());
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + getComparableQualifier().hashCode();
  }

  public PsiType getType() {
    return JavaCompletionUtil.getQualifiedMemberReferenceType(JavaCompletionUtil.getLookupElementType(myQualifier), (PsiMember)getObject());
  }

}
