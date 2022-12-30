// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.encapsulateFields;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.PreviewableRefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;

public class EncapsulateFieldsHandler implements PreviewableRefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(EncapsulateFieldsHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);
    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.ENCAPSULATE_FIELDS);
        return;
      }
      if (element instanceof PsiField field) {
        if (field.getContainingClass() == null) {
          String message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("the.field.should.be.declared.in.a.class"));
          CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.ENCAPSULATE_FIELDS);
          return;
        }
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      if (element instanceof PsiClass) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  /**
   * if elements.length == 1 the expected value is either PsiClass or PsiField
   * if elements.length > 1 the expected values are PsiField objects only
   */
  @Override
  public void invoke(@NotNull final Project project, final PsiElement @NotNull [] elements, DataContext dataContext) {
    PsiClass aClass = null;
    final HashSet<PsiField> preselectedFields = new HashSet<>();
    if (elements.length == 1) {
      if (elements[0] instanceof PsiClass) {
        aClass = (PsiClass) elements[0];
      } else if (elements[0] instanceof PsiField field) {
        aClass = field.getContainingClass();
        preselectedFields.add(field);
      } else {
        return;
      }
    } else {
      for (PsiElement element : elements) {
        if (!(element instanceof PsiField field)) {
          return;
        }
        if (aClass == null) {
          aClass = field.getContainingClass();
          preselectedFields.add(field);
        }
        else {
          if (aClass.equals(field.getContainingClass())) {
            preselectedFields.add(field);
          }
          else {
            String message = RefactoringBundle.getCannotRefactorMessage(
              JavaRefactoringBundle.message("fields.to.be.refactored.should.belong.to.the.same.class"));
            Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
            CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.ENCAPSULATE_FIELDS);
            return;
          }
        }
      }
    }

    LOG.assertTrue(aClass != null);
    final List<PsiField> fields = ContainerUtil.filter(aClass.getFields(), field -> !(field instanceof PsiEnumConstant));
    if (fields.isEmpty()) {
      CommonRefactoringUtil.showErrorHint(project, CommonDataKeys.EDITOR.getData(dataContext),
                                          JavaRefactoringBundle.message("encapsulate.fields.nothing.todo.warning.message"),
                                          getRefactoringName(), HelpID.ENCAPSULATE_FIELDS);
      return;
    }

    if (aClass.isInterface()) {
      String message = RefactoringBundle.getCannotRefactorMessage(
        JavaRefactoringBundle.message("encapsulate.fields.refactoring.cannot.be.applied.to.interface"));
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.ENCAPSULATE_FIELDS);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, aClass)) return;

    EncapsulateFieldsDialog dialog = createDialog(project, aClass, preselectedFields);
    dialog.show();
  }

  protected EncapsulateFieldsDialog createDialog(Project project, PsiClass aClass, HashSet<PsiField> preselectedFields) {
    return new EncapsulateFieldsDialog(
      project,
      aClass,
      preselectedFields,
      new JavaEncapsulateFieldHelper());
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull PsiElement element) {
    if (!(element instanceof PsiField field)) {
      return IntentionPreviewInfo.EMPTY;
    }
    final FieldDescriptor fieldDescriptor = new FieldDescriptorImpl(field, GenerateMembersUtil.suggestGetterName(field),
                                                                    GenerateMembersUtil.suggestSetterName(field),
                                                                    GenerateMembersUtil.generateGetterPrototype(field),
                                                                    GenerateMembersUtil.generateSetterPrototype(field));
    final EncapsulateFieldsDescriptor descriptor = new EncapsulateOnPreviewDescriptor(fieldDescriptor);
    final EncapsulateFieldsProcessor processor = new EncapsulateFieldsProcessor(project, descriptor) {
      @Override
      protected Iterable<PsiReferenceExpression> getFieldReferences(@NotNull PsiField field) {
        return VariableAccessUtils.getVariableReferences(field, field.getContainingFile());
      }
    };
    processor.performRefactoring(processor.findUsages());
    return IntentionPreviewInfo.DIFF;
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("encapsulate.fields.title");
  }

  static private class EncapsulateOnPreviewDescriptor implements EncapsulateFieldsDescriptor {
    private final FieldDescriptor myFieldDescriptor;

    EncapsulateOnPreviewDescriptor(FieldDescriptor fieldDescriptor) {
      myFieldDescriptor = fieldDescriptor;
    }

    @Override
    public FieldDescriptor[] getSelectedFields() {
      return new FieldDescriptor[]{myFieldDescriptor};
    }

    @Override
    public boolean isToEncapsulateGet() {
      return true;
    }

    @Override
    public boolean isToEncapsulateSet() {
      return true;
    }

    @Override
    public boolean isToUseAccessorsWhenAccessible() {
      return true;
    }

    @Override
    public String getFieldsVisibility() {
      return PsiModifier.PRIVATE;
    }

    @Override
    public String getAccessorsVisibility() {
      return PsiModifier.PUBLIC;
    }

    @Override
    public int getJavadocPolicy() {
      return DocCommentPolicy.MOVE;
    }

    @Override
    public PsiClass getTargetClass() {
      return myFieldDescriptor.getField().getContainingClass();
    }
  }
}