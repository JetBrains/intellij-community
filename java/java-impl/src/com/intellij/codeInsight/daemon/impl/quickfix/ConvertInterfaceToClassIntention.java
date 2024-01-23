// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.*;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.FunctionalExpressionSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.psiutils.PsiElementOrderComparator;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.modcommand.ModCommand.psiUpdate;
import static com.intellij.modcommand.ModCommand.showConflicts;

public final class ConvertInterfaceToClassIntention extends PsiBasedModCommandAction<PsiClass> {
  private final boolean myCheckStartPosition;

  public ConvertInterfaceToClassIntention(@NotNull PsiClass aClass) {
    super(aClass);
    myCheckStartPosition = false;
  }

  public ConvertInterfaceToClassIntention() {
    super(PsiClass.class);
    myCheckStartPosition = true;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass cls) {
    if (myCheckStartPosition) {
      PsiElement lBrace = cls.getLBrace();
      if (lBrace == null) return null;
      if (context.offset() > lBrace.getTextOffset()) return null;
    }
    if (!canConvertToClass(cls)) return null;
    return Presentation.of(IntentionPowerPackBundle.message("convert.interface.to.class.intention.name")).withPriority(
      PriorityAction.Priority.LOW);
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("convert.interface.to.class.intention.family.name");
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiClass anInterface) {
    final Collection<PsiClass> inheritors = ClassInheritorsSearch.search(anInterface, anInterface.getUseScope(), false).findAll();
    final Map<PsiElement, ModShowConflicts.Conflict> conflicts =
      IntentionPreviewUtils.isIntentionPreviewActive() ? Map.of() : getConflicts(anInterface, inheritors);
    return showConflicts(conflicts)
      .andThen(psiUpdate(anInterface, (writableInterface, updater) -> convertInterfaceToClass(writableInterface, inheritors, updater)));
  }

  @NotNull
  private static Map<PsiElement, ModShowConflicts.Conflict> getConflicts(@NotNull PsiClass anInterface,
                                                                         @NotNull Collection<PsiClass> inheritors) {
    final Map<PsiElement, ModShowConflicts.Conflict> conflicts = new HashMap<>();
    inheritors.forEach(aClass -> {
      if (aClass.isEnum() || aClass.isRecord() || aClass.isInterface()) {
        final ModShowConflicts.Conflict conflict = new ModShowConflicts.Conflict(List.of(IntentionPowerPackBundle.message(
          "0.implementing.1.will.not.compile.after.converting.1.to.a.class",
          RefactoringUIUtil.getDescription(aClass, true),
          RefactoringUIUtil.getDescription(anInterface, false))));
        conflicts.put(aClass, conflict);
        return;
      }
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
      if (referenceElements.length > 0) {
        final PsiElement target = referenceElements[0].resolve();
        if (target instanceof PsiClass targetClass && !CommonClassNames.JAVA_LANG_OBJECT.equals(targetClass.getQualifiedName())) {
          final ModShowConflicts.Conflict conflict = new ModShowConflicts.Conflict(List.of(IntentionPowerPackBundle.message(
            "0.already.extends.1.and.will.not.compile.after.converting.2.to.a.class",
            RefactoringUIUtil.getDescription(aClass, true),
            RefactoringUIUtil.getDescription(target, true),
            RefactoringUIUtil.getDescription(anInterface, false))));
          conflicts.put(aClass, conflict);
        }
      }
    });

    final PsiFunctionalExpression functionalExpression =
      FunctionalExpressionSearch.search(anInterface, anInterface.getUseScope()).findFirst();
    if (functionalExpression != null) {
      final ModShowConflicts.Conflict conflict = new ModShowConflicts.Conflict(List.of(IntentionPowerPackBundle.message(
        "0.will.not.compile.after.converting.1.to.a.class",
        ClassPresentationUtil.getFunctionalExpressionPresentation(functionalExpression, true),
        RefactoringUIUtil.getDescription(anInterface, false))));
      conflicts.put(functionalExpression, conflict);
    }
    Comparator<PsiElement> comparator = Comparator.comparing(
      (PsiElement e) -> e.getContainingFile().getVirtualFile().getPath()).thenComparing(PsiElementOrderComparator.getInstance());
    return EntryStream.of(conflicts).sorted(Map.Entry.comparingByKey(comparator)).toCustomMap(LinkedHashMap::new);
  }

  public static boolean canConvertToClass(@NotNull PsiClass aClass) {
    if (!aClass.isInterface() || aClass.isAnnotationType()) {
      return false;
    }
    return !AnnotationUtil.isAnnotated(aClass, CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE, 0);
  }

  private static void changeInterfaceToClass(PsiClass anInterface) {
    final PsiIdentifier nameIdentifier = anInterface.getNameIdentifier();
    final PsiKeyword interfaceKeyword = PsiTreeUtil.getPrevSiblingOfType(nameIdentifier, PsiKeyword.class);
    assert interfaceKeyword != null;
    final PsiKeyword classKeyword = JavaPsiFacade.getInstance(anInterface.getProject()).getElementFactory().createKeyword("class");
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

    for (final PsiMethod method : anInterface.getMethods()) {
      PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);
      if (method.hasModifierProperty(PsiModifier.DEFAULT)) {
        PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, false);
      }
      else if (!method.hasModifierProperty(PsiModifier.STATIC) && !method.isConstructor()) {
        PsiUtil.setModifierProperty(method, PsiModifier.ABSTRACT, true);
      }
    }

    for (final PsiField field : anInterface.getFields()) {
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        modifierList.setModifierProperty(PsiModifier.STATIC, true);
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
      }
    }

    for (PsiClass innerClass : anInterface.getInnerClasses()) {
      final PsiModifierList modifierList = innerClass.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
        if (!innerClass.isInterface()) {
          modifierList.setModifierProperty(PsiModifier.STATIC, true);
        }
      }
    }
  }

  private static void convertInterfaceToClass(PsiClass anInterface, Collection<PsiClass> inheritors, @NotNull ModPsiUpdater updater) {
    final List<PsiClass> writableInheritors = ContainerUtil.map(inheritors, updater::getWritable);
    moveSubClassImplementsToExtends(anInterface, writableInheritors);
    changeInterfaceToClass(anInterface);
    moveExtendsToImplements(anInterface);
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
