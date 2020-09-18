// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.convertToRecord;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.jvm.types.JvmReferenceType;
import com.intellij.lang.jvm.types.JvmType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.PsiAnnotation.TargetType;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.PsiModifier.*;

public class ConvertToRecordHandler implements RefactoringActionHandler {

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null) {
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    if (elements.length != 1) return;
    PsiClass psiClass;
    if (elements[0] instanceof PsiClass) {
      psiClass = (PsiClass)elements[0];
    }
    else if (elements[0].getParent() instanceof PsiClass) {
      psiClass = (PsiClass)elements[0].getParent();
    }
    else {
      return;
    }
    PsiClassDefinition psiClassDef = getClassDefinition(psiClass);
    if (psiClassDef == null) {
      String message = JavaRefactoringBundle.message("convert.to.record.is.inappropriate");
      CommonRefactoringUtil.showErrorHint(project, CommonDataKeys.EDITOR.getData(dataContext),
                                          RefactoringBundle.getCannotRefactorMessage(message),
                                          JavaRefactoringBundle.message("convert.to.record.title"),
                                          HelpID.CONVERT_TO_RECORD);
      return;
    }
    new ConvertToRecordDialog(psiClassDef).show();
  }

  public static boolean isAppropriatePsiClass(@NotNull PsiClass psiClass) {
    return getClassDefinition(psiClass) != null;
  }

  /**
   * There are some restrictions for records:
   * http://cr.openjdk.java.net/~gbierman/jep384/jep384-20200506/specs/records-jls.html
   */
  public static PsiClassDefinition getClassDefinition(@NotNull PsiClass psiClass) {
    boolean isNotAppropriatePsiClass = psiClass.isEnum() || psiClass.isAnnotationType() || psiClass instanceof PsiAnonymousClass ||
                                       psiClass.isInterface() || psiClass.isRecord();
    if (isNotAppropriatePsiClass) return null;

    PsiModifierList psiClassModifiers = psiClass.getModifierList();
    if (psiClassModifiers == null || psiClassModifiers.hasModifierProperty(ABSTRACT) || psiClassModifiers.hasModifierProperty(SEALED)) {
      return null;
    }
    if (!mayBeFinal(psiClass)) return null;

    // todo support local classes later
    if (PsiUtil.isLocalClass(psiClass)) return null;
    if (psiClass.getContainingClass() != null && !psiClass.hasModifierProperty(STATIC)) return null;

    PsiClass superClass = psiClass.getSuperClass();
    if (superClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName())) return null;

    if (ClassInheritorsSearch.search(psiClass, false).findFirst() != null) return null;
    if (ContainerUtil.exists(psiClass.getInitializers(), initializer -> !initializer.hasModifierProperty(STATIC))) return null;

    PsiClassDefinition result = new PsiClassDefinition(psiClass);
    return result.isValid() ? result : null;
  }

  /**
   * Some classes might be proxied e.g. according to JPA spec an entity class can't be final.
   */
  private static boolean mayBeFinal(@NotNull PsiClass psiClass) {
    return !psiClass.hasAnnotation("javax.persistence.Entity");
  }

  /**
   * Encapsulates necessary information about the converting class e.g its existing fields, accessors...
   * It helps to validate whether a class will be a well-formed record and supports performing a refactoring.
   */
  static class PsiClassDefinition {
    private final PsiClass myClass;
    private final MultiMap<PsiField, FieldAccessorDefinition> myFieldAccessors = new MultiMap<>();
    private final List<PsiMethod> myOrdinaryMethods = new SmartList<>();
    private final List<ConstructorDefinition> myConstructors = new SmartList<>();

    private Map<PsiField, FieldAccessorDefinition> fieldAccessorsCache;

    private PsiClassDefinition(@NotNull PsiClass psiClass) {
      myClass = psiClass;
      prepare();
    }

    Project getProject() {
      return myClass.getProject();
    }

    PsiClass getPsiClass() {
      return myClass;
    }

    @NotNull
    Map<PsiField, @Nullable FieldAccessorDefinition> getFieldAccessors() {
      if (fieldAccessorsCache != null) return fieldAccessorsCache;

      Map<PsiField, FieldAccessorDefinition> result = new HashMap<>();
      for (var entry : myFieldAccessors.entrySet()) {
        PsiField newKey = entry.getKey();
        Collection<FieldAccessorDefinition> oldValue = entry.getValue();
        FieldAccessorDefinition newValue = oldValue.size() == 1 ? oldValue.iterator().next() : null;
        result.put(newKey, newValue);
      }
      fieldAccessorsCache = result;
      return result;
    }

    @Nullable
    PsiMethod getCanonicalConstructor() {
      return myConstructors.size() == 1 ? myConstructors.get(0).myConstructor : null;
    }

    private boolean isValid() {
      if (myConstructors.size() > 1) return false;
      if (myConstructors.size() == 1) {
        ConstructorDefinition ctor = myConstructors.get(0);
        boolean isCanonical = ctor.myCanonical && throwsOnlyUncheckedExceptions(ctor.myConstructor);
        if (!isCanonical) return false;
      }
      for (var entry : myFieldAccessors.entrySet()) {
        PsiField field = entry.getKey();
        if (!field.hasModifierProperty(FINAL) || field.hasInitializer()) return false;
        if (HighlightUtil.RESTRICTED_RECORD_COMPONENT_NAMES.contains(field.getName())) return false;
        if (entry.getValue().size() > 1) return false;
      }
      for (PsiMethod ordinaryMethod : myOrdinaryMethods) {
        if (ordinaryMethod.hasModifierProperty(NATIVE)) return false;
        boolean conflictsWithPotentialAccessor = ContainerUtil.exists(myFieldAccessors.keySet(), field ->
          ordinaryMethod.getParameterList().isEmpty() && field.getName().equals(ordinaryMethod.getName()));
        if (conflictsWithPotentialAccessor) return false;
      }
      return true;
    }

    private void prepare() {
      Arrays.stream(myClass.getFields()).filter(field -> !field.hasModifierProperty(STATIC))
        .forEach(field -> myFieldAccessors.put(field, new ArrayList<>()));
      for (PsiMethod method : myClass.getMethods()) {
        if (method.isConstructor()) {
          myConstructors.add(new ConstructorDefinition(method, myFieldAccessors.keySet()));
          continue;
        }
        if (!throwsOnlyUncheckedExceptions(method)) {
          myOrdinaryMethods.add(method);
          continue;
        }
        FieldAccessorDefinition fieldAccessorDef = createFieldAccessor(method);
        if (fieldAccessorDef == null) {
          myOrdinaryMethods.add(method);
        }
        else {
          myFieldAccessors.putValue(fieldAccessorDef.myBackingField, fieldAccessorDef);
        }
      }
    }

    private static boolean throwsOnlyUncheckedExceptions(@NotNull PsiMethod psiMethod) {
      for (JvmReferenceType throwsType : psiMethod.getThrowsTypes()) {
        PsiClassType throwsClassType = ObjectUtils.tryCast(throwsType, PsiClassType.class);
        if (throwsClassType == null) continue;
        if (!ExceptionUtil.isUncheckedException(throwsClassType)) {
          return false;
        }
      }
      return true;
    }

    @Nullable
    private FieldAccessorDefinition createFieldAccessor(@NotNull PsiMethod psiMethod) {
      if (!psiMethod.getParameterList().isEmpty()) return null;
      String methodName = psiMethod.getName();
      PsiField backingField = null;
      boolean recordStyleNaming = false;
      for (PsiField field : myFieldAccessors.keySet()) {
        if (!field.getType().equalsToText(Objects.requireNonNull(psiMethod.getReturnType()).getCanonicalText())) continue;
        String fieldName = field.getName();
        if (fieldName.equals(methodName)) {
          backingField = field;
          recordStyleNaming = true;
          break;
        }
        if (fieldName.equals(PropertyUtilBase.getPropertyNameByGetter(psiMethod))) {
          backingField = field;
          break;
        }
      }
      return backingField == null ? null : new FieldAccessorDefinition(psiMethod, backingField, recordStyleNaming);
    }
  }

  /**
   * Encapsulates information about the converting constructor e.g whether its canonical or not.
   */
  private static class ConstructorDefinition {
    private final PsiMethod myConstructor;
    private final boolean myCanonical;

    private ConstructorDefinition(@NotNull PsiMethod constructor, @NotNull Set<PsiField> instanceFields) {
      myConstructor = constructor;

      Map<String, JvmType> ctorParams =
        Arrays.stream(myConstructor.getParameters()).collect(Collectors.toMap(param -> param.getName(), param -> param.getType()));
      for (PsiField instanceField : instanceFields) {
        PsiType ctorParamType = ObjectUtils.tryCast(ctorParams.get(instanceField.getName()), PsiType.class);
        if (ctorParamType instanceof PsiEllipsisType) {
          ctorParamType = ((PsiEllipsisType)ctorParamType).toArrayType();
        }
        if (ctorParamType == null || !TypeUtils.typeEquals(ctorParamType.getCanonicalText(), instanceField.getType())) {
          myCanonical = false;
          return;
        }
      }
      myCanonical = true;
    }
  }

  /**
   * Encapsulates information about the converting of field accessors.
   * For instance an existing default accessor may be removed during further record creation.
   */
  static class FieldAccessorDefinition {
    private final PsiMethod myFieldAccessor;
    private final PsiField myBackingField;
    private final boolean myDefault;
    private final boolean myRecordStyleNaming;

    private FieldAccessorDefinition(@NotNull PsiMethod accessor, @NotNull PsiField backingField, boolean recordStyleNaming) {
      myFieldAccessor = accessor;
      myBackingField = backingField;
      myRecordStyleNaming = recordStyleNaming;

      if (accessor.getDocComment() != null) {
        myDefault = false;
        return;
      }
      PsiExpression returnExpr = PropertyUtilBase.getSingleReturnValue(accessor);
      boolean isDefaultAccessor = backingField.equals(PropertyUtil.getFieldOfGetter(accessor, returnExpr, false));
      if (!isDefaultAccessor) {
        myDefault = false;
        return;
      }
      myDefault = !existsAnnotationConflicts(accessor, backingField, TargetType.FIELD) &&
                  !existsAnnotationConflicts(backingField, accessor, TargetType.METHOD);
    }

    @NotNull
    PsiMethod getAccessor() {
      return myFieldAccessor;
    }

    @NotNull
    PsiField getBackingField() {
      return myBackingField;
    }

    boolean isDefault() {
      return myDefault;
    }

    boolean isRecordStyleNaming() {
      return myRecordStyleNaming;
    }
  }

  /**
   * During record creation we have to move the field annotations to the record component.
   * For instance, if an annotation's target includes both field and method target,
   * then we have to check whether the method is already marked by this annotation
   * as a compiler propagates annotations of the record components to appropriate targets automatically.
   */
  private static boolean existsAnnotationConflicts(@NotNull PsiModifierListOwner first,
                                                   @NotNull PsiModifierListOwner second,
                                                   @NotNull TargetType targetType) {
    boolean result = false;
    for (PsiAnnotation firstAnn : first.getAnnotations()) {
      PsiClass firstAnnType = firstAnn.resolveAnnotationType();
      if (firstAnnType == null) continue;
      TargetType firstAnnTarget = AnnotationTargetUtil.findAnnotationTarget(firstAnnType, targetType);
      boolean hasFieldTarget = firstAnnTarget != null && firstAnnTarget != TargetType.UNKNOWN;
      if (!hasFieldTarget) continue;
      if (!ContainerUtil.exists(second.getAnnotations(), secondAnn -> AnnotationUtil.equal(firstAnn, secondAnn))) {
        result = true;
        break;
      }
    }
    return result;
  }
}
