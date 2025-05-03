// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.FieldAccessorCandidate;
import com.intellij.codeInspection.classCanBeRecord.ConvertToRecordFix.RecordConstructorCandidate;
import com.intellij.java.syntax.parser.DeclarationParser;
import com.intellij.java.syntax.parser.JavaParser;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

final class RecordBuilder {
  private final StringBuilder myRecordText = new StringBuilder();
  private final PsiClass myOriginClass;

  RecordBuilder(@NotNull PsiClass originClass) {
    myOriginClass = originClass;
  }

  void addRecordDeclaration() {
    myRecordText.append("record");
  }

  void addRecordHeader(@Nullable RecordConstructorCandidate canonicalCtorCandidate,
                       @NotNull Map<PsiField, @Nullable FieldAccessorCandidate> fieldToAccessorCandidateMap) {
    myRecordText.append("(");
    StringJoiner recordComponentsJoiner = new StringJoiner(",");
    if (canonicalCtorCandidate == null) {
      fieldToAccessorCandidateMap.forEach((field, fieldAccessor) -> {
        recordComponentsJoiner.add(generateComponentText(field, field.getType(), fieldAccessor));
      });
    }
    else {
      PsiMethod canonicalCtor = canonicalCtorCandidate.getConstructorMethod();
      Arrays.stream(canonicalCtor.getParameterList().getParameters())
        .map(parameter -> {
          PsiField field = canonicalCtorCandidate.getCtorParamsToFields().get(parameter);
          if (field == null) {
            throw new IllegalStateException("no field found corresponding to constructor parameter '" + parameter.getName() + "'");
          }
          return generateComponentText(field, parameter, fieldToAccessorCandidateMap.get(field));
        })
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
    PsiMethod fieldAccessor = fieldAccessorCandidate.method();
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
    JavaDummyElement dummyElement = new JavaDummyElement(
      myRecordText.toString(), (builder, languageLevel) -> {
      new JavaParser(languageLevel).getDeclarationParser().parse(builder, DeclarationParser.Context.CLASS);
    }, LanguageLevel.JDK_16);
    DummyHolder holder = DummyHolderFactory.createHolder(myOriginClass.getManager(), dummyElement, myOriginClass);
    return (PsiClass)Objects.requireNonNull(SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode()));
  }

  private static @NotNull String generateComponentText(@NotNull PsiField field,
                                                       @NotNull PsiParameter ctorParameter,
                                                       @Nullable FieldAccessorCandidate fieldAccessorCandidate) {
    if (field == null) {
      throw new IllegalStateException("no field found to which the constructor parameter '" +
                                      ctorParameter.getType().toString() +
                                      ctorParameter.getName() +
                                      "' is assigned");
    }
    // Don't use parameter.getType() directly, as type annotations may differ; prefer type annotations on the field
    PsiType componentType = field.getType();
    if (ctorParameter.getType() instanceof PsiEllipsisType && componentType instanceof PsiArrayType arrayType) {
      componentType = new PsiEllipsisType(arrayType.getComponentType(), arrayType.getAnnotationProvider());
    }
    return generateComponentText(field, componentType, fieldAccessorCandidate);
  }

  private static @NotNull String generateComponentText(@NotNull PsiField field,
                                                       @NotNull PsiType componentType,
                                                       @Nullable FieldAccessorCandidate fieldAccessorCandidate) {
    PsiAnnotation[] fieldAnnotations = field.getAnnotations();
    String fieldAnnotationsText = Arrays.stream(fieldAnnotations)
      .filter(anno -> !AnnotationTargetUtil.isTypeAnnotation(anno))
      .map(PsiAnnotation::getText).collect(Collectors.joining(" "));
    String annotationsText = fieldAnnotationsText.isEmpty() ? fieldAnnotationsText : fieldAnnotationsText + " ";
    if (fieldAccessorCandidate != null && fieldAccessorCandidate.isDefault()) {
      String accessorAnnotationsText = Arrays.stream(fieldAccessorCandidate.method().getAnnotations())
        .filter(accessorAnn -> !CommonClassNames.JAVA_LANG_OVERRIDE.equals(accessorAnn.getQualifiedName()))
        .filter(anno -> !AnnotationTargetUtil.isTypeAnnotation(anno))
        .filter(accessorAnn -> !ContainerUtil.exists(fieldAnnotations, fieldAnn -> AnnotationUtil.equal(fieldAnn, accessorAnn)))
        .map(PsiAnnotation::getText).collect(Collectors.joining(" "));
      annotationsText = accessorAnnotationsText.isEmpty() ? annotationsText : annotationsText + accessorAnnotationsText + " ";
    }
    return annotationsText + componentType.getCanonicalText(true) + " " + field.getName();
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
    if (myRecordText.isEmpty()) return;
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
