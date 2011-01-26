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
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;

/**
* @author peter
*/
class JavaClassNameInsertHandler implements InsertHandler<JavaPsiClassReferenceElement> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaClassNameInsertHandler");
  static final InsertHandler<JavaPsiClassReferenceElement> JAVA_CLASS_INSERT_HANDLER = new JavaClassNameInsertHandler();

  public void handleInsert(final InsertionContext context, final JavaPsiClassReferenceElement item) {
    final char c = context.getCompletionChar();

    if (c != '.' && c != ' ' && c != '#') {
      context.setAddCompletionChar(false);
    }

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
          new CodeCompletionHandlerBase(CompletionType.BASIC).invoke(project, editor, file);
        }
      });
    }

    if (position != null) {
      PsiElement parent = position.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)parent;
        if (PsiTreeUtil.getParentOfType(position, PsiDocTag.class) != null) {
          if (ref.isReferenceTo(psiClass)) {
            return;
          }
        }
        final PsiReferenceParameterList parameterList = ref.getParameterList();
        if (parameterList != null && parameterList.getTextLength() > 0) {
          return;
        }
      }
    }

    if (shouldInsertParentheses(psiClass, position)) {
      if (ConstructorInsertHandler.insertParentheses(context, item, psiClass, false)) {
        AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
      }
    }
    else if (insertingAnnotationWithParameters(context, item)) {
      JavaCompletionUtil.insertParentheses(context, item, false, true);
      AutoPopupController.getInstance(project).autoPopupParameterInfo(editor, null);
    }

    LOG.assertTrue(context.getTailOffset() >= 0);
    String docText = context.getDocument().getText();
    DefaultInsertHandler.addImportForItem(context, item);
    if (context.getTailOffset() < 0) {
      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
        LOG.error("Tail offset degraded: " + context.getStartOffset() + "; " + docText);
      } else {
        LOG.error("Tail offset degraded after insertion");
      }
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

  }

  private static boolean shouldInsertParentheses(PsiClass psiClass, PsiElement position) {
    final PsiJavaCodeReferenceElement ref = PsiTreeUtil.getParentOfType(position, PsiJavaCodeReferenceElement.class);
    final PsiElement prevElement = FilterPositionUtil.searchNonSpaceNonCommentBack(ref);
    if (prevElement != null && prevElement.getParent() instanceof PsiNewExpression) {
      final PsiClassType classType = JavaPsiFacade.getElementFactory(position.getProject()).createType(psiClass);

      for (ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes((PsiExpression)prevElement.getParent(), true)) {
        final PsiType type = info.getType();

        if (info.isArrayTypeInfo()) {
          return false;
        }

        if (type instanceof PsiClassType && ((PsiClassType)type).rawType().isAssignableFrom(classType)) {
          return true;
        }
      }
      return !JavaCompletionUtil.hasAccessibleInnerClass(psiClass, position);
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
            if (!(m instanceof PsiAnnotationMethod)) continue;
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
