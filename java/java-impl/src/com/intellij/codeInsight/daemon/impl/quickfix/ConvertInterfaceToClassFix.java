// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ConvertInterfaceToClassFix extends LocalQuickFixAndIntentionActionOnPsiElement implements PriorityAction {

  public ConvertInterfaceToClassFix(@Nullable PsiClass aClass) {
    super(aClass);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    if (!(startElement instanceof PsiClass)) return false;
    return canConvertToClass((PsiClass)startElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (!(startElement instanceof PsiClass)) return;
    convert((PsiClass)startElement);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull Priority getPriority() {
    return Priority.LOW;
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return IntentionPowerPackBundle.message("convert.interface.to.class.intention.name");
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("convert.interface.to.class.intention.family.name");
  }

  public static void convert(@NotNull PsiClass anInterface) {
    final SearchScope searchScope = anInterface.getUseScope();
    final Collection<PsiClass> inheritors = ClassInheritorsSearch.search(anInterface, searchScope, false).findAll();
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    inheritors.forEach(aClass -> {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      if (referenceElements.length > 0) {
        final PsiElement target = referenceElements[0].resolve();
        if (target instanceof PsiClass && !CommonClassNames.JAVA_LANG_OBJECT.equals(((PsiClass)target).getQualifiedName())) {
          conflicts.putValue(aClass, IntentionPowerPackBundle.message(
            "0.already.extends.1.and.will.not.compile.after.converting.2.to.a.class",
            RefactoringUIUtil.getDescription(aClass, true), RefactoringUIUtil.getDescription(target, true),
            RefactoringUIUtil.getDescription(anInterface, false)));
        }
      }
    });

    final PsiFunctionalExpression functionalExpression = FunctionalExpressionSearch.search(anInterface, searchScope).findFirst();
    if (functionalExpression != null) {
      final String conflictMessage = ClassPresentationUtil.getFunctionalExpressionPresentation(functionalExpression, true) +
                                     " will not compile after converting " +
                                     RefactoringUIUtil.getDescription(anInterface, false) +
                                     " to a class";
      conflicts.putValue(functionalExpression, conflictMessage);
    }
    final boolean conflictsDialogOK;
    if (conflicts.isEmpty()) {
      conflictsDialogOK = true;
    }
    else {
      final Application application = ApplicationManager.getApplication();
      if (application.isUnitTestMode()) {
        throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
      }
      final ConflictsDialog conflictsDialog =
        new ConflictsDialog(anInterface.getProject(), conflicts, () -> convertInterfaceToClass(anInterface, inheritors));
      conflictsDialogOK = conflictsDialog.showAndGet();
    }
    if (conflictsDialogOK) {
      convertInterfaceToClass(anInterface, inheritors);
    }
  }

  public static boolean canConvertToClass(@NotNull PsiClass aClass) {
    if (!aClass.isInterface() || aClass.isAnnotationType()) {
      return false;
    }
    final SearchScope useScope = aClass.getUseScope();
    for (PsiClass inheritor :
      ClassInheritorsSearch.search(aClass, useScope, true)) {
      if (inheritor.isInterface()) {
        return false;
      }
    }
    return !AnnotationUtil.isAnnotated(aClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, 0);
  }

  private static void changeInterfaceToClass(PsiClass anInterface) {
    final PsiIdentifier nameIdentifier = anInterface.getNameIdentifier();
    assert nameIdentifier != null;
    final PsiElement whiteSpace = nameIdentifier.getPrevSibling();
    assert whiteSpace != null;
    final PsiElement interfaceToken = whiteSpace.getPrevSibling();
    assert interfaceToken != null;
    final PsiKeyword interfaceKeyword = (PsiKeyword)interfaceToken.getOriginalElement();
    final Project project = anInterface.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiKeyword classKeyword = factory.createKeyword("class");
    interfaceKeyword.replace(classKeyword);

    final PsiModifierList classModifierList = anInterface.getModifierList();
    if (classModifierList == null) {
      return;
    }
    classModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);

    final PsiElement parent = anInterface.getParent();
    if (parent instanceof PsiClass) {
      classModifierList.setModifierProperty(PsiModifier.STATIC, true);
    }

    final PsiMethod[] methods = anInterface.getMethods();
    for (final PsiMethod method : methods) {
      PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
      if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
        PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, false);
      }
      else if (!method.hasModifierProperty(PsiModifier.STATIC) && !method.isConstructor()) {
        PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, true);
      }
    }

    final PsiField[] fields = anInterface.getFields();
    for (final PsiField field : fields) {
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
      }
    }

    final PsiClass[] innerClasses = anInterface.getInnerClasses();
    for (PsiClass innerClass : innerClasses) {
      final PsiModifierList modifierList = innerClass.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        if (!innerClass.isInterface()) {
          modifierList.setModifierProperty(PsiModifier.STATIC, true);
        }
      }
    }
  }

  private static void convertInterfaceToClass(PsiClass anInterface, Collection<PsiClass> inheritors) {
    final List<PsiClass> prepare = new ArrayList<>();
    prepare.add(anInterface);
    prepare.addAll(inheritors);
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(prepare)) {
      return;
    }
    WriteAction.run(() -> {
      moveSubClassImplementsToExtends(anInterface, inheritors);
      changeInterfaceToClass(anInterface);
      moveExtendsToImplements(anInterface);
    });
  }

  private static void moveExtendsToImplements(PsiClass anInterface) {
    final PsiReferenceList extendsList = anInterface.getExtendsList();
    final PsiReferenceList implementsList = anInterface.getImplementsList();
    assert extendsList != null;
    final PsiJavaCodeReferenceElement[] extendsRefElements = extendsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement referenceElement : extendsRefElements) {
      assert implementsList != null;
      final PsiElement resolved = referenceElement.resolve();
      if (resolved instanceof PsiClass && ((PsiClass)resolved).isInterface()) {
        implementsList.add(referenceElement);
        referenceElement.delete();
      }
    }
  }

  private static void moveSubClassImplementsToExtends(PsiClass oldInterface, Collection<PsiClass> inheritors) {
    for (PsiClass inheritor : inheritors) {
      final PsiReferenceList implementsList = inheritor.getImplementsList();
      final PsiReferenceList extendsList = inheritor.getExtendsList();
      if (implementsList != null) {
        moveReference(implementsList, extendsList, oldInterface);
      }
    }
  }

  private static void moveReference(@NotNull PsiReferenceList source,
                                    @Nullable PsiReferenceList target,
                                    @NotNull PsiClass oldInterface) {
    final PsiJavaCodeReferenceElement[] implementsReferences = source.getReferenceElements();
    for (PsiJavaCodeReferenceElement implementsReference : implementsReferences) {
      if (implementsReference.isReferenceTo(oldInterface)) {
        if (target != null) {
          final PsiJavaCodeReferenceElement[] referenceElements = target.getReferenceElements();
          if (referenceElements.length > 0) {
            final PsiElement aClass = referenceElements[0].resolve();
            if (aClass instanceof PsiClass && CommonClassNames.JAVA_LANG_OBJECT.equals(((PsiClass)aClass).getQualifiedName())) {
              referenceElements[0].delete();
            }
          }
          target.add(implementsReference);
        }
        implementsReference.delete();
      }
    }
  }
}
