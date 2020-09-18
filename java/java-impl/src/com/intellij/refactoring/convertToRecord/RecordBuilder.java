// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.convertToRecord;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.refactoring.convertToRecord.ConvertToRecordHandler.FieldAccessorDefinition;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

class RecordBuilder {
  private final StringBuilder myRecordText = new StringBuilder();
  private final PsiClass myOriginClass;

  RecordBuilder(@NotNull PsiClass originClass) {
    myOriginClass = originClass;
  }

  void addRecordDeclaration() {
    myRecordText.append("record");
  }

  void addRecordHeader(@Nullable PsiMethod canonicalCtor, @NotNull Map<PsiField, @Nullable FieldAccessorDefinition> fieldAccessors) {
    myRecordText.append("(");
    if (canonicalCtor != null) {
      StringJoiner recordComponentsJoiner = new StringJoiner(", ");
      for (JvmParameter jvmParameter : canonicalCtor.getParameters()) {
        PsiParameter parameter = ObjectUtils.tryCast(jvmParameter, PsiParameter.class);
        if (parameter == null) continue;
        String parameterName = parameter.getName();
        String annotationsText = generateAnnotationsText(parameterName, fieldAccessors);
        recordComponentsJoiner.add(annotationsText + parameter.getType().getCanonicalText() + " " + parameterName);
      }
      myRecordText.append(recordComponentsJoiner.toString());
    }
    myRecordText.append(")");
  }

  void addCanonicalCtor(@NotNull PsiMethod canonicalCtor) {
    VisibilityUtil.setVisibility(canonicalCtor.getModifierList(), VisibilityUtil.getVisibilityModifier(myOriginClass.getModifierList()));
    processUncheckedExceptions(canonicalCtor);
    CodeStyleManager.getInstance(myOriginClass.getProject()).reformat(canonicalCtor);
    myRecordText.append(canonicalCtor.getText());
  }

  void addFieldAccessor(@NotNull FieldAccessorDefinition fieldAccessorDef) {
    PsiMethod fieldAccessor = fieldAccessorDef.getAccessor();
    if (fieldAccessorDef.isDefault()) {
      trimEndingWhiteSpaces();
      return;
    }
    PsiModifierList fieldModifiers = fieldAccessor.getModifierList();
    VisibilityUtil.setVisibility(fieldModifiers, PsiModifier.PUBLIC);
    processOverrideAnnotation(fieldModifiers);
    processUncheckedExceptions(fieldAccessor);
    CodeStyleManager.getInstance(myOriginClass.getProject()).reformat(fieldAccessor);
    myRecordText.append(fieldAccessor.getText());
  }

  void addPsiElement(@NotNull PsiElement psiElement) {
    myRecordText.append(psiElement.getText());
  }

  @NotNull
  PsiClass build() {
    return JavaPsiFacade.getElementFactory(myOriginClass.getProject()).createRecordFromText(myRecordText.toString(), null);
  }

  @NotNull
  private static String generateAnnotationsText(@NotNull String parameterName,
                                                @NotNull Map<PsiField, @Nullable FieldAccessorDefinition> fieldAccessors) {
    PsiField field = null;
    FieldAccessorDefinition fieldAccessorDef = null;
    for (var entry : fieldAccessors.entrySet()) {
      if (entry.getKey().getName().equals(parameterName)) {
        field = entry.getKey();
        fieldAccessorDef = entry.getValue();
      }
    }
    assert field != null;
    PsiAnnotation[] fieldAnnotations = field.getAnnotations();
    String fieldAnnotationsText = Arrays.stream(fieldAnnotations).map(PsiAnnotation::getText).collect(Collectors.joining(" "));
    String annotationsText = fieldAnnotationsText == "" ? fieldAnnotationsText : fieldAnnotationsText + " ";
    if (fieldAccessorDef != null && fieldAccessorDef.isDefault()) {
      String accessorAnnotationsText = Arrays.stream(fieldAccessorDef.getAccessor().getAnnotations())
        .filter(accessorAnn -> !CommonClassNames.JAVA_LANG_OVERRIDE.equals(accessorAnn.getQualifiedName()))
        .filter(accessorAnn -> !ContainerUtil.exists(fieldAnnotations, fieldAnn -> AnnotationUtil.equal(fieldAnn, accessorAnn)))
        .map(PsiAnnotation::getText).collect(Collectors.joining(" "));
      annotationsText = accessorAnnotationsText == "" ? annotationsText : annotationsText + accessorAnnotationsText + " ";
    }
    return annotationsText;
  }

  private void processOverrideAnnotation(@NotNull PsiModifierList fieldModifiers) {
    PsiAnnotation annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(CommonClassNames.JAVA_LANG_OVERRIDE,
                                                                                 PsiNameValuePair.EMPTY_ARRAY, fieldModifiers);
    if (annotation != null) {
      JavaCodeStyleManager.getInstance(myOriginClass.getProject()).shortenClassReferences(annotation);
    }
  }

  private void processUncheckedExceptions(@NotNull PsiMethod fieldAccessor) {
    PsiReferenceList fieldAccessorThrowsList = fieldAccessor.getThrowsList();
    PsiClassType[] throwsReferenceTypes = fieldAccessorThrowsList.getReferencedTypes();
    if (throwsReferenceTypes.length == 0) return;

    PsiDocComment existingComment = fieldAccessor.getDocComment();
    PsiJavaParserFacade parserFacade = JavaPsiFacade.getElementFactory(myOriginClass.getProject());
    if (existingComment == null) {
      PsiDocComment newComment = parserFacade.createDocCommentFromText("/***/", fieldAccessor);
      Arrays.stream(throwsReferenceTypes).forEach(rt -> newComment.add(createDocTag(parserFacade, rt)));
      fieldAccessor.addBefore(newComment, fieldAccessor.getFirstChild());
    }
    else {
      PsiDocTag[] throwsTags = existingComment.findTagsByName("throws");
      for (PsiClassType throwsReferenceType : throwsReferenceTypes) {
        boolean tagAlreadyExists = ContainerUtil.exists(throwsTags, throwTag -> {
          PsiClass resolvedClass = JavaDocUtil.resolveClassInTagValue(throwTag.getValueElement());
          if (resolvedClass == null) return false;
          return throwsReferenceType.equalsToText(Objects.requireNonNull(resolvedClass.getQualifiedName()));
        });
        if (!tagAlreadyExists) {
          existingComment.add(createDocTag(parserFacade, throwsReferenceType));
        }
      }
    }
    fieldAccessorThrowsList.deleteChildRange(fieldAccessorThrowsList.getFirstChild(), fieldAccessorThrowsList.getLastChild());
  }

  private void trimEndingWhiteSpaces() {
    int endIndex = myRecordText.length();
    if (endIndex == 0) return;
    if (myRecordText.charAt(endIndex - 1) != ' ') return;

    int[] chars = myRecordText.chars().toArray();
    int startIndex;
    for (startIndex = endIndex - 1; startIndex >= 0; startIndex--) {
      if (chars[startIndex] != ' ' && chars[startIndex] != '\n') break;
    }
    myRecordText.delete(++startIndex, endIndex);
  }

  private static PsiDocTag createDocTag(@NotNull PsiJavaParserFacade parserFacade, @NotNull PsiClassType throwsReferenceType) {
    return parserFacade.createDocTagFromText("@throws " + throwsReferenceType.getCanonicalText());
  }
}
