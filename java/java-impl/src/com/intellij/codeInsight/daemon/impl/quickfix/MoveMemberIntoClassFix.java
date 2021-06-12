// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.analysis.MemberModel;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
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
    if (editor == null) return;
    PsiJavaFile javaFile = ObjectUtils.tryCast(file, PsiJavaFile.class);
    if (javaFile == null) return;
    PsiErrorElement errorElement = ObjectUtils.tryCast(startElement, PsiErrorElement.class);
    if (errorElement == null) return;
    MemberModel model = MemberModel.create(errorElement);
    if (model == null) return;
    String className = FileUtilRt.getNameWithoutExtension(file.getName());
    PsiClass psiClass = ContainerUtil.find(javaFile.getClasses(), c -> className.equals(c.getName()));
    TextRange memberRange = model.textRange();
    Document document = editor.getDocument();
    String memberText = document.getText(memberRange);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    MemberModel.MemberType memberType = model.memberType();
    PsiMember member = memberType.create(factory, memberText, file);
    if (psiClass == null) {
      psiClass = (PsiClass)file.add(createClass(factory, className, member));
      documentManager.doPostponedOperationsAndUnblockDocument(document);
    }
    SmartPsiElementPointer<PsiClass> classPtr = SmartPointerManager.createPointer(psiClass);
    document.deleteString(memberRange.getStartOffset(), memberRange.getEndOffset());
    documentManager.commitDocument(document);
    psiClass = classPtr.getElement();
    if (psiClass == null) return;
    psiClass.add(member);
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
}
