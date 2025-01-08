// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.openapi.util.Comparing;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
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

  private final @NotNull JavaErrorVisitor myVisitor;

  AnnotationChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

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
    if (!myVisitor.hasErrorResults()) checkSafeVarargAnnotation(annotation);
    if (!myVisitor.hasErrorResults()) checkTargetAnnotationDuplicates(annotation);
    if (!myVisitor.hasErrorResults()) checkFunctionalInterface(annotation);
    if (!myVisitor.hasErrorResults()) checkOverrideAnnotation(annotation);
  }

  private void checkFunctionalInterface(@NotNull PsiAnnotation annotation) {
    if (myVisitor.isApplicable(JavaFeature.LAMBDA_EXPRESSIONS) &&
        Comparing.strEqual(annotation.getQualifiedName(), CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE)) {
      PsiAnnotationOwner owner = annotation.getOwner();
      if (owner instanceof PsiModifierList list) {
        PsiElement parent = list.getParent();
        if (parent instanceof PsiClass psiClass) {
          switch (LambdaUtil.checkInterfaceFunctional(psiClass)) {
            case NOT_INTERFACE -> myVisitor.report(JavaErrorKinds.LAMBDA_NOT_FUNCTIONAL_INTERFACE.create(annotation, psiClass));
            case NO_ABSTRACT_METHOD -> myVisitor.report(JavaErrorKinds.LAMBDA_NO_TARGET_METHOD.create(annotation, psiClass));
            case MULTIPLE_ABSTRACT_METHODS -> myVisitor.report(JavaErrorKinds.LAMBDA_MULTIPLE_TARGET_METHODS.create(annotation, psiClass));
          }
          if (psiClass.hasModifierProperty(PsiModifier.SEALED)) {
            myVisitor.report(JavaErrorKinds.LAMBDA_FUNCTIONAL_INTERFACE_SEALED.create(annotation, psiClass));
          }
        }
      }
    }
  }

  private void checkSafeVarargAnnotation(@NotNull PsiAnnotation annotation) {
    if (!Comparing.strEqual(annotation.getQualifiedName(), CommonClassNames.JAVA_LANG_SAFE_VARARGS)) return;
    PsiAnnotationOwner owner = annotation.getOwner();
    if (!(owner instanceof PsiModifierList list)) return;
    PsiElement parent = list.getParent();
    if (parent instanceof PsiRecordComponent) {
      myVisitor.report(JavaErrorKinds.SAFE_VARARGS_ON_RECORD_COMPONENT.create(annotation));
    } else if (parent instanceof PsiMethod method) {
      if (!method.isVarArgs()) {
        myVisitor.report(JavaErrorKinds.SAFE_VARARGS_ON_FIXED_ARITY.create(annotation, method));
      }
      else if (!GenericsUtil.isSafeVarargsNoOverridingCondition(method)) {
        myVisitor.report(JavaErrorKinds.SAFE_VARARGS_ON_NON_FINAL_METHOD.create(annotation, method));
      }
    }
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

  private void checkTargetAnnotationDuplicates(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return;

    PsiElement resolved = nameRef.resolve();
    if (!(resolved instanceof PsiClass psiClass) || !CommonClassNames.JAVA_LANG_ANNOTATION_TARGET.equals(psiClass.getQualifiedName())) {
      return;
    }

    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length < 1) return;
    PsiAnnotationMemberValue value = attributes[0].getValue();
    if (!(value instanceof PsiArrayInitializerMemberValue initializerMemberValue)) return;
    PsiAnnotationMemberValue[] arrayInitializers = initializerMemberValue.getInitializers();
    Set<PsiElement> targets = new HashSet<>();
    for (PsiAnnotationMemberValue initializer : arrayInitializers) {
      if (initializer instanceof PsiReferenceExpression referenceExpression) {
        PsiElement target = referenceExpression.resolve();
        if (target != null) {
          if (targets.contains(target)) {
            myVisitor.report(JavaErrorKinds.ANNOTATION_REPEATED_TARGET.create(referenceExpression));
          }
          targets.add(target);
        }
      }
    }
  }

  private void checkOverrideAnnotation(@NotNull PsiAnnotation annotation) {
    if (!CommonClassNames.JAVA_LANG_OVERRIDE.equals(annotation.getQualifiedName())) return;
    if (!(annotation.getOwner() instanceof PsiModifierList list)) return;
    if (!(list.getParent() instanceof PsiMethod method)) return;
    if (method.hasModifierProperty(PsiModifier.STATIC)) {
      myVisitor.report(JavaErrorKinds.OVERRIDE_ON_STATIC_METHOD.create(annotation, method));
      return;
    }
    MethodSignatureBackedByPsiMethod superMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
    PsiClass psiClass = method.getContainingClass();
    if (psiClass != null) {
      if (superMethod != null && psiClass.isInterface()) {
        PsiMethod psiMethod = superMethod.getMethod();
        PsiClass superClass = psiMethod.getContainingClass();
        if (superClass != null &&
            CommonClassNames.JAVA_LANG_OBJECT.equals(superClass.getQualifiedName()) &&
            psiMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
          superMethod = null;
        }
      }
      else if (superMethod == null) {
        if (IncompleteModelUtil.isIncompleteModel(psiClass)) {
          if (!IncompleteModelUtil.isHierarchyResolved(psiClass)) {
            return;
          }
        }
        else {
          for (PsiClassType type : psiClass.getSuperTypes()) {
            // There's an unresolvable superclass: likely the error on @Override is induced.
            // Do not show an error on override, as it's reasonable to fix hierarchy first.
            if (type.resolve() == null) return;
          }
        }
      }
    }
    if (superMethod == null) {
      if (JavaPsiRecordUtil.getRecordComponentForAccessor(method) == null) {
        myVisitor.report(JavaErrorKinds.OVERRIDE_ON_NON_OVERRIDING_METHOD.create(annotation, method));
      }
      return;
    }
    PsiClass superClass = superMethod.getMethod().getContainingClass();
    if (superClass != null && superClass.isInterface()) {
      myVisitor.checkFeature(annotation, JavaFeature.OVERRIDE_INTERFACE);
    }
  }

  void checkNameValuePair(@NotNull PsiNameValuePair pair) {
    if (pair.getFirstChild() instanceof PsiErrorElement) return;
    PsiIdentifier identifier = pair.getNameIdentifier();
    if (identifier == null && pair.getParent() instanceof PsiAnnotationParameterList list) {
      PsiNameValuePair[] attributes = list.getAttributes();
      if (attributes.length > 1) {
        myVisitor.report(JavaErrorKinds.ANNOTATION_ATTRIBUTE_ANNOTATION_NAME_IS_MISSING.create(pair));
        return;
      }
    }
    PsiAnnotation annotation = PsiTreeUtil.getParentOfType(pair, PsiAnnotation.class);
    if (annotation == null) return;
    PsiClass annotationClass = annotation.resolveAnnotationType();
    if (annotationClass == null) return;
    PsiReference ref = pair.getReference();
    if (ref == null) return;
    PsiMethod method = (PsiMethod)ref.resolve();
    if (method == null) {
      myVisitor.report(JavaErrorKinds.ANNOTATION_ATTRIBUTE_UNKNOWN_METHOD.create(pair, ref.getCanonicalText()));
    }
    else if (method instanceof PsiAnnotationMethod annotationMethod) {
      PsiAnnotationMemberValue value = pair.getValue();
      if (value != null) {
        PsiType expectedType = Objects.requireNonNull(method.getReturnType());
        checkMemberValueType(value, expectedType, annotationMethod);
      }

      checkDuplicateAttribute(pair);
    }
  }

  private void checkDuplicateAttribute(@NotNull PsiNameValuePair pair) {
    PsiAnnotationParameterList annotation = (PsiAnnotationParameterList)pair.getParent();
    PsiNameValuePair[] attributes = annotation.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      if (attribute == pair) break;
      String name = pair.getName();
      if (Objects.equals(attribute.getName(), name)) {
        myVisitor.report(
          JavaErrorKinds.ANNOTATION_ATTRIBUTE_DUPLICATE.create(pair, name == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : name));
      }
    }
  }

  void checkMemberValueType(@NotNull PsiAnnotationMemberValue value,
                            @NotNull PsiType expectedType,
                            @NotNull PsiAnnotationMethod method) {
    if (expectedType instanceof PsiClassType && expectedType.equalsToText(CommonClassNames.JAVA_LANG_CLASS)) {
      if (!(value instanceof PsiClassObjectAccessExpression)) {
        myVisitor.report(JavaErrorKinds.ANNOTATION_ATTRIBUTE_NON_CLASS_LITERAL.create(value));
        return;
      }
    }
    if (method instanceof SyntheticElement) return;

    if (value instanceof PsiAnnotation annotation) {
      PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef == null) return;

      if (expectedType instanceof PsiClassType classType) {
        PsiClass aClass = classType.resolve();
        if (aClass != null && nameRef.isReferenceTo(aClass)) return;
      }

      if (expectedType instanceof PsiArrayType arrayType) {
        PsiType componentType = arrayType.getComponentType();
        if (componentType instanceof PsiClassType classType) {
          PsiClass aClass = classType.resolve();
          if (aClass != null && nameRef.isReferenceTo(aClass)) return;
        }
      }

      myVisitor.report(JavaErrorKinds.ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE.create(annotation, method, expectedType));
      return;
    }

    if (value instanceof PsiArrayInitializerMemberValue arrayValue && !(expectedType instanceof PsiArrayType)) {
      myVisitor.report(JavaErrorKinds.ANNOTATION_ATTRIBUTE_ILLEGAL_ARRAY_INITIALIZER.create(arrayValue, method, expectedType));
      return;
    }

    if (value instanceof PsiExpression expr) {
      PsiType type = expr.getType();

      PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass != null &&
          psiClass.isEnum() &&
          !(expr instanceof PsiReferenceExpression referenceExpression && referenceExpression.resolve() instanceof PsiEnumConstant)) {
        myVisitor.report(JavaErrorKinds.ANNOTATION_ATTRIBUTE_NON_ENUM_CONSTANT.create(expr));
        return;
      }

      if (type != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expr) ||
          expectedType instanceof PsiArrayType arrayType &&
          TypeConversionUtil.areTypesAssignmentCompatible(arrayType.getComponentType(), expr)) {
        return;
      }

      myVisitor.report(JavaErrorKinds.ANNOTATION_ATTRIBUTE_INCOMPATIBLE_TYPE.create(expr, method, expectedType));
    }
  }

  void checkArrayInitializer(@NotNull PsiArrayInitializerMemberValue initializer) {
    PsiMethod method = null;

    PsiElement parent = initializer.getParent();
    if (parent instanceof PsiNameValuePair) {
      PsiReference reference = parent.getReference();
      if (reference != null) {
        method = (PsiMethod)reference.resolve();
      }
    }
    else if (PsiUtil.isAnnotationMethod(parent)) {
      method = (PsiMethod)parent;
    }

    if (method instanceof PsiAnnotationMethod annotationMethod) {
      PsiType type = method.getReturnType();
      if (type instanceof PsiArrayType arrayType) {
        type = arrayType.getComponentType();
        PsiAnnotationMemberValue[] initializers = initializer.getInitializers();
        for (PsiAnnotationMemberValue initializer1 : initializers) {
          checkMemberValueType(initializer1, type, annotationMethod);
        }
      }
    }
  }
}
