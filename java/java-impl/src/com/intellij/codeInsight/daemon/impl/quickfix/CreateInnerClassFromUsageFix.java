// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IPopupChooserBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDeconstructionList;
import com.intellij.psi.PsiDeconstructionPattern;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.ListSelectionModel;
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
  protected @IntentionName @Nullable String getAvailableText(@NotNull PsiJavaCodeReferenceElement element, int offset) {
    if (getPossibleTargets(element).length == 0) return null;
    return super.getAvailableText(element, offset);
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
    if (!aClass.isPhysical()) {
      PsiClass add = createInnerClass(aClass, ref, superClassName);
      PsiDeconstructionPattern pattern = getDeconstructionPattern(ref);
      if (pattern != null) {
        setupRecordFromDeconstructionPattern(add, pattern, getText());
      }
    }
    else {
      if (!FileModificationService.getInstance().preparePsiElementForWrite(aClass)) return;
      WriteCommandAction.runWriteCommandAction(aClass.getProject(), getText(), null,
                                               () -> {
                                                 PsiClass add = createInnerClass(aClass, ref, superClassName);
                                                 ref.bindToElement(add);
                                                 PsiDeconstructionPattern pattern = getDeconstructionPattern(ref);
                                                 if (pattern != null) {
                                                   setupRecordFromDeconstructionPattern(add, pattern, getText());
                                                 }
                                               },
                                               aClass.getContainingFile());
    }
  }

  /**
   * Adds the new class into the target class. It adds the modifiers, the super class reference and the
   * type parameters. It starts no template, so a {@link ModCommandAction} can call it.
   *
   * @param aClass         the class which gets the new class
   * @param ref            the reference which needs the new class
   * @param superClassName the qualified name of the super class, or null when the class needs none
   * @return the new class
   */
  private @NotNull PsiClass createInnerClass(@NotNull PsiClass aClass,
                                             @NotNull PsiJavaCodeReferenceElement ref,
                                             @Nullable String superClassName) {
    String refName = ref.getReferenceName();
    LOG.assertTrue(refName != null);
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
    PsiClass created = myKind.create(elementFactory, refName);
    final PsiModifierList modifierList = created.getModifierList();
    LOG.assertTrue(modifierList != null);
    if (aClass.isInterface() || PsiUtil.isLocalOrAnonymousClass(aClass)) {
      modifierList.setModifierProperty(PsiModifier.PACKAGE_LOCAL, true);
    }
    else {
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    }
    if (CommonJavaRefactoringUtil.isInStaticContext(ref, aClass) && !aClass.isInterface()) {
      modifierList.setModifierProperty(PsiModifier.STATIC, true);
    }
    if (superClassName != null) {
      CreateFromUsageUtils.setupSuperClassReference(created, superClassName);
    }
    CreateFromUsageBaseFix.setupGenericParameters(created, ref);
    return (PsiClass)aClass.add(created);
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

  @Override
  public @Nullable ModCommandAction getFallbackModCommandAction() {
    PsiJavaCodeReferenceElement element = getRefElement();
    return element == null ? null : new CreateInnerClassFromUsageModCommandAction(element);
  }

  /**
   * Creates the class in the innermost class around the reference. The fix which the user starts in the
   * editor asks for the target class, which a {@link ModCommandAction} cannot do.
   */
  private final class CreateInnerClassFromUsageModCommandAction extends PsiUpdateModCommandAction<PsiJavaCodeReferenceElement> {
    private CreateInnerClassFromUsageModCommandAction(@NotNull PsiJavaCodeReferenceElement element) {
      super(element);
    }

    @Override
    public @NotNull String getFamilyName() {
      return CreateInnerClassFromUsageFix.this.getFamilyName();
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaCodeReferenceElement element) {
      if (element.getQualifier() != null) return null;
      String text = getAvailableText(element, context.offset());
      return text == null ? null : Presentation.of(text);
    }

    @Override
    protected void invoke(@NotNull ActionContext context,
                          @NotNull PsiJavaCodeReferenceElement element,
                          @NotNull ModPsiUpdater updater) {
      PsiClass[] targets = getPossibleTargets(element);
      if (targets.length == 0) return;
      PsiClass added = createInnerClass(targets[0], element, getSuperClassName(element));
      PsiDeconstructionPattern pattern = getDeconstructionPattern(element);
      if (pattern != null) {
        CreateRecordFromNewFix.setupRecordComponentsFromPattern(added.getRecordHeader(), DummyTemplateBuilder.INSTANCE,
                                                                pattern.getDeconstructionList());
      }
      updater.moveCaretTo(ObjectUtils.notNull(added.getNameIdentifier(), added));
    }
  }
}
