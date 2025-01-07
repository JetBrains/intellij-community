// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

final class AnnotationChecker {
  private static final ElementPattern<PsiElement> ANY_ANNOTATION_ALLOWED = psiElement().andOr(
    psiElement().withParent(PsiNameValuePair.class),
    psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiNameValuePair.class),
    psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiAnnotationMethod.class),
    psiElement().withParent(PsiAnnotationMethod.class).afterLeaf(PsiKeyword.DEFAULT),
    // Unterminated parameter list like "void test(@NotNull String)": error on annotation looks annoying here
    psiElement().withParents(PsiModifierList.class, PsiParameterList.class)
  );

  private final JavaErrorVisitor myVisitor;

  AnnotationChecker(JavaErrorVisitor visitor) { myVisitor = visitor; }

  private void checkReferenceTarget(@NotNull PsiAnnotation annotation, @Nullable PsiJavaCodeReferenceElement ref) {
    if (ref == null) return;
    PsiElement refTarget = ref.resolve();
    if (refTarget == null) return;

    if (!(refTarget instanceof PsiClass)) {
      myVisitor.report(JavaErrorKinds.ANNOTATION_NOT_ALLOWED_REF.create(annotation));
      return;
    }

    PsiElement parent = ref.getParent();
    while (parent instanceof PsiJavaCodeReferenceElement referenceElement) {
      PsiElement qualified = referenceElement.resolve();
      if (qualified instanceof PsiMember member && member.hasModifierProperty(PsiModifier.STATIC)) {
        myVisitor.report(JavaErrorKinds.ANNOTATION_NOT_ALLOWED_STATIC.create(annotation));
        return;
      }
      if (qualified instanceof PsiClass) {
        parent = parent.getParent();
      }
      else {
        break;
      }
    }
  }

  @Contract("null->null; !null->!null")
  private static PsiJavaCodeReferenceElement getOutermostReferenceElement(@Nullable PsiJavaCodeReferenceElement ref) {
    if (ref == null) return null;

    while (ref.getQualifier() instanceof PsiJavaCodeReferenceElement referenceElement) {
      ref = referenceElement;
    }
    return ref;
  }
  
  void checkAnnotation(@NotNull PsiAnnotation annotation) {
    if (!myVisitor.hasErrorResults()) checkAnnotationApplicability(annotation);
    if (!myVisitor.hasErrorResults()) checkAnnotationType(annotation);
    if (!myVisitor.hasErrorResults()) checkMissingAttributes(annotation);
  }

  private void checkMissingAttributes(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return;
    PsiElement aClass = nameRef.resolve();
    if (aClass instanceof PsiClass psiClass && psiClass.isAnnotationType()) {
      Set<String> names = new HashSet<>();
      PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        String name = attribute.getName();
        names.add(Objects.requireNonNullElse(name, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME));
      }

      PsiMethod[] annotationMethods = psiClass.getMethods();
      List<String> missed = new ArrayList<>();
      for (PsiMethod method : annotationMethods) {
        if (PsiUtil.isAnnotationMethod(method)) {
          PsiAnnotationMethod annotationMethod = (PsiAnnotationMethod)method;
          if (annotationMethod.getDefaultValue() == null) {
            if (!names.contains(annotationMethod.getName())) {
              missed.add(annotationMethod.getName());
            }
          }
        }
      }

      if (!missed.isEmpty()) {
        myVisitor.report(JavaErrorKinds.ANNOTATION_MISSING_ATTRIBUTE.create(annotation, missed));
      }
    }
  }

  private void checkAnnotationType(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
    if (nameReferenceElement != null) {
      PsiElement resolved = nameReferenceElement.resolve();
      if (resolved != null && (!(resolved instanceof PsiClass psiClass) || !psiClass.isAnnotationType())) {
        myVisitor.report(JavaErrorKinds.ANNOTATION_TYPE_EXPECTED.create(nameReferenceElement));
      }
    }
  }

  private void checkAnnotationApplicability(@NotNull PsiAnnotation annotation) {
    if (ANY_ANNOTATION_ALLOWED.accepts(annotation)) return;
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return;

    PsiAnnotationOwner owner = annotation.getOwner();
    PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(owner);
    if (owner == null || targets.length == 0) {
      myVisitor.report(JavaErrorKinds.ANNOTATION_NOT_ALLOWED_HERE.create(annotation));
      return;
    }
    if (!(owner instanceof PsiModifierList)) {
      myVisitor.checkFeature(annotation, JavaFeature.TYPE_ANNOTATIONS);
      if (myVisitor.hasErrorResults()) return;
    }

    PsiAnnotation.TargetType applicable = AnnotationTargetUtil.findAnnotationTarget(annotation, targets);
    if (applicable == PsiAnnotation.TargetType.UNKNOWN) return;

    if (applicable == null) {
      if (targets.length == 1 && targets[0] == PsiAnnotation.TargetType.TYPE_USE) {
        PsiElement parent = annotation.getParent();
        if (parent instanceof PsiTypeElement) {
          PsiElement modifierList =
            PsiTreeUtil.skipSiblingsBackward(parent, PsiWhiteSpace.class, PsiComment.class, PsiTypeParameterList.class);
          if (modifierList instanceof PsiModifierList psiModifierList) {
            targets = AnnotationTargetUtil.getTargetsForLocation(psiModifierList);
            if (AnnotationTargetUtil.findAnnotationTarget(annotation, targets) == null) {
              myVisitor.report(JavaErrorKinds.ANNOTATION_NOT_APPLICABLE.create(annotation, Arrays.asList(targets)));
            }
            return;
          }
        }
      }
      myVisitor.report(JavaErrorKinds.ANNOTATION_NOT_APPLICABLE.create(annotation, Arrays.asList(targets)));
      return;
    }

    if (applicable == PsiAnnotation.TargetType.TYPE_USE) {
      if (PsiTreeUtil.skipParentsOfType(annotation, PsiTypeElement.class) instanceof PsiClassObjectAccessExpression) {
        myVisitor.report(JavaErrorKinds.ANNOTATION_NOT_ALLOWED_CLASS.create(annotation));
        return;
      }
      if (owner instanceof PsiClassReferenceType referenceType) {
        PsiJavaCodeReferenceElement ref = referenceType.getReference();
        checkReferenceTarget(annotation, ref);
      }
      else if (owner instanceof PsiModifierList || owner instanceof PsiTypeElement) {
        PsiElement nextElement = owner instanceof PsiTypeElement typeElementOwner
                                 ? typeElementOwner
                                 : PsiTreeUtil.skipSiblingsForward((PsiModifierList)owner, PsiComment.class, PsiWhiteSpace.class,
                                                                   PsiTypeParameterList.class);
        if (nextElement instanceof PsiTypeElement typeElement) {
          PsiType type = typeElement.getType();
          //see JLS 9.7.4 Where Annotations May Appear
          if (PsiTypes.voidType().equals(type)) {
            myVisitor.report(JavaErrorKinds.ANNOTATION_NOT_ALLOWED_VOID.create(annotation));
            return;
          }
          if (typeElement.isInferredType()) {
            myVisitor.report(JavaErrorKinds.ANNOTATION_NOT_ALLOWED_VAR.create(annotation));
            return;
          }
          if (!(type instanceof PsiPrimitiveType || type instanceof PsiArrayType)) {
            PsiJavaCodeReferenceElement ref = getOutermostReferenceElement(typeElement.getInnermostComponentReferenceElement());
            checkReferenceTarget(annotation, ref);
          }
        }
      }
    }
  }
}
