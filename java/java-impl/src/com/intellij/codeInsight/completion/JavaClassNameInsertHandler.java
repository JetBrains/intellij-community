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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

/**
* @author peter
*/
class JavaClassNameInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaClassNameInsertHandler");
  static final InsertHandler<JavaPsiClassReferenceElement> JAVA_CLASS_INSERT_HANDLER = new JavaClassNameInsertHandler();

  public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
    final char c = context.getCompletionChar();

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
    final boolean annotation = insertingAnnotation(context, item);

    final Editor editor = context.getEditor();
    if (c == '#') {
      context.setLaterRunnable(new Runnable() {
        public void run() {
          new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project, editor);
        }
      });
    } else if (c == '.' && PsiTreeUtil.getParentOfType(position, PsiParameterList.class) == null) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
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
    context.setTailOffset(context.getOffset(refEnd));

    if (shouldInsertParentheses(file.findElementAt(context.getTailOffset() - 1))) {
      if (ConstructorInsertHandler.insertParentheses(context, item, psiClass, false)) {
        fillTypeArgs |= psiClass.hasTypeParameters() && PsiUtil.getLanguageLevel(file).isAtLeast(LanguageLevel.JDK_1_5);
      }
    }
    else if (insertingAnnotationWithParameters(context, item)) {
      JavaCompletionUtil.insertParentheses(context, item, false, true);
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
    }

    if (annotation) {
      // Check if someone inserts annotation class that require @
      PsiElement elementAt = file.findElementAt(context.getStartOffset());
      final PsiElement parentElement = elementAt != null ? elementAt.getParent():null;

      if (elementAt instanceof PsiIdentifier &&
          (PsiTreeUtil.getParentOfType(elementAt, PsiAnnotationParameterList.class) != null ||
           parentElement instanceof PsiErrorElement && parentElement.getParent() instanceof PsiJavaFile // top level annotation without @
          )
          && isAtTokenNeeded(context)) {
        int expectedOffsetForAtToken = elementAt.getTextRange().getStartOffset();
        context.getDocument().insertString(expectedOffsetForAtToken, "@");
      }
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
    if(insertingAnnotation(context, item)) {
      final Document document = context.getEditor().getDocument();
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
      PsiElement elementAt = context.getFile().findElementAt(context.getStartOffset());
      if (elementAt instanceof PsiIdentifier) {
        final PsiModifierListOwner parent = PsiTreeUtil.getParentOfType(elementAt, PsiModifierListOwner.class, false, PsiCodeBlock.class);
        if (parent != null) {
          for (PsiMethod m : ((PsiClass)item.getObject()).getMethods()) {
            if (!PsiUtil.isAnnotationMethod(m)) continue;
            final PsiAnnotationMemberValue defaultValue = ((PsiAnnotationMethod)m).getDefaultValue();
            if (defaultValue == null) return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean insertingAnnotation(InsertionContext context, LookupElement item) {
    final Object obj = item.getObject();
    if (!(obj instanceof PsiClass) || !((PsiClass)obj).isAnnotationType()) return false;

    final Document document = context.getEditor().getDocument();
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(document);
    final int offset = context.getStartOffset();

    final PsiFile file = context.getFile();

    if (PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiImportStatement.class, false) != null) return false;

    //outside of any class: we are surely inserting an annotation
    if (PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiClass.class, false) == null) return true;

    //the easiest check that there's a @ before the identifier
    return PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiAnnotation.class, false) != null;

  }

  private static boolean isAtTokenNeeded(InsertionContext myContext) {
    HighlighterIterator iterator = ((EditorEx)myContext.getEditor()).getHighlighter().createIterator(myContext.getStartOffset());
    LOG.assertTrue(iterator.getTokenType() == JavaTokenType.IDENTIFIER);
    iterator.retreat();
    if (iterator.getTokenType() == TokenType.WHITE_SPACE) iterator.retreat();
    return iterator.getTokenType() != JavaTokenType.AT && iterator.getTokenType() != JavaTokenType.DOT;
  }
}
