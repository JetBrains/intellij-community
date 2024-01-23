// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.encapsulateFields;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EncapsulateFieldsHandler implements PreviewableRefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance(EncapsulateFieldsHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    List<PsiElement> elements =
      CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, file, null,
                                                                e -> e instanceof PsiField field && field.getContainingClass() != null ||
                                                                     e instanceof PsiClass);
    if (elements.isEmpty()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("error.wrong.caret.position.class"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.ENCAPSULATE_FIELDS);
      return;
    }

    invoke(project, elements.toArray(PsiElement.EMPTY_ARRAY), dataContext);
  }

  /**
   * if elements.length == 1 the expected value is either PsiClass or PsiField
   * if elements.length > 1 the expected values are PsiField objects only
   */
  @Override
  public void invoke(@NotNull final Project project, final PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length == 0) {
      return;
    }
    PsiElement containingClass = elements[0];
    if (containingClass instanceof PsiField field) {
      containingClass = field.getContainingClass();
    }
    if (!(containingClass instanceof PsiClass)) {
      return;
    }
    PsiElement finalContainingClass = containingClass;

    List<SmartPsiElementPointer<PsiElement>> smartElements = new ArrayList<>();
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    for (PsiElement psiElement : elements) {
      if (psiElement == null) {
        continue;
      }
      smartElements.add(smartPointerManager.createSmartPsiElementPointer(psiElement));
    }
    ReadAction.nonBlocking(() -> {
        PsiClass aClass = null;
        Runnable callback;
        final HashSet<PsiField> preselectedFields = new HashSet<>();
        if (smartElements.size() == 1) {
          PsiElement element = smartElements.get(0).getElement();
          if (element == null) {
            return null;
          }
          if (element instanceof PsiClass) {
            aClass = (PsiClass)elements[0];
          }
          else if (element instanceof PsiField field) {
            aClass = field.getContainingClass();
            preselectedFields.add(field);
          }
          else {
            return null;
          }
        }
        else {
          for (SmartPsiElementPointer<PsiElement> smartElement : smartElements) {
            PsiElement element = smartElement.getElement();
            if (element == null) {
              return null;
            }
            if (!(element instanceof PsiField field)) {
              return null;
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
                callback = () ->
                {
                  String message = RefactoringBundle.getCannotRefactorMessage(
                    JavaRefactoringBundle.message("fields.to.be.refactored.should.belong.to.the.same.class"));
                  Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
                  CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.ENCAPSULATE_FIELDS);
                };
                return callback;
              }
            }
          }
        }

        LOG.assertTrue(aClass != null);
        final List<PsiField> fields = ContainerUtil.filter(aClass.getFields(), field -> !(field instanceof PsiEnumConstant));
        if (fields.isEmpty()) {
          callback = () -> {
            CommonRefactoringUtil.showErrorHint(project, CommonDataKeys.EDITOR.getData(dataContext),
                                                JavaRefactoringBundle.message("encapsulate.fields.nothing.todo.warning.message"),
                                                getRefactoringName(), HelpID.ENCAPSULATE_FIELDS);
          };
          return callback;
        }

        if (aClass.isInterface()) {
          callback = () -> {
            String message = RefactoringBundle.getCannotRefactorMessage(
              JavaRefactoringBundle.message("encapsulate.fields.refactoring.cannot.be.applied.to.interface"));
            Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
            CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.ENCAPSULATE_FIELDS);
          };
          return callback;
        }
        EncapsulateFieldsDialog.EncapsulateFieldsContainer container = prepare(aClass, preselectedFields);
        PsiClass finalAClass = aClass;
        callback = () -> {
          EncapsulateFieldsDialog dialog = getDialog(project, container);
          if (!CommonRefactoringUtil.checkReadOnlyStatus(project, finalAClass)) return;
          dialog.show();
        };
        return callback;
      })
      .finishOnUiThread(ModalityState.defaultModalityState(), callback -> {
        if (callback != null) {
          callback.run();
        }
      })
      .expireWhen(() -> !finalContainingClass.isValid())
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  @NotNull
  private static EncapsulateFieldsDialog.EncapsulateFieldsContainer prepare(@NotNull  PsiClass aClass,
                                                                            @NotNull Set<PsiField> preselectedFields) {
    return EncapsulateFieldsDialog.EncapsulateFieldsContainer.create(aClass, preselectedFields, new JavaEncapsulateFieldHelper());
  }

  protected static EncapsulateFieldsDialog createDialog(@Nullable Project project,
                                                        @NotNull PsiClass aClass,
                                                        @NotNull Set<PsiField> preselectedFields) {
    EncapsulateFieldsDialog.EncapsulateFieldsContainer container = prepare(aClass, preselectedFields);
    return getDialog(project, container);
  }

  @NotNull
  private static EncapsulateFieldsDialog getDialog(@Nullable  Project project,
                                                   @NotNull EncapsulateFieldsDialog.EncapsulateFieldsContainer container) {
    return new EncapsulateFieldsDialog(project, container);
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
        return VariableAccessUtils.getVariableReferences(field);
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