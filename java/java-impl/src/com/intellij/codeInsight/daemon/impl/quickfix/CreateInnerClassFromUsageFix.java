// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public final class CreateInnerClassFromUsageFix extends CreateClassFromUsageBaseFix {
  public CreateInnerClassFromUsageFix(final PsiJavaCodeReferenceElement refElement, final CreateClassKind kind) {
    super(kind, refElement);
  }

  @Override
  public String getText(String varName) {
    return QuickFixBundle.message("create.inner.class.from.usage.text", myKind.getDescriptionAccusative(), varName);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null) return;
    final String superClassName = getSuperClassName(element);
    PsiClass[] targets = getPossibleTargets(element);
    LOG.assertTrue(targets.length > 0);
    if (targets.length == 1) {
      doInvoke(targets[0], superClassName);
    }
    else {
      chooseTargetClass(targets, editor, superClassName);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiJavaCodeReferenceElement element = getRefElement();
    if (element == null) return IntentionPreviewInfo.EMPTY;
    element = PsiTreeUtil.findSameElementInCopy(element, psiFile);
    PsiClass[] targets = getPossibleTargets(element);
    if (targets.length == 0) return IntentionPreviewInfo.EMPTY;
    doInvoke(targets[0], getSuperClassName(element));
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final PsiFile psiFile) {
    return super.isAvailable(project, editor, psiFile) && getPossibleTargets(getRefElement()).length > 0;
  }

  private static PsiClass @NotNull [] getPossibleTargets(final PsiJavaCodeReferenceElement element) {
    List<PsiClass> result = new ArrayList<>();
    PsiElement run = element;
    PsiMember contextMember = PsiTreeUtil.getParentOfType(run, PsiMember.class);

    while (contextMember != null) {
      if (contextMember instanceof PsiClass && !(contextMember instanceof PsiTypeParameter)) {
        if (!isUsedInExtends(run, (PsiClass)contextMember)) {
          result.add((PsiClass)contextMember);
        }
      }
      run = contextMember;
      contextMember = PsiTreeUtil.getParentOfType(run, PsiMember.class);
    }

    return result.isEmpty() ? PsiClass.EMPTY_ARRAY : result.toArray(PsiClass.EMPTY_ARRAY);
  }

  private static boolean isUsedInExtends(PsiElement element, PsiClass psiClass) {
    final PsiReferenceList extendsList = psiClass.getExtendsList();
    final PsiReferenceList implementsList = psiClass.getImplementsList();
    if (extendsList != null && PsiTreeUtil.isAncestor(extendsList, element, false)) {
      return true;
    }

    return implementsList != null && PsiTreeUtil.isAncestor(implementsList, element, false);
  }

  private void chooseTargetClass(PsiClass[] classes, final Editor editor, final String superClassName) {
    PsiClassListCellRenderer renderer = new PsiClassListCellRenderer();
    final IPopupChooserBuilder<PsiClass> builder = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(List.of(classes))
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setRenderer(renderer)
      .setTitle(QuickFixBundle.message("target.class.chooser.title"))
      .setItemChosenCallback((aClass) -> doInvoke(aClass, superClassName));
    renderer.installSpeedSearch(builder);
    builder.createPopup().showInBestPositionFor(editor);
  }

  private void doInvoke(final PsiClass aClass, final String superClassName) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement ref;
    if (!aClass.isPhysical()) {
      ref = PsiTreeUtil.findSameElementInCopy(getRefElement(), aClass.getContainingFile());
    } else {
      ref = getRefElement();
    }
    assert ref != null;
    String refName = ref.getReferenceName();
    LOG.assertTrue(refName != null);
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
    PsiClass created = myKind.create(elementFactory, refName);
    final PsiModifierList modifierList = created.getModifierList();
    LOG.assertTrue(modifierList != null);
    if (aClass.isInterface() || PsiUtil.isLocalOrAnonymousClass(aClass)) {
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    } else {
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    }
    if (CommonJavaRefactoringUtil.isInStaticContext(ref, aClass) && !aClass.isInterface()) {
      modifierList.setModifierProperty(PsiModifier.STATIC, true);
    }
    if (superClassName != null) {
      CreateFromUsageUtils.setupSuperClassReference(created, superClassName);
    }
    CreateFromUsageBaseFix.setupGenericParameters(created, ref);

    if (!aClass.isPhysical()) {
      PsiClass add = (PsiClass)aClass.add(created);
      PsiDeconstructionPattern pattern = getDeconstructionPattern(ref);
      if (pattern != null) {
        setupRecordFromDeconstructionPattern(add, pattern, getText());
      }
    }
    else {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(aClass)) return;
      WriteCommandAction.runWriteCommandAction(aClass.getProject(), getText(), null,
                                               () -> {
                                                 PsiClass add = (PsiClass)aClass.add(created);
                                                 ref.bindToElement(add);
                                                 PsiDeconstructionPattern pattern = getDeconstructionPattern(ref);
                                                 if (pattern != null) {
                                                   setupRecordFromDeconstructionPattern(add, pattern, getText());
                                                 }
                                               },
                                               aClass.getContainingFile());
    }
  }

  static void setupRecordFromDeconstructionPattern(@Nullable PsiClass aClass, final @NotNull PsiDeconstructionPattern pattern,
                                                   @IntentionName @NotNull String text) {
    if (aClass == null) return;

    final PsiJavaCodeReferenceElement classReference = pattern.getTypeElement().getInnermostComponentReferenceElement();
    if (classReference != null && aClass.isPhysical()) {
      classReference.bindToElement(aClass);
    }

    PsiDeconstructionList deconstructionList = pattern.getDeconstructionList();
    final Project project = aClass.getProject();
    if (deconstructionList.getDeconstructionComponents().length != 0) {
      TemplateBuilderImpl templateBuilder = createRecordHeaderTemplate(aClass, deconstructionList);
      CreateFromUsageBaseFix.startTemplate(project, aClass, templateBuilder.buildTemplate(), text);
    }
    else {
      CodeInsightUtil.positionCursor(project, aClass.getContainingFile(), ObjectUtils.notNull(aClass.getNameIdentifier(), aClass));
    }
  }

  private static @NotNull TemplateBuilderImpl createRecordHeaderTemplate(PsiClass aClass, PsiDeconstructionList list) {
    TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(aClass);
    PsiRecordHeader header = aClass.getRecordHeader();
    CreateRecordFromNewFix.setupRecordComponentsFromPattern(header, templateBuilder, list);
    return templateBuilder;
  }
}
