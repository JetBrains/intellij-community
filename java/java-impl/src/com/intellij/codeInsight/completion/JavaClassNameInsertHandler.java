/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
* @author peter
*/
class JavaClassNameInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  static final InsertHandler<JavaPsiClassReferenceElement> JAVA_CLASS_INSERT_HANDLER = new JavaClassNameInsertHandler();

  @Override
  public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
    int offset = context.getTailOffset() - 1;
    final PsiFile file = context.getFile();
    if (PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiImportStatementBase.class, false) != null) {
      final PsiJavaCodeReferenceElement ref = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiJavaCodeReferenceElement.class, false);
      final String qname = item.getQualifiedName();
      if (qname != null && (ref == null || !qname.equals(ref.getCanonicalText()))) {
        AllClassesGetter.INSERT_FQN.handleInsert(context, item);
      }
      return;
    }

    PsiElement position = file.findElementAt(offset);
    PsiClass psiClass = item.getObject();
    final Project project = context.getProject();

    final Editor editor = context.getEditor();
    final char c = context.getCompletionChar();
    if (c == '#') {
      context.setLaterRunnable(new Runnable() {
        @Override
        public void run() {
          new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor);
        }
      });
    } else if (c == '.' && PsiTreeUtil.getParentOfType(position, PsiParameterList.class) == null) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }

    if (PsiTreeUtil.getParentOfType(position, PsiDocComment.class, false) != null &&
        CodeStyleSettingsManager.getSettings(project).USE_FQ_CLASS_NAMES_IN_JAVADOC) {
      AllClassesGetter.INSERT_FQN.handleInsert(context, item);
      return;
    }

    if (position != null) {
      PsiElement parent = position.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)parent;
        if (PsiTreeUtil.getParentOfType(position, PsiDocTag.class) != null && ref.isReferenceTo(psiClass)) {
          return;
        }
      }
    }

    OffsetKey refEnd = context.trackOffset(context.getTailOffset(), false);

    boolean fillTypeArgs = context.getCompletionChar() == '<';
    if (fillTypeArgs) {
      context.setAddCompletionChar(false);
    }

    PsiTypeLookupItem.addImportForItem(context, psiClass);
    if (context.getOffset(refEnd) < 0) {
      return;
    }

    context.setTailOffset(context.getOffset(refEnd));

    context.commitDocument();
    if (shouldInsertParentheses(file.findElementAt(context.getTailOffset() - 1))) {
      if (ConstructorInsertHandler.insertParentheses(context, item, psiClass, false)) {
        fillTypeArgs |= psiClass.hasTypeParameters() && PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5);
      }
    }
    else if (insertingAnnotationWithParameters(context, item)) {
      JavaCompletionUtil.insertParentheses(context, item, false, true);
    }

    if (fillTypeArgs && context.getCompletionChar() != '(') {
      JavaCompletionUtil.promptTypeArgs(context, context.getOffset(refEnd));
    }
  }

  private static boolean shouldInsertParentheses(PsiElement position) {
    final PsiJavaCodeReferenceElement ref = PsiTreeUtil.getParentOfType(position, PsiJavaCodeReferenceElement.class);
    if (ref == null) {
      return false;
    }

    final PsiReferenceParameterList parameterList = ref.getParameterList();
    if (parameterList != null && parameterList.getTextLength() > 0) {
      return false;
    }

    final PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(ref);
    if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
      for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes((PsiExpression)prevElement.getParent(), true)) {
        if (info.getType() instanceof PsiArrayType) {
          return false;
        }
      }

      return true;
    }

    return false;
  }

  private static boolean insertingAnnotationWithParameters(InsertionContext context, LookupElement item) {
    final Object obj = item.getObject();
    if (!(obj instanceof PsiClass) || !((PsiClass)obj).isAnnotationType()) return false;

    PsiElement leaf = context.getFile().findElementAt(context.getStartOffset());
    if (psiElement(PsiIdentifier.class).withParents(PsiJavaCodeReferenceElement.class, PsiAnnotation.class).accepts(leaf)) {
      return shouldHaveAnnotationParameters((PsiClass)obj);
    }
    return false;
  }

  static boolean shouldHaveAnnotationParameters(PsiClass annoClass) {
    for (PsiMethod m : annoClass.getMethods()) {
      if (!PsiUtil.isAnnotationMethod(m)) continue;
      if (((PsiAnnotationMethod)m).getDefaultValue() == null) return true;
    }
    return false;
  }
}
