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
package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.*;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.util.MethodParenthesesHandler;
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class PsiMethodInsertHandler implements InsertHandler<LookupElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.simple.PsiMethodInsertHandler");
  public static final PsiMethodInsertHandler INSTANCE = new PsiMethodInsertHandler();

  public static void insertParentheses(final InsertionContext context, final LookupElement item, boolean overloadsMatter, boolean hasParams) {
    final Editor editor = context.getEditor();
    final TailType tailType = getTailType(item, context);
    final PsiFile file = context.getFile();

    context.setAddCompletionChar(false);


    final boolean needLeftParenth = isToInsertParenth(file.findElementAt(context.getStartOffset()));
    final boolean needRightParenth = shouldInsertRParenth(context.getCompletionChar(), tailType, hasParams);

    if (needLeftParenth) {
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(context.getProject());
      ParenthesesInsertHandler.getInstance(hasParams,
                                           styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES,
                                           styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES && hasParams,
                                           needRightParenth,
                                           styleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE
      ).handleInsert(context, item);
    }

    if (needLeftParenth && hasParams) {
      // Invoke parameters popup
      AutoPopupController.getInstance(file.getProject()).autoPopupParameterInfo(editor, overloadsMatter ? null : (PsiElement)item.getObject());
    }
    if (tailType == TailType.SMART_COMPLETION || needLeftParenth && needRightParenth) {
      tailType.processTail(editor, context.getTailOffset());
    }
  }

  public void handleInsert(final InsertionContext context, final LookupElement item) {
    final Editor editor = context.getEditor();
    final Document document = editor.getDocument();
    final PsiFile file = context.getFile();
    final int offset = editor.getCaretModel().getOffset();
    final PsiMethod method = (PsiMethod)item.getObject();

    final LookupElement[] allItems = context.getElements();
    final boolean overloadsMatter = allItems.length == 1 && item.getUserData(LookupItem.FORCE_SHOW_SIGNATURE_ATTR) == null;

    final boolean hasParams = MethodParenthesesHandler.hasParams(item, allItems, overloadsMatter, method);

    insertParentheses(context, item, overloadsMatter, hasParams);

    insertExplicitTypeParams(item, document, offset, file);

    final PsiType type = method.getReturnType();
    if (context.getCompletionChar() == '!' && type != null && PsiType.BOOLEAN.isAssignableFrom(type)) {
      PsiDocumentManager.getInstance(method.getProject()).commitDocument(document);
      final PsiMethodCallExpression methodCall = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiMethodCallExpression.class, false);
      if (methodCall != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH);
        document.insertString(methodCall.getTextRange().getStartOffset(), "!");
      }
    }

  }

  private static boolean shouldInsertRParenth(char completionChar, TailType tailType, boolean hasParams) {
    if (tailType == TailType.SMART_COMPLETION) {
      return false;
    }

    if (completionChar == '(' && !hasParams) {
      //it's highly probable that the user will type ')' next and it may not be overwritten if the flag is off
      return CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    }

    return true;
  }

  @NotNull
  private static TailType getTailType(final LookupElement item, InsertionContext context) {
    final char completionChar = context.getCompletionChar();
    if (completionChar == '!') return item instanceof LookupItem ? ((LookupItem)item).getTailType() : TailType.NONE;
    if (completionChar == '(') {
      final Object o = item.getObject();
      if (o instanceof PsiMethod) {
        final PsiMethod psiMethod = (PsiMethod)o;
        return psiMethod.getParameterList().getParameters().length > 0 || psiMethod.getReturnType() != PsiType.VOID
               ? TailType.NONE : TailType.SEMICOLON;
      } else if (o instanceof PsiClass) { // it may be a constructor
        return TailType.NONE;
      }
    }
    if (completionChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) return TailType.SMART_COMPLETION;
    if (!context.shouldAddCompletionChar()) {
      return TailType.NONE;
    }

    return LookupItem.handleCompletionChar(context.getEditor(), item, completionChar);
  }

  public static boolean isToInsertParenth(PsiElement place){
    if (place == null) return true;
    return !(place.getParent() instanceof PsiImportStaticReferenceElement);
  }

  private static void insertExplicitTypeParams(final LookupElement item, final Document document, final int offset, PsiFile file) {
    final PsiMethod method = (PsiMethod)item.getObject();
    if (!SmartCompletionDecorator.hasUnboundTypeParams(method)) {
      return;
    }

    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();

    PsiExpression expression = PsiTreeUtil.findElementOfClassAtOffset(file, offset - 1, PsiExpression.class, false);
    if (expression == null) return;

    final Project project = file.getProject();
    final ExpectedTypeInfo[] expectedTypes = ExpectedTypesProvider.getExpectedTypes(expression, true);
    if (expectedTypes == null) return;

    for (final ExpectedTypeInfo type : expectedTypes) {
      if (type.isInsertExplicitTypeParams()) {
        final OffsetMap map = new OffsetMap(document);
        final OffsetKey refOffsetKey = OffsetKey.create("refOffset");
        map.addOffset(refOffsetKey, offset - 1);

        final String typeParams = getTypeParamsText(method, type.getType());
        if (typeParams == null) {
          return;
        }
        final String qualifierText = getQualifierText(file, method, offset - 1);

        document.insertString(offset - method.getName().length(), qualifierText + typeParams);
        PsiDocumentManager.getInstance(project).commitDocument(document);

        final PsiReference reference = file.findReferenceAt(map.getOffset(refOffsetKey));
        if (reference instanceof PsiJavaCodeReferenceElement) {
          try {
            CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(
              JavaCodeStyleManager.getInstance(project).shortenClassReferences((PsiElement)reference));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        return;
      }
    }
    

  }

  private static String getQualifierText(PsiFile file, PsiMethod method, final int refOffset) {
    final PsiReference reference = file.findReferenceAt(refOffset);
    if (reference instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)reference).isQualified()) {
      return "";
    }

    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return "";
    }

    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return containingClass.getQualifiedName() + ".";
    }

    if (containingClass.getManager().areElementsEquivalent(containingClass, PsiTreeUtil.findElementOfClassAtOffset(file, refOffset, PsiClass.class, false))) {
      return "this.";
    }

    return containingClass.getQualifiedName() + ".this.";
  }

  @Nullable
  private static String getTypeParamsText(final PsiMethod method, PsiType expectedType) {
    final PsiSubstitutor substitutor = SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor(method, expectedType);
    assert substitutor != null;
    final PsiTypeParameter[] parameters = method.getTypeParameters();
    assert parameters.length > 0;
    final StringBuilder builder = new StringBuilder("<");
    boolean first = true;
    for (final PsiTypeParameter parameter : parameters) {
      if (!first) builder.append(", ");
      first = false;
      final PsiType type = substitutor.substitute(parameter);
      if (type == null || type instanceof PsiWildcardType || type instanceof PsiCapturedWildcardType) return null;

      final String text = type.getCanonicalText();
      if (text.indexOf('?') >= 0) return null;

      builder.append(text);
    }
    return builder.append(">").toString();
  }
}
