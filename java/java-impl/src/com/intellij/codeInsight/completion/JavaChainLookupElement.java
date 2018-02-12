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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.TypedLookupItem;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.ClassConditionKey;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author peter
 */
public class JavaChainLookupElement extends LookupElementDecorator<LookupElement> implements TypedLookupItem {
  public static final Key<Boolean> CHAIN_QUALIFIER = Key.create("CHAIN_QUALIFIER");
  public static final ClassConditionKey<JavaChainLookupElement> CLASS_CONDITION_KEY = ClassConditionKey.create(JavaChainLookupElement.class);
  private final LookupElement myQualifier;
  private final String mySeparator;

  public JavaChainLookupElement(LookupElement qualifier, LookupElement main) {
    this(qualifier, main, ".");
  }
  public JavaChainLookupElement(LookupElement qualifier, LookupElement main, String separator) {
    super(main);
    myQualifier = qualifier;
    mySeparator = separator;
  }

  @NotNull
  @Override
  public String getLookupString() {
    return maybeAddParentheses(myQualifier.getLookupString()) + mySeparator + getDelegate().getLookupString();
  }

  public LookupElement getQualifier() {
    return myQualifier;
  }

  @Override
  public Set<String> getAllLookupStrings() {
    final Set<String> strings = getDelegate().getAllLookupStrings();
    final THashSet<String> result = new THashSet<>();
    result.addAll(strings);
    result.add(getLookupString());
    return result;
  }

  @NotNull
  @Override
  public String toString() {
    return maybeAddParentheses(myQualifier.toString()) + mySeparator + getDelegate();
  }

  private String maybeAddParentheses(String s) {
    return getQualifierObject() instanceof PsiMethod ? s + "()" : s;
  }

  @Nullable
  private Object getQualifierObject() {
    Object qObject = myQualifier.getObject();
    if (qObject instanceof ResolveResult) {
      qObject = ((ResolveResult)qObject).getElement();
    }
    return qObject;
  }

  @Override
  public boolean isValid() {
    return super.isValid() && myQualifier.isValid();
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    super.renderElement(presentation);
    final LookupElementPresentation qualifierPresentation = new LookupElementPresentation();
    myQualifier.renderElement(qualifierPresentation);
    String name = maybeAddParentheses(qualifierPresentation.getItemText());
    final String qualifierText = myQualifier.as(CastingLookupElementDecorator.CLASS_CONDITION_KEY) != null ? "(" + name + ")" : name;
    presentation.setItemText(qualifierText + mySeparator + presentation.getItemText());

    if (myQualifier instanceof JavaPsiClassReferenceElement) {
      presentation.appendTailText(((JavaPsiClassReferenceElement)myQualifier).getLocationString(), false);
    }
    if (qualifierPresentation.isStrikeout()) {
      presentation.setStrikeout(true);
    }
  }

  @Override
  public void handleInsert(InsertionContext context) {
    final Document document = context.getEditor().getDocument();
    document.replaceString(context.getStartOffset(), context.getTailOffset(), ";");
    myQualifier.putUserData(CHAIN_QUALIFIER, true);
    final InsertionContext qualifierContext = CompletionUtil.emulateInsertion(context, context.getStartOffset(), myQualifier);
    OffsetKey oldStart = context.trackOffset(context.getStartOffset(), false);

    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(document);

    int start = CharArrayUtil.shiftForward(context.getDocument().getCharsSequence(), context.getStartOffset(), " \t\n");
    if (shouldParenthesizeQualifier(context.getFile(), start, qualifierContext.getTailOffset())) {
      final String space = CodeStyleSettingsManager.getSettings(qualifierContext.getProject())
                             .getCommonSettings(JavaLanguage.INSTANCE).SPACE_WITHIN_PARENTHESES ? " " : "";
      document.insertString(start, "(" + space);
      document.insertString(qualifierContext.getTailOffset(), space + ")");
    }

    char atTail = document.getCharsSequence().charAt(qualifierContext.getTailOffset());
    if (atTail != ';') {
      return;
    }
    document.replaceString(qualifierContext.getTailOffset(), qualifierContext.getTailOffset() + 1, mySeparator);

    CompletionUtil.emulateInsertion(getDelegate(), qualifierContext.getTailOffset() + mySeparator.length(), context);
    context.commitDocument();

    int formatStart = context.getOffset(oldStart);
    int formatEnd = context.getTailOffset();
    if (formatStart >= 0 && formatEnd >= 0) {
      CodeStyleManager.getInstance(context.getProject()).reformatText(context.getFile(), formatStart, formatEnd);
    }
  }

  protected boolean shouldParenthesizeQualifier(final PsiFile file, final int startOffset, final int endOffset) {
    PsiElement element = file.findElementAt(startOffset);
    if (element == null) {
      return false;
    }

    PsiElement last = element;
    while (element != null &&
           element.getTextRange().getStartOffset() >= startOffset && element.getTextRange().getEndOffset() <= endOffset &&
           !(element instanceof PsiExpressionStatement)) {
      last = element;
      element = element.getParent();
    }
    PsiExpression expr = PsiTreeUtil.getParentOfType(last, PsiExpression.class, false, PsiClass.class);
    if (expr == null) {
      return false;
    }
    if (expr.getTextRange().getEndOffset() > endOffset) {
      return true;
    }

    if (expr instanceof PsiJavaCodeReferenceElement ||
        expr instanceof PsiMethodCallExpression ||
        expr instanceof PsiNewExpression ||
        expr instanceof PsiArrayAccessExpression) {
      return false;
    }

    return true;
  }

  @NotNull
  private LookupElement getComparableQualifier() {
    final CastingLookupElementDecorator casting = myQualifier.as(CastingLookupElementDecorator.CLASS_CONDITION_KEY);
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

  @Override
  public PsiType getType() {
    final Object object = getObject();
    if (object instanceof PsiMember) {
      return JavaCompletionUtil.getQualifiedMemberReferenceType(JavaCompletionUtil.getLookupElementType(myQualifier), (PsiMember)object);
    }
    return ((PsiVariable) object).getType();
  }
}
