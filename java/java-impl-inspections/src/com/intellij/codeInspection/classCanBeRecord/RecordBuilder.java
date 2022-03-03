// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.FieldAccessorCandidate;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiUtil;
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

  void addRecordHeader(@Nullable PsiMethod canonicalCtor, @NotNull Map<PsiField, @Nullable FieldAccessorCandidate> fieldAccessors) {
    myRecordText.append("(");
    StringJoiner recordComponentsJoiner = new StringJoiner(",");
    if (canonicalCtor == null) {
      fieldAccessors.forEach(
        (field, fieldAccessor) -> recordComponentsJoiner.add(generateComponentText(field, field.getType(), fieldAccessor)));
    }
    else {
      Arrays.stream(canonicalCtor.getParameterList().getParameters())
        .map(parameter -> generateComponentText(parameter, parameter.getType(), fieldAccessors))
        .forEach(recordComponentsJoiner::add);
    }
    myRecordText.append(recordComponentsJoiner);
    myRecordText.append(")");
  }

  void addCanonicalCtor(@NotNull PsiMethod canonicalCtor) {
    VisibilityUtil.setVisibility(canonicalCtor.getModifierList(), VisibilityUtil.getVisibilityModifier(myOriginClass.getModifierList()));
    processUncheckedExceptions(canonicalCtor);
    myRecordText.append(canonicalCtor.getText());
  }

  void addFieldAccessor(@NotNull FieldAccessorCandidate fieldAccessorCandidate) {
    PsiMethod fieldAccessor = fieldAccessorCandidate.getAccessor();
    if (fieldAccessorCandidate.isDefault()) {
      trimEndingWhiteSpaces();
      return;
    }
    PsiModifierList accessorModifiers = fieldAccessor.getModifierList();
    VisibilityUtil.setVisibility(accessorModifiers, PsiModifier.PUBLIC);
    processOverrideAnnotation(accessorModifiers);
    processUncheckedExceptions(fieldAccessor);
    myRecordText.append(fieldAccessor.getText());
  }

  void addModifierList(@NotNull PsiModifierList modifierList) {
    modifierList.setModifierProperty(PsiModifier.STATIC, false);
    modifierList.setModifierProperty(PsiModifier.FINAL, false);
    addPsiElement(modifierList);
  }

  void addPsiElement(@NotNull PsiElement psiElement) {
    myRecordText.append(psiElement.getText());
  }

  @NotNull
  PsiClass build() {
    PsiJavaFile psiFile = (PsiJavaFile)PsiFileFactory.getInstance(myOriginClass.getProject())
      .createFileFromText("Dummy.java", JavaLanguage.INSTANCE, myRecordText.toString(), false, false);
    PsiUtil.FILE_LANGUAGE_LEVEL_KEY.set(psiFile, LanguageLevel.JDK_16);
    return psiFile.getClasses()[0];
  }

  @NotNull
  private static String generateComponentText(@NotNull PsiParameter parameter, @NotNull PsiType componentType,
                                              @NotNull Map<PsiField, @Nullable FieldAccessorCandidate> fieldAccessors) {
    PsiField field = null;
    FieldAccessorCandidate fieldAccessorCandidate = null;
    for (var entry : fieldAccessors.entrySet()) {
      if (entry.getKey().getName().equals(parameter.getName())) {
        field = entry.getKey();
        fieldAccessorCandidate = entry.getValue();
        break;
      }
    }
    assert field != null;
    return generateComponentText(field, componentType, fieldAccessorCandidate);
  }

  @NotNull
  private static String generateComponentText(@NotNull PsiField field, @NotNull PsiType componentType,
                                              @Nullable FieldAccessorCandidate fieldAccessorCandidate) {
    PsiAnnotation[] fieldAnnotations = field.getAnnotations();
    String fieldAnnotationsText = Arrays.stream(fieldAnnotations).map(PsiAnnotation::getText).collect(Collectors.joining(" "));
    String annotationsText = fieldAnnotationsText == "" ? fieldAnnotationsText : fieldAnnotationsText + " ";
    if (fieldAccessorCandidate != null && fieldAccessorCandidate.isDefault()) {
      String accessorAnnotationsText = Arrays.stream(fieldAccessorCandidate.getAccessor().getAnnotations())
        .filter(accessorAnn -> !CommonClassNames.JAVA_LANG_OVERRIDE.equals(accessorAnn.getQualifiedName()))
        .filter(accessorAnn -> !ContainerUtil.exists(fieldAnnotations, fieldAnn -> AnnotationUtil.equal(fieldAnn, accessorAnn)))
        .map(PsiAnnotation::getText).collect(Collectors.joining(" "));
      annotationsText = accessorAnnotationsText == "" ? annotationsText : annotationsText + accessorAnnotationsText + " ";
    }
    return annotationsText + componentType.getCanonicalText() + " " + field.getName();
  }

  private void processOverrideAnnotation(@NotNull PsiModifierList accessorModifiers) {
    PsiAnnotation annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(CommonClassNames.JAVA_LANG_OVERRIDE,
                                                                                 PsiNameValuePair.EMPTY_ARRAY, accessorModifiers);
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
    if (myRecordText.length() == 0) return;
    int endIndex = myRecordText.length() - 1;
    if (myRecordText.charAt(endIndex) != ' ') return;

    while (endIndex > 0 && (myRecordText.charAt(endIndex - 1) == ' ' || myRecordText.charAt(endIndex - 1) == '\n')) {
      endIndex--;
    }
    myRecordText.setLength(endIndex);
  }

  private static PsiDocTag createDocTag(@NotNull PsiJavaParserFacade parserFacade, @NotNull PsiClassType throwsReferenceType) {
    return parserFacade.createDocTagFromText("@throws " + throwsReferenceType.getCanonicalText());
  }
}
