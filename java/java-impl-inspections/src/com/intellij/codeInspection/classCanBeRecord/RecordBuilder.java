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
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
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
      fieldToAccessorCandidateMap.forEach((field, fieldAccessorCandidate) -> {
        recordComponentsJoiner.add(generateComponentText(field.getAnnotations(), field.getName(), field.getType(), fieldAccessorCandidate));
      });
    }
    else {
      PsiParameter[] ctorParams = canonicalCtorCandidate.constructor().getParameterList().getParameters();
      for (PsiParameter parameter : ctorParams) {
        if (!canonicalCtorCandidate.paramsToFields().containsKey(parameter)) continue;
        PsiField field = canonicalCtorCandidate.paramsToFields().get(parameter);
        if (field == null) {
          String componentText = generateComponentText(PsiAnnotation.EMPTY_ARRAY, parameter.getName(), parameter.getType(), null);
          recordComponentsJoiner.add(componentText);
          continue;
        }
        FieldAccessorCandidate fieldAccessorCandidate = fieldToAccessorCandidateMap.get(field);
        String componentText = generateComponentText(field, parameter, fieldAccessorCandidate);
        recordComponentsJoiner.add(componentText);
      }
    }
    myRecordText.append(recordComponentsJoiner);
    myRecordText.append(")");
  }

  void addCanonicalCtor(@NotNull PsiMethod ctor) {
    // An explicitly declared canonical constructor must provide at least as much access as the record class. See JLS 8.10.4 
    VisibilityUtil.setVisibility(ctor.getModifierList(), VisibilityUtil.getVisibilityModifier(myOriginClass.getModifierList()));
    processUncheckedExceptions(ctor);
    myRecordText.append(ctor.getText());
  }

  //@formatter:off Temporarily disable formatter because of bug IDEA-371809
  /// Converts the non-canonical, non-delegating `ctor` (that directly assigns all instance fields) to
  /// a single call to the canonical constructor.
  /// 
  /// A sample record with an implicit canonical constructor and a delegating constructor:
  /// 
  /// Before:
  /// 
  /// ```java
  /// class Person {
  ///   final String name;
  ///   final int age;
  ///   final int weight;
  /// 
  ///   Person(String name, int age, int weight) {
  ///     this.name = name;
  ///     this.age = age;
  ///     this.weight = weight;
  ///     System.out.println("Created a person!");
  ///   }
  /// }
  /// ```
  /// 
  /// After:
  /// 
  /// ```java
  /// record Person(String name, int age, int weight) {
  ///   Person(String name) {
  ///     this(name, 42, 100);
  ///     System.out.println("Created a person!");
  ///   }
  /// }
  /// ```
  //@formatter:on
  void addDelegatingCtor(@NotNull PsiMethod canonicalCtor,
                         @NotNull PsiMethod ctor,
                         @NotNull Map<@NotNull String, @NotNull PsiExpression> fieldNamesToInitializers,
                         @NotNull Set<@NotNull PsiStatement> trailingStatements) {
    processUncheckedExceptions(ctor);
    final PsiCodeBlock body = ctor.getBody();
    assert body != null;

    PsiStatement[] statements = body.getStatements();
    CommentTracker ct = new CommentTracker();
    for (int i = 0; i < statements.length; i++) {
      @NotNull PsiStatement statement = statements[i];
      @Nullable PsiStatement nextStatement = i < statements.length - 1 ? statements[i + 1] : null;

      if (!trailingStatements.contains(statement)) {
        final boolean isLastAssignmentStatement = nextStatement == null || trailingStatements.contains(nextStatement);
        if (isLastAssignmentStatement) {
          PsiStatement delegatingCtorCall = createDelegatingCtorCall(canonicalCtor, fieldNamesToInitializers);
          ct.replaceAndRestoreComments(statement, delegatingCtorCall);
        }
        else {
          ct.delete(statement);
        }
      }
    }

    processUncheckedExceptions(ctor);
    myRecordText.append(ctor.getText());
  }

  private static PsiStatement createDelegatingCtorCall(@NotNull PsiMethod canonicalCtor,
                                                       @NotNull Map<@NotNull String, @NotNull PsiExpression> fieldNamesToInitializers) {
    StringBuilder delegatingCtorInvocationText = new StringBuilder();
    delegatingCtorInvocationText.append("this(");

    List<@NotNull PsiExpression> expressionsInCorrectOrder = new ArrayList<>();
    for (PsiParameter canonicalCtorParameter : canonicalCtor.getParameterList().getParameters()) {
      PsiExpression fieldInitializerExpr = fieldNamesToInitializers.get(canonicalCtorParameter.getName());
      if (fieldInitializerExpr != null) {
        expressionsInCorrectOrder.add(fieldInitializerExpr);
      }
    }

    delegatingCtorInvocationText.append(expressionsInCorrectOrder.stream().map(PsiExpression::getText).collect(Collectors.joining(", ")));
    delegatingCtorInvocationText.append(");");

    PsiElementFactory factory = PsiElementFactory.getInstance(canonicalCtor.getProject());
    return factory.createStatementFromText(delegatingCtorInvocationText.toString(), canonicalCtor);
  }

  void addCtor(@NotNull PsiMethod ctor) {
    processUncheckedExceptions(ctor);
    myRecordText.append(ctor.getText());
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

  @NotNull PsiClass build() {
    JavaDummyElement dummyElement = new JavaDummyElement(myRecordText.toString(), (builder, languageLevel) -> {
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
    // Don't use parameter.getType() directly, as kind annotations may differ; prefer kind annotations on the field
    PsiType componentType = field.getType();
    if (ctorParameter.getType() instanceof PsiEllipsisType && componentType instanceof PsiArrayType arrayType) {
      componentType = new PsiEllipsisType(arrayType.getComponentType(), arrayType.getAnnotationProvider());
    }
    return generateComponentText(field.getAnnotations(), field.getName(), componentType, fieldAccessorCandidate);
  }

  private static @NotNull String generateComponentText(@NotNull PsiAnnotation @NotNull [] fieldAnnotations,
                                                       @NotNull String fieldName,
                                                       @NotNull PsiType componentType,
                                                       @Nullable FieldAccessorCandidate fieldAccessorCandidate) {
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
    return annotationsText + componentType.getCanonicalText(true) + " " + fieldName;
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
