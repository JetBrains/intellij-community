// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationBundle;
import com.intellij.refactoring.typeMigration.TypeMigrationVariableTypeFixProvider;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.*;

/**
 * @author anna
 */
public class ConvertFieldToAtomicIntention extends PsiElementBaseIntentionAction implements PriorityAction {
  private static final Logger LOG = Logger.getInstance(ConvertFieldToAtomicIntention.class);

  private final Map<PsiType, String> myFromToMap = new HashMap<>();

  {
    myFromToMap.put(PsiType.INT, AtomicInteger.class.getName());
    myFromToMap.put(PsiType.LONG, AtomicLong.class.getName());
    myFromToMap.put(PsiType.BOOLEAN, AtomicBoolean.class.getName());
    myFromToMap.put(PsiType.INT.createArrayType(), AtomicIntegerArray.class.getName());
    myFromToMap.put(PsiType.LONG.createArrayType(), AtomicLongArray.class.getName());
  }

  @NotNull
  @Override
  public String getText() {
    return TypeMigrationBundle.message("convert.to.atomic.family.name");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @NotNull
  @Override
  public Priority getPriority() {
    return Priority.LOW;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiVariable psiVariable = getVariable(element);
    if (psiVariable == null || psiVariable instanceof PsiResourceVariable) return false;
    if (psiVariable.getLanguage() != JavaLanguage.INSTANCE) return false;
    if (psiVariable.getTypeElement() == null) return false;
    if (!PsiUtil.isLanguageLevel5OrHigher(psiVariable)) return false;
    final PsiType psiType = psiVariable.getType();
    final PsiClass psiTypeClass = PsiUtil.resolveClassInType(psiType);
    if (psiTypeClass != null) {
      final String qualifiedName = psiTypeClass.getQualifiedName();
      if (qualifiedName != null) { //is already atomic
        if (myFromToMap.containsValue(qualifiedName) ||
            qualifiedName.equals(AtomicReference.class.getName()) ||
            qualifiedName.equals(AtomicReferenceArray.class.getName())) {
          return false;
        }
      }
    }
    else if (!myFromToMap.containsKey(psiType)) {
      return false;
    }
    return AllowedApiFilterExtension.isClassAllowed(AtomicReference.class.getName(), element);
  }

  PsiVariable getVariable(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiLocalVariable || parent instanceof PsiField) {
        return (PsiVariable)parent;
      }
    }
    return null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiVariable var = getVariable(element);
    LOG.assertTrue(var != null);

    final PsiType fromType = var.getType();
    PsiClassType toType = getMigrationTargetType(project, element, fromType);
    if (toType == null) return;

    if (!FileModificationService.getInstance().preparePsiElementsForWrite(var)) return;
    addExplicitInitializer(var);
    String toTypeCanonicalText = toType.getCanonicalText();
    TypeMigrationVariableTypeFixProvider.runTypeMigrationOnVariable(var, toType, editor, false, false);
    postProcessVariable(var, toTypeCanonicalText);
  }

  static void addExplicitInitializer(@NotNull PsiVariable var) {
    PsiExpression currentInitializer = var.getInitializer();
    if (currentInitializer != null) return;
    final PsiType type = var.getType();
    String initializerText = PsiTypesUtil.getDefaultValueOfType(type);
    if (!PsiKeyword.NULL.equals(initializerText)) {
      WriteAction.run(() -> {
        PsiExpression initializer = JavaPsiFacade.getElementFactory(var.getProject()).createExpressionFromText(initializerText, var);
        var.setInitializer(initializer);
      });
    }
  }

  static void postProcessVariable(@NotNull PsiVariable var, @NotNull String toType) {

    Project project = var.getProject();
    if (var instanceof PsiField || JavaCodeStyleSettings.getInstance(var.getContainingFile()).GENERATE_FINAL_LOCALS) {
      PsiModifierList modifierList = Objects.requireNonNull(var.getModifierList());
      WriteAction.run(() -> {
        if (var.getInitializer() == null) {
          final PsiExpression newInitializer = JavaPsiFacade.getElementFactory(project).createExpressionFromText("new " + toType + "()", var);
          var.setInitializer(newInitializer);
        }

        modifierList.setModifierProperty(PsiModifier.FINAL, true);
        modifierList.setModifierProperty(PsiModifier.VOLATILE, false);

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(var);
        CodeStyleManager.getInstance(project).reformat(var);
      });
    }
  }

  @Nullable
  private PsiClassType getMigrationTargetType(@NotNull Project project,
                                              @NotNull PsiElement element,
                                              @NotNull PsiType fromType) {
    JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiElementFactory factory = psiFacade.getElementFactory();
    final String atomicQualifiedName = myFromToMap.get(fromType);
    if (atomicQualifiedName != null) {
      final PsiClass atomicClass = psiFacade.findClass(atomicQualifiedName, GlobalSearchScope.allScope(project));
      if (atomicClass == null) {//show warning
        return null;
      }
      return factory.createType(atomicClass);
    }
    else if (fromType instanceof PsiArrayType) {
      final PsiClass atomicReferenceArrayClass =
        psiFacade.findClass(AtomicReferenceArray.class.getName(), GlobalSearchScope.allScope(project));
      if (atomicReferenceArrayClass == null) {//show warning
        return null;
      }
      final Map<PsiTypeParameter, PsiType> substitutor = new HashMap<>();
      final PsiTypeParameter[] typeParameters = atomicReferenceArrayClass.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiType componentType = ((PsiArrayType)fromType).getComponentType();
        if (componentType instanceof PsiPrimitiveType) componentType = ((PsiPrimitiveType)componentType).getBoxedType(element);
        substitutor.put(typeParameters[0], componentType);
      }
      return factory.createType(atomicReferenceArrayClass, factory.createSubstitutor(substitutor));
    }
    else {
      final PsiClass atomicReferenceClass = psiFacade.findClass(AtomicReference.class.getName(), GlobalSearchScope.allScope(project));
      if (atomicReferenceClass == null) {//show warning
        return null;
      }
      final Map<PsiTypeParameter, PsiType> substitutor = new HashMap<>();
      final PsiTypeParameter[] typeParameters = atomicReferenceClass.getTypeParameters();
      if (typeParameters.length == 1) {
        PsiType type = fromType;
        if (type instanceof PsiPrimitiveType) type = ((PsiPrimitiveType)fromType).getBoxedType(element);
        substitutor.put(typeParameters[0], type);
      }
      return factory.createType(atomicReferenceClass, factory.createSubstitutor(substitutor));
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  public static class ConvertNonFinalLocalToAtomicFix extends ConvertFieldToAtomicIntention {
    private final PsiElement myContext;

    public ConvertNonFinalLocalToAtomicFix(PsiElement context) {
      myContext = context;
    }

    @NotNull
    @Override
    public Priority getPriority() {
      return Priority.HIGH;
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
      return getVariable(element) != null;
    }

    @Override
    PsiVariable getVariable(PsiElement element) {
      if(myContext instanceof PsiReferenceExpression && myContext.isValid()) {
        PsiReferenceExpression ref = (PsiReferenceExpression)myContext;
        if(PsiUtil.isAccessedForWriting(ref)) {
          return ObjectUtils.tryCast(ref.resolve(), PsiLocalVariable.class);
        }
      }
      return null;
    }
  }
}
