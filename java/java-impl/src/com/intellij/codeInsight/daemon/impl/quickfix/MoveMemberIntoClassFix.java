// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.analysis.MemberModel;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.java.JavaBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class MoveMemberIntoClassFix extends PsiUpdateModCommandAction<PsiErrorElement> {

  public MoveMemberIntoClassFix(@NotNull PsiErrorElement errorElement) {
    super(errorElement);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiErrorElement element, @NotNull ModPsiUpdater updater) {
    PsiFile file = element.getContainingFile();
    MemberInfo rangeAndMember = createMember(file, element);
    if (rangeAndMember == null) return;
    Document document = file.getViewProvider().getDocument();
    TextRange memberRange = rangeAndMember.range();
    PsiMember member = rangeAndMember.member();
    PsiClass psiClass = getPsiClass(file, member);
    if (psiClass == null) return;
    if (psiClass.getContainingFile() != file) {
      psiClass = (PsiClass)file.add(psiClass);
    }
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(context.project());
    documentManager.doPostponedOperationsAndUnblockDocument(document);
    SmartPsiElementPointer<PsiClass> classPtr = SmartPointerManager.createPointer(psiClass);
    document.deleteString(memberRange.getStartOffset(), memberRange.getEndOffset());
    documentManager.commitDocument(document);
    psiClass = classPtr.getElement();
    if (psiClass == null) return;
    PsiElement rBrace = psiClass.getRBrace();
    if (rBrace == null) return;
    psiClass.addBefore(member, rBrace);
  }

  @Nullable
  private static PsiClass getPsiClass(@NotNull PsiFile file, @NotNull PsiMember member) {
    String className = FileUtilRt.getNameWithoutExtension(file.getName());
    if (!StringUtil.isJavaIdentifier(className)) return null;
    PsiJavaFile javaFile = ObjectUtils.tryCast(file, PsiJavaFile.class);
    if (javaFile == null) return null;
    PsiClass psiClass = ContainerUtil.find(javaFile.getClasses(), c -> className.equals(c.getName()));
    if (psiClass != null) return psiClass;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(file.getProject());
    return createClass(factory, className, member);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.move.member.into.class");
  }

  private static @NotNull PsiClass createClass(@NotNull PsiElementFactory factory, @NotNull String className, @NotNull PsiMember member) {
    PsiMethod psiMethod = ObjectUtils.tryCast(member, PsiMethod.class);
    if (psiMethod == null || psiMethod.getBody() != null || psiMethod.hasModifier(JvmModifier.NATIVE)) {
      return factory.createClass(className);
    }
    if (psiMethod.hasModifier(JvmModifier.ABSTRACT)) {
      PsiClass psiClass = factory.createClass(className);
      Objects.requireNonNull(psiClass.getModifierList()).setModifierProperty(PsiModifier.ABSTRACT, true);
      return psiClass;
    }
    return factory.createInterface(className);
  }
  
  record MemberInfo(@NotNull TextRange range, @NotNull PsiMember member) {}

  private static @Nullable MemberInfo createMember(@NotNull PsiFile file, @NotNull PsiErrorElement errorElement) {
    MemberModel model = MemberModel.create(errorElement);
    if (model == null) return null;
    TextRange memberRange = model.textRange();
    String memberText = memberRange.substring(file.getText());
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(file.getProject());
    MemberModel.MemberType memberType = model.memberType();
    return new MemberInfo(memberRange, memberType.create(factory, memberText, file));
  }

  @Override
  protected @NotNull IntentionPreviewInfo generatePreview(ActionContext context, PsiErrorElement element) {
    MemberInfo info = createMember(context.file(), element);
    if (info == null || !(info.member() instanceof PsiNamedElement member)) return IntentionPreviewInfo.EMPTY;
    PsiClass psiClass = getPsiClass(context.file(), info.member());
    if (psiClass == null) return IntentionPreviewInfo.EMPTY;
    return IntentionPreviewInfo.movePsi(member, psiClass);
  }
}
