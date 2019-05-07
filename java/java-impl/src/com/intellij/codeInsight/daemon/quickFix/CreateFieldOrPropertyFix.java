// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyMemberType;
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

  private final SmartPsiElementPointer<PsiClass> myClass;
  private final String myName;
  private final PsiType myType;
  private final PropertyMemberType myMemberType;
  private final PsiAnnotation[] myAnnotations;

  public CreateFieldOrPropertyFix(@NotNull PsiClass aClass,
                                  String name,
                                  PsiType type,
                                  @NotNull PropertyMemberType memberType,
                                  PsiAnnotation[] annotations) {
    myClass = SmartPointerManager.createPointer(aClass);
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
    PsiClass aClass = myClass.getElement();
    if (aClass == null) return;
    final PsiFile file = aClass.getContainingFile();
    final Editor editor = CodeInsightUtil.positionCursorAtLBrace(project, aClass.getContainingFile(), aClass);
    if (editor != null) {
      WriteCommandAction.writeCommandAction(project, file)
                        .withGlobalUndo()
                        .run(() -> generateMembers(project, editor));
    }
  }

  private void generateMembers(final Project project, final Editor editor) {
    try {
      PsiClass aClass = myClass.getElement();
      if (aClass == null) return;
      List<? extends GenerationInfo> prototypes = new GenerateFieldOrPropertyHandler(myName, myType, myMemberType, myAnnotations)
          .generateMemberPrototypes(aClass, ClassMember.EMPTY_ARRAY);
      prototypes = GenerateMembersUtil.insertMembersAtOffset(aClass, editor.getCaretModel().getOffset(), prototypes);
      if (prototypes.isEmpty()) return;
      final PsiElement scope = prototypes.get(0).getPsiMember().getContext();
      assert scope != null;
      final Expression expression = new EmptyExpression() {
        @Override
        public Result calculateResult(final ExpressionContext context) {
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