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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
public class JavaChainLookupElement extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  private final LookupElement myQualifier;

  private JavaChainLookupElement(LookupElement qualifier, LookupElement main) {
    super(main);
    myQualifier = qualifier;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return maybeAddParentheses(myQualifier.getLookupString()) + "." + getDelegate().getLookupString();
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
    return maybeAddParentheses(myQualifier.toString()) + "." + getDelegate();
  }

  private String maybeAddParentheses(String s) {
    return myQualifier.getObject() instanceof PsiMethod ? s + "()" : s;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    final LookupElementPresentation qualifierPresentation = new LookupElementPresentation();
    myQualifier.renderElement(qualifierPresentation);
    String name = maybeAddParentheses(qualifierPresentation.getItemText());
    final String qualifierText = myQualifier.as(CastingLookupElementDecorator.class) != null ? "(" + name + ")" : name;
    presentation.setItemText(qualifierText + "." + presentation.getItemText());
  }

  @Override
  public void handleInsert(InsertionContext context) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(JavaCompletionFeatures.SECOND_SMART_COMPLETION_CHAIN);

    final Document document = context.getEditor().getDocument();
    document.replaceString(context.getStartOffset(), context.getTailOffset(), ";");
    final InsertionContext qualifierContext = CompletionUtil.emulateInsertion(context, context.getStartOffset(), myQualifier);

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
    final CastingLookupElementDecorator casting = CastingLookupElementDecorator.from(myQualifier);
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
    final Object object = getObject();
    if (object instanceof PsiMember) {
      return JavaCompletionUtil.getQualifiedMemberReferenceType(JavaCompletionUtil.getLookupElementType(myQualifier), (PsiMember)object);
    }
    return ((PsiVariable) object).getType();
  }

  public static LookupElement chainElements(LookupElement qualifier, LookupElement main) {
    return new JavaChainLookupElement(qualifier, main);
  }
}
