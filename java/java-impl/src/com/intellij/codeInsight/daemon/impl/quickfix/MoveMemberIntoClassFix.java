// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.analysis.MemberModel;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class MoveMemberIntoClassFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  public MoveMemberIntoClassFix(@Nullable PsiErrorElement errorElement) {
    super(errorElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    Pair<TextRange, PsiMember> rangeAndMember = createMember(file);
    if (rangeAndMember == null) return;
    Document document = file.getViewProvider().getDocument();
    TextRange memberRange = rangeAndMember.getFirst();
    PsiMember member = rangeAndMember.getSecond();
    PsiClass psiClass = getPsiClass(file, member);
    if (psiClass == null) return;
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
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
    return (PsiClass)file.add(createClass(factory, className, member));
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
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

  private @Nullable Pair<@NotNull TextRange, @NotNull PsiMember> createMember(@NotNull PsiFile file) {
    PsiErrorElement errorElement = ObjectUtils.tryCast(getStartElement(), PsiErrorElement.class);
    if (errorElement == null) return null;
    MemberModel model = MemberModel.create(errorElement);
    if (model == null) return null;
    TextRange memberRange = model.textRange();
    String memberText = memberRange.substring(file.getText());
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(file.getProject());
    MemberModel.MemberType memberType = model.memberType();
    return Pair.create(memberRange, memberType.create(factory, memberText, file));
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    Pair<TextRange, PsiMember> member = createMember(file);
    if (member == null || !(member.getSecond() instanceof PsiNamedElement)) return IntentionPreviewInfo.EMPTY;
    PsiClass psiClass = getPsiClass(file, member.getSecond());
    if (psiClass == null) return IntentionPreviewInfo.EMPTY;
    return IntentionPreviewInfo.movePsi((PsiNamedElement)member.getSecond(), psiClass);
  }
}
