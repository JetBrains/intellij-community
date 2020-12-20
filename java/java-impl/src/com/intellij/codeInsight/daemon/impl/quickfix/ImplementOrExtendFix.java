// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.util.ObjectUtils.tryCast;

public final class ImplementOrExtendFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  private final SmartPsiElementPointer<PsiClass> mySubclassPointer;
  private final SmartPsiElementPointer<PsiClass> myParentClassPointer;
  private final boolean myOnTheFly;

  private ImplementOrExtendFix(@NotNull PsiElement place, @NotNull PsiClass subclass, @NotNull PsiClass parentClass, boolean onTheFly) {
    super(place);
    mySubclassPointer = SmartPointerManager.createPointer(subclass);
    myParentClassPointer = SmartPointerManager.createPointer(parentClass);
    myOnTheFly = onTheFly;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    // can happen during batch-inspection if resolution has already been applied
    // to plugin.xml or java class
    PsiClass subclass = mySubclassPointer.getElement();
    if (subclass == null || !subclass.isValid()) return;
    PsiFile subclassFile = subclass.getContainingFile();
    boolean external = file != subclassFile;
    if (external && !subclassFile.isWritable()) {
        String className = subclass.getQualifiedName();
        Messages.showErrorDialog(project, RefactoringBundle.message("0.is.read.only", className), CommonBundle.getErrorTitle());
        return;
    }
    PsiClass parentClass = myParentClassPointer.getElement();
    if (parentClass == null) return;
    if (!FileModificationService.getInstance().prepareFileForWrite(subclassFile)) return;

    WriteAction.run(() -> {
      PsiElement e = implementOrExtend(parentClass, subclass);
      if (myOnTheFly && external && e instanceof Navigatable) ((Navigatable)e).navigate(true);
    });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @IntentionName @NotNull String getText() {
    PsiClass parentClass = Objects.requireNonNull(myParentClassPointer.getElement());
    return parentClass.isInterface()
           ? QuickFixBundle.message("implement.or.extend.fix.implement.text", parentClass.getQualifiedName())
           : QuickFixBundle.message("implement.or.extend.fix.extend.text", parentClass.getQualifiedName());
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return QuickFixBundle.message("implement.or.extend.fix.family");
  }

  public static IntentionAction[] createActions(@NotNull PsiElement place, @NotNull PsiClass subclass,
                                                @NotNull PsiClass parentClass, boolean onTheFly) {
    return ContainerUtil.map2Array(createFixes(place, subclass, parentClass, onTheFly), IntentionAction.class, f -> (IntentionAction)f);
  }

  public static LocalQuickFix @NotNull [] createFixes(@NotNull PsiElement place, @NotNull PsiClass subclass,
                                                      @NotNull PsiClass parentClass, boolean onTheFly) {
    if (!parentClass.isInterface() && (subclass.isInterface() || subclass.isRecord() || subclass.isEnum())) {
      return LocalQuickFix.EMPTY_ARRAY;
    }
    if (subclass.isAnnotationType()) return LocalQuickFix.EMPTY_ARRAY;
    PsiModifierList modifiers = subclass.getModifierList();
    if (modifiers == null) return LocalQuickFix.EMPTY_ARRAY;
    if (parentClass.isInterface()) {
      PsiReferenceList targetList = subclass.isInterface() ? subclass.getExtendsList() : subclass.getImplementsList();
      if (targetList == null) return LocalQuickFix.EMPTY_ARRAY;
    }
    else if (subclass.getExtendsList() == null || hasNonObjectParent(subclass)) {
      return LocalQuickFix.EMPTY_ARRAY;
    }

    PsiJavaCodeReferenceElement subclassRef = tryCast(place, PsiJavaCodeReferenceElement.class);
    LocalQuickFix[] fixes = subclassRef == null ? null : ExtendSealedClassFix.createFixes(subclassRef, parentClass, subclass);
    if (fixes != null) return fixes;
    return new LocalQuickFix[]{new ImplementOrExtendFix(place, subclass, parentClass, onTheFly)};
  }

  static boolean hasNonObjectParent(@NotNull PsiClass psiClass) {
    PsiClassType[] extendsListTypes = psiClass.getExtendsListTypes();
    if (extendsListTypes.length == 0) return false;
    if (extendsListTypes.length == 1 && TypeUtils.isJavaLangObject(extendsListTypes[0])) return false;
    return true;
  }

  static @Nullable PsiElement implementOrExtend(@NotNull PsiClass parentClass, @NotNull PsiClass subclass) {
    boolean subclassIsInterface = subclass.isInterface();
    boolean parentIsInterface = parentClass.isInterface();
    if (!parentIsInterface && subclassIsInterface) return null;
    final PsiReferenceList targetList;
    if (parentIsInterface && !subclassIsInterface) {
      PsiReferenceList implementsList = subclass.getImplementsList();
      if (implementsList == null) return null;
      targetList = implementsList;
    }
    else {
      PsiReferenceList extendsList = subclass.getExtendsList();
      if (extendsList == null || !subclassIsInterface && hasNonObjectParent(subclass)) return null;
      PsiJavaCodeReferenceElement[] parents = extendsList.getReferenceElements();
      if (parents.length > 0 && !subclassIsInterface) parents[0].delete();
      targetList = extendsList;
    }
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(parentClass.getProject()).getElementFactory();
    PsiJavaCodeReferenceElement parentReference = elementFactory.createReferenceElementByType(elementFactory.createType(parentClass));
    return targetList.add(parentReference);
  }
}
