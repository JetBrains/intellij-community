// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.PsiAnnotation.TargetType;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.PsiModifier.*;

public class ConvertToRecordFix extends InspectionGadgetsFix {
  private final boolean myShowAffectedMembers;
  private final boolean mySuggestAccessorsRenaming;

  ConvertToRecordFix(boolean showAffectedMembers, boolean suggestAccessorsRenaming) {
    myShowAffectedMembers = showAffectedMembers;
    mySuggestAccessorsRenaming = suggestAccessorsRenaming;
  }

  @Override
  public @IntentionFamilyName @NotNull String getFamilyName() {
    return JavaBundle.message("class.can.be.record.quick.fix");
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  protected void doFix(Project project, ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    if (psiElement == null) return;
    PsiClass psiClass = ObjectUtils.tryCast(psiElement.getParent(), PsiClass.class);
    if (psiClass == null) return;

    RecordCandidate recordCandidate = getClassDefinition(psiClass, mySuggestAccessorsRenaming);
    if (recordCandidate == null) return;

    ConvertToRecordProcessor processor = new ConvertToRecordProcessor(recordCandidate, myShowAffectedMembers);
    processor.setPrepareSuccessfulSwingThreadCallback(() -> {});
    processor.run();
  }

  /**
   * There are some restrictions for records:
   * https://docs.oracle.com/javase/specs/jls/se15/preview/specs/records-jls.html.
   */
  public static RecordCandidate getClassDefinition(@NotNull PsiClass psiClass, boolean suggestAccessorsRenaming) {
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

    if (ContainerUtil.exists(psiClass.getInitializers(), initializer -> !initializer.hasModifierProperty(STATIC))) return null;

    RecordCandidate result = new RecordCandidate(psiClass, suggestAccessorsRenaming);
    if (!result.isValid()) return null;
    return ClassInheritorsSearch.search(psiClass, false).findFirst() == null ? result : null;
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
  static class RecordCandidate {
    private final PsiClass myClass;
    private final boolean mySuggestAccessorsRenaming;
    private final MultiMap<PsiField, FieldAccessorCandidate> myFieldAccessors = new MultiMap<>();
    private final List<PsiMethod> myOrdinaryMethods = new SmartList<>();
    private final List<RecordConstructorCandidate> myConstructors = new SmartList<>();

    private Map<PsiField, FieldAccessorCandidate> myFieldAccessorsCache;

    private RecordCandidate(@NotNull PsiClass psiClass, boolean suggestAccessorsRenaming) {
      myClass = psiClass;
      mySuggestAccessorsRenaming = suggestAccessorsRenaming;
      prepare();
    }

    Project getProject() {
      return myClass.getProject();
    }

    PsiClass getPsiClass() {
      return myClass;
    }

    @NotNull
    Map<PsiField, @Nullable FieldAccessorCandidate> getFieldAccessors() {
      if (myFieldAccessorsCache != null) return myFieldAccessorsCache;

      Map<PsiField, FieldAccessorCandidate> result = new HashMap<>();
      for (var entry : myFieldAccessors.entrySet()) {
        PsiField newKey = entry.getKey();
        Collection<FieldAccessorCandidate> oldValue = entry.getValue();
        FieldAccessorCandidate newValue = ContainerUtil.getOnlyItem(oldValue);
        result.put(newKey, newValue);
      }
      myFieldAccessorsCache = result;
      return result;
    }

    @Nullable
    PsiMethod getCanonicalConstructor() {
      return myConstructors.size() == 1 ? myConstructors.get(0).myConstructor : null;
    }

    private boolean isValid() {
      if (myConstructors.size() > 1) return false;
      if (myConstructors.size() == 1) {
        RecordConstructorCandidate ctorCandidate = myConstructors.get(0);
        boolean isCanonical = ctorCandidate.myCanonical && throwsOnlyUncheckedExceptions(ctorCandidate.myConstructor);
        if (!isCanonical) return false;
      }
      if (myFieldAccessors.size() == 0) return false;
      for (var entry : myFieldAccessors.entrySet()) {
        PsiField field = entry.getKey();
        if (!field.hasModifierProperty(FINAL) || field.hasInitializer()) return false;
        if (HighlightUtil.RESTRICTED_RECORD_COMPONENT_NAMES.contains(field.getName())) return false;
        if (entry.getValue().size() > 1) return false;
      }
      for (PsiMethod ordinaryMethod : myOrdinaryMethods) {
        if (ordinaryMethod.hasModifierProperty(NATIVE)) return false;
        boolean conflictsWithPotentialAccessor = ordinaryMethod.getParameterList().isEmpty() &&
                                                 ContainerUtil.exists(myFieldAccessors.keySet(),
                                                                      field -> field.getName().equals(ordinaryMethod.getName()));
        if (conflictsWithPotentialAccessor) return false;
      }
      return true;
    }

    private void prepare() {
      Arrays.stream(myClass.getFields()).filter(field -> !field.hasModifierProperty(STATIC))
        .forEach(field -> myFieldAccessors.put(field, new ArrayList<>()));
      for (PsiMethod method : myClass.getMethods()) {
        if (method.isConstructor()) {
          myConstructors.add(new RecordConstructorCandidate(method, myFieldAccessors.keySet()));
          continue;
        }
        if (!throwsOnlyUncheckedExceptions(method)) {
          myOrdinaryMethods.add(method);
          continue;
        }
        FieldAccessorCandidate fieldAccessorCandidate = createFieldAccessor(method);
        if (fieldAccessorCandidate == null) {
          myOrdinaryMethods.add(method);
        }
        else {
          myFieldAccessors.putValue(fieldAccessorCandidate.myBackingField, fieldAccessorCandidate);
        }
      }
    }

    private static boolean throwsOnlyUncheckedExceptions(@NotNull PsiMethod psiMethod) {
      for (PsiClassType throwsType : psiMethod.getThrowsList().getReferencedTypes()) {
        PsiClassType throwsClassType = ObjectUtils.tryCast(throwsType, PsiClassType.class);
        if (throwsClassType == null) continue;
        if (!ExceptionUtil.isUncheckedException(throwsClassType)) {
          return false;
        }
      }
      return true;
    }

    @Nullable
    private FieldAccessorCandidate createFieldAccessor(@NotNull PsiMethod psiMethod) {
      if (!psiMethod.getParameterList().isEmpty()) return null;
      String methodName = psiMethod.getName();
      PsiField backingField = null;
      boolean recordStyleNaming = false;
      for (PsiField field : myFieldAccessors.keySet()) {
        if (!field.getType().equals(psiMethod.getReturnType())) continue;
        String fieldName = field.getName();
        if (fieldName.equals(methodName)) {
          backingField = field;
          recordStyleNaming = true;
          break;
        }
        if (mySuggestAccessorsRenaming && fieldName.equals(PropertyUtilBase.getPropertyNameByGetter(psiMethod))) {
          backingField = field;
          break;
        }
      }
      return backingField == null ? null : new FieldAccessorCandidate(psiMethod, backingField, recordStyleNaming);
    }
  }

  /**
   * Encapsulates information about the converting constructor e.g whether its canonical or not.
   */
  private static class RecordConstructorCandidate {
    private final PsiMethod myConstructor;
    private final boolean myCanonical;

    private RecordConstructorCandidate(@NotNull PsiMethod constructor, @NotNull Set<PsiField> instanceFields) {
      myConstructor = constructor;

      if (constructor.getTypeParameters().length > 0) {
        myCanonical = false;
        return;
      }
      PsiParameter[] ctorParams = myConstructor.getParameterList().getParameters();
      Map<String, PsiType> ctorParamsWithType = Arrays.stream(ctorParams)
        .collect(Collectors.toMap(param -> param.getName(), param -> param.getType(), (first, second) -> first));
      if (ctorParams.length != ctorParamsWithType.size()) {
        myCanonical = false;
        return;
      }
      for (PsiField instanceField : instanceFields) {
        PsiType ctorParamType = ObjectUtils.tryCast(ctorParamsWithType.get(instanceField.getName()), PsiType.class);
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
  static class FieldAccessorCandidate {
    private final PsiMethod myFieldAccessor;
    private final PsiField myBackingField;
    private final boolean myDefault;
    private final boolean myRecordStyleNaming;

    private FieldAccessorCandidate(@NotNull PsiMethod accessor, @NotNull PsiField backingField, boolean recordStyleNaming) {
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
      myDefault = !hasAnnotationConflict(accessor, backingField, TargetType.FIELD) &&
                  !hasAnnotationConflict(backingField, accessor, TargetType.METHOD);
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
  private static boolean hasAnnotationConflict(@NotNull PsiModifierListOwner first,
                                               @NotNull PsiModifierListOwner second,
                                               @NotNull TargetType targetType) {
    boolean result = false;
    for (PsiAnnotation firstAnn : first.getAnnotations()) {
      TargetType firstAnnTarget = AnnotationTargetUtil.findAnnotationTarget(firstAnn, targetType);
      boolean hasDesiredTarget = firstAnnTarget != null && firstAnnTarget != TargetType.UNKNOWN;
      if (!hasDesiredTarget) continue;
      if (!ContainerUtil.exists(second.getAnnotations(), secondAnn -> AnnotationUtil.equal(firstAnn, secondAnn))) {
        result = true;
        break;
      }
    }
    return result;
  }
}
