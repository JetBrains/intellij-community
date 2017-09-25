/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateFieldOrPropertyHandler;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.GenerationInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyMemberType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
*/
public class CreateFieldOrPropertyFix implements IntentionAction, LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.quickFix.CreateFieldOrPropertyFix");

  private final PsiClass myClass;
  private final String myName;
  private final PsiType myType;
  private final PropertyMemberType myMemberType;
  private final PsiAnnotation[] myAnnotations;

  public CreateFieldOrPropertyFix(final PsiClass aClass,
                                  final String name,
                                  final PsiType type,
                                  @NotNull PropertyMemberType memberType,
                                  final PsiAnnotation[] annotations) {
    myClass = aClass;
    myName = name;
    myType = type;
    myMemberType = memberType;
    myAnnotations = annotations;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message(myMemberType == PropertyMemberType.FIELD ? "create.field.text":"create.property.text", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    applyFixInner(project);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    applyFixInner(project);
  }

  private void applyFixInner(final Project project) {
    final PsiFile file = myClass.getContainingFile();
    final Editor editor = CodeInsightUtil.positionCursorAtLBrace(project, myClass.getContainingFile(), myClass);
    if (editor != null) {
      new WriteCommandAction(project, file) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          generateMembers(project, editor, file);
        }

        @Override
        protected boolean isGlobalUndoAction() {
          return true; // todo check
        }
      }.execute();
    }
  }

  private void generateMembers(final Project project, final Editor editor, final PsiFile file) {
    try {
      List<? extends GenerationInfo> prototypes = new GenerateFieldOrPropertyHandler(myName, myType, myMemberType, myAnnotations).generateMemberPrototypes(myClass, ClassMember.EMPTY_ARRAY);
      prototypes = GenerateMembersUtil.insertMembersAtOffset(myClass, editor.getCaretModel().getOffset(), prototypes);
      if (prototypes.isEmpty()) return;
      final PsiElement scope = prototypes.get(0).getPsiMember().getContext();
      assert scope != null;
      final Expression expression = new EmptyExpression() {
        @Override
        public com.intellij.codeInsight.template.Result calculateResult(final ExpressionContext context) {
          return new TextResult(myType.getCanonicalText());
        }
      };
      final TemplateBuilderImpl builder = new TemplateBuilderImpl(scope);
      boolean first = true;
      @NonNls final String TYPE_NAME_VAR = "TYPE_NAME_VAR";
      for (GenerationInfo prototype : prototypes) {
        final PsiTypeElement typeElement = PropertyUtilBase.getPropertyTypeElement(prototype.getPsiMember());
        if (first) {
          first = false;
          builder.replaceElement(typeElement, TYPE_NAME_VAR, expression, true);
        }
        else {
          builder.replaceElement(typeElement, TYPE_NAME_VAR, TYPE_NAME_VAR, false);
        }
      }
      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
      editor.getCaretModel().moveToOffset(scope.getTextRange().getStartOffset());
      TemplateManager.getInstance(project).startTemplate(editor, builder.buildInlineTemplate());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

}