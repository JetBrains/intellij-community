/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author ven
 */
public class AnnotationsHighlightUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil");

  @Nullable
  static HighlightInfo checkNameValuePair(PsiNameValuePair pair) {
    PsiReference ref = pair.getReference();
    if (ref == null) return null;
    PsiMethod method = (PsiMethod)ref.resolve();
    if (method == null) {
      if (pair.getName() != null) {
        final String description = JavaErrorMessages.message("annotation.unknown.method", ref.getCanonicalText());
        PsiElement element = ref.getElement();
        final HighlightInfo highlightInfo =
          HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(element).descriptionAndTooltip(description).create();
        QuickFixAction.registerQuickFixAction(highlightInfo, QuickFixFactory.getInstance().createCreateAnnotationMethodFromUsageFix(pair));
        return highlightInfo;
      }
      else {
        String description = JavaErrorMessages.message("annotation.missing.method", ref.getCanonicalText());
        PsiElement element = ref.getElement();
        final HighlightInfo highlightInfo =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
        for (IntentionAction action : QuickFixFactory.getInstance().createAddAnnotationAttributeNameFixes(pair)) {
          QuickFixAction.registerQuickFixAction(highlightInfo, action);
        }
        return highlightInfo;
      }
    }
    else {
      PsiType returnType = method.getReturnType();
      assert returnType != null : method;
      PsiAnnotationMemberValue value = pair.getValue();
      HighlightInfo info = checkMemberValueType(value, returnType);
      if (info != null) return info;

      return checkDuplicateAttribute(pair);
    }
  }

  @Nullable
  private static HighlightInfo checkDuplicateAttribute(PsiNameValuePair pair) {
    PsiAnnotationParameterList annotation = (PsiAnnotationParameterList)pair.getParent();
    PsiNameValuePair[] attributes = annotation.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      if (attribute == pair) break;
      String name = pair.getName();
      if (Comparing.equal(attribute.getName(), name)) {
        String description = JavaErrorMessages.message("annotation.duplicate.attribute",
                                                       name == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : name);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pair).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  private static String formatReference(PsiJavaCodeReferenceElement ref) {
    return ref.getCanonicalText();
  }

  @Nullable
  static HighlightInfo checkMemberValueType(@Nullable PsiAnnotationMemberValue value, PsiType expectedType) {
    if (value == null) return null;

    if (expectedType instanceof PsiClassType && expectedType.equalsToText(CommonClassNames.JAVA_LANG_CLASS)) {
      if (!(value instanceof PsiClassObjectAccessExpression)) {
        String description = JavaErrorMessages.message("annotation.non.class.literal.attribute.value");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description).create();
      }
    }

    if (value instanceof PsiAnnotation) {
      PsiJavaCodeReferenceElement nameRef = ((PsiAnnotation)value).getNameReferenceElement();
      if (nameRef == null) return null;

      if (expectedType instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType)expectedType).resolve();
        if (aClass != null && nameRef.isReferenceTo(aClass)) return null;
      }

      if (expectedType instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)expectedType).getComponentType();
        if (componentType instanceof PsiClassType) {
          PsiClass aClass = ((PsiClassType)componentType).resolve();
          if (aClass != null && nameRef.isReferenceTo(aClass)) return null;
        }
      }

      String description = JavaErrorMessages.message("incompatible.types", JavaHighlightUtil.formatType(expectedType), formatReference(nameRef) );
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description).create();
    }

    if (value instanceof PsiArrayInitializerMemberValue) {
      if (expectedType instanceof PsiArrayType) return null;
      String description = JavaErrorMessages.message("annotation.illegal.array.initializer", JavaHighlightUtil.formatType(expectedType));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description).create();
    }

    if (value instanceof PsiExpression) {
      PsiExpression expr = (PsiExpression)value;
      PsiType type = expr.getType();

      final PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass != null && psiClass.isEnum() && !(expr instanceof PsiReferenceExpression && ((PsiReferenceExpression)expr).resolve() instanceof PsiEnumConstant)) {
        String description = JavaErrorMessages.message("annotation.non.enum.constant.attribute.value");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description).create();
      }

      if (type != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expr) ||
          expectedType instanceof PsiArrayType &&
          TypeConversionUtil.areTypesAssignmentCompatible(((PsiArrayType)expectedType).getComponentType(), expr)) {
        return null;
      }

      String description = JavaErrorMessages.message("incompatible.types", JavaHighlightUtil.formatType(expectedType), JavaHighlightUtil.formatType(type));
      HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description).create();
      QuickFixAction.registerQuickFixAction(info, QuickFixFactory.getInstance().createSurroundWithQuotesAnnotationParameterValueFix(value, expectedType));
      return info;
    }

    LOG.error("Unknown annotation member value: " + value);
    return null;
  }

  static HighlightInfo checkDuplicateAnnotations(@NotNull PsiAnnotation annotationToCheck, @NotNull LanguageLevel languageLevel) {
    PsiAnnotationOwner owner = annotationToCheck.getOwner();
    if (owner == null) return null;

    PsiJavaCodeReferenceElement element = annotationToCheck.getNameReferenceElement();
    if (element == null) return null;
    PsiElement resolved = element.resolve();
    if (!(resolved instanceof PsiClass)) return null;

    PsiClass annotationType = (PsiClass)resolved;

    PsiClass contained = contained(annotationType);
    String containedElementFQN = contained == null ? null : contained.getQualifiedName();

    if (containedElementFQN != null) {
      String containerName = annotationType.getQualifiedName();
      if (isAnnotationRepeatedTwice(owner, containedElementFQN)) {
        String description = JavaErrorMessages.message("annotation.container.wrong.place", containerName);
        return annotationError(annotationToCheck, description);
      }
    }
    else if (isAnnotationRepeatedTwice(owner, annotationType.getQualifiedName())) {
      if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        String description = JavaErrorMessages.message("annotation.duplicate.annotation");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      }

      PsiAnnotation metaAnno = PsiImplUtil.findAnnotation(annotationType.getModifierList(), CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE);
      if (metaAnno == null) {
        String explanation = JavaErrorMessages.message("annotation.non.repeatable", annotationType.getQualifiedName());
        String description = JavaErrorMessages.message("annotation.duplicate.explained", explanation);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      }

      String explanation = doCheckRepeatableAnnotation(metaAnno);
      if (explanation != null) {
        String description = JavaErrorMessages.message("annotation.duplicate.explained", explanation);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      }

      PsiClass container = getRepeatableContainer(metaAnno);
      if (container != null) {
        PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(owner);
        PsiAnnotation.TargetType applicable = AnnotationTargetUtil.findAnnotationTarget(container, targets);
        if (applicable == null) {
          String target = JavaErrorMessages.message("annotation.target." + targets[0]);
          String message = JavaErrorMessages.message("annotation.container.not.applicable", container.getName(), target);
          return annotationError(annotationToCheck, message);
        }
      }
    }

    return null;
  }

  // returns contained element
  private static PsiClass contained(PsiClass annotationType) {
    if (!annotationType.isAnnotationType()) return null;
    PsiMethod[] values = annotationType.findMethodsByName("value", false);
    if (values.length != 1) return null;
    PsiMethod value = values[0];
    PsiType returnType = value.getReturnType();
    if (!(returnType instanceof PsiArrayType)) return null;
    PsiType type = ((PsiArrayType)returnType).getComponentType();
    if (!(type instanceof PsiClassType)) return null;
    PsiClass contained = ((PsiClassType)type).resolve();
    if (contained == null || !contained.isAnnotationType()) return null;
    if (PsiImplUtil.findAnnotation(contained.getModifierList(), CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE) == null) return null;

    return contained;
  }

  private static boolean isAnnotationRepeatedTwice(@NotNull PsiAnnotationOwner owner, @Nullable String qualifiedName) {
    int count = 0;
    for (PsiAnnotation annotation : owner.getAnnotations()) {
      PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef == null) continue;
      PsiElement resolved = nameRef.resolve();
      if (!(resolved instanceof PsiClass) || !Comparing.equal(qualifiedName, ((PsiClass)resolved).getQualifiedName())) continue;
      if (++count == 2) return true;
    }
    return false;
  }

  @Nullable
  static HighlightInfo checkMissingAttributes(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;
    PsiClass aClass = (PsiClass)nameRef.resolve();
    if (aClass != null && aClass.isAnnotationType()) {
      Set<String> names = new HashSet<>();
      PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        final String name = attribute.getName();
        if (name != null) {
          names.add(name);
        }
        else {
          names.add(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
        }
      }

      PsiMethod[] annotationMethods = aClass.getMethods();
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
        StringBuffer buff = new StringBuffer("'" + missed.get(0) + "'");
        for (int i = 1; i < missed.size(); i++) {
          buff.append(", ");
          buff.append("'").append(missed.get(i)).append("'");
        }

        String description = JavaErrorMessages.message("annotation.missing.attribute", buff);
        HighlightInfo info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameRef).descriptionAndTooltip(description).create();
        IntentionAction fix = QuickFixFactory.getInstance().createAddMissingRequiredAnnotationParametersFix(
          annotation, annotationMethods, missed);
        QuickFixAction.registerQuickFixAction(info, fix);
        return info;
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkConstantExpression(PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (PsiUtil.isAnnotationMethod(parent) || parent instanceof PsiNameValuePair || parent instanceof PsiArrayInitializerMemberValue) {
      if (!PsiUtil.isConstantExpression(expression)) {
        String description = JavaErrorMessages.message("annotation.non.constant.attribute.value");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkValidAnnotationType(PsiType type, PsiTypeElement typeElement) {
    if (type != null && type.accept(AnnotationReturnTypeVisitor.INSTANCE).booleanValue()) {
      return null;
    }
    String description = JavaErrorMessages.message("annotation.invalid.annotation.member.type", type != null ? type.getPresentableText() : null);
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
  }

  private static final ElementPattern<PsiElement> ANY_ANNOTATION_ALLOWED = psiElement().andOr(
    psiElement().withParent(PsiNameValuePair.class),
    psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiNameValuePair.class),
    psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiAnnotationMethod.class),
    psiElement().withParent(PsiAnnotationMethod.class).afterLeaf(PsiKeyword.DEFAULT)
  );

  @Nullable
  public static HighlightInfo checkApplicability(@NotNull PsiAnnotation annotation, @NotNull LanguageLevel level, @NotNull PsiFile file) {
    if (ANY_ANNOTATION_ALLOWED.accepts(annotation)) {
      return null;
    }

    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;

    PsiAnnotationOwner owner = annotation.getOwner();
    PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(owner);
    if (owner == null || targets.length == 0) {
      String message = JavaErrorMessages.message("annotation.not.allowed.here");
      return annotationError(annotation, message);
    }

    if (!(owner instanceof PsiModifierList)) {
      HighlightInfo info = HighlightUtil.checkFeature(annotation, HighlightUtil.Feature.TYPE_ANNOTATIONS, level, file);
      if (info != null) return info;
    }

    PsiAnnotation.TargetType applicable = AnnotationTargetUtil.findAnnotationTarget(annotation, targets);
    if (applicable == PsiAnnotation.TargetType.UNKNOWN) return null;

    if (applicable == null) {
      String target = JavaErrorMessages.message("annotation.target." + targets[0]);
      String message = JavaErrorMessages.message("annotation.not.applicable", nameRef.getText(), target);
      return annotationError(annotation, message);
    }

    if (applicable == PsiAnnotation.TargetType.TYPE_USE) {
      if (owner instanceof PsiClassReferenceType) {
        PsiJavaCodeReferenceElement ref = ((PsiClassReferenceType)owner).getReference();
        HighlightInfo info = checkReferenceTarget(annotation, ref);
        if (info != null) return info;
      }
      else if (owner instanceof PsiModifierList || owner instanceof PsiTypeElement) {
        PsiElement nextElement = owner instanceof PsiTypeElement
            ? (PsiTypeElement)owner
            : PsiTreeUtil.skipSiblingsForward((PsiModifierList)owner, PsiComment.class, PsiWhiteSpace.class, PsiTypeParameterList.class);
        if (nextElement instanceof PsiTypeElement) {
          PsiTypeElement typeElement = (PsiTypeElement)nextElement;
          PsiType type = typeElement.getType();
          if (PsiType.VOID.equals(type)) {
            String message = JavaErrorMessages.message("annotation.not.allowed.void");
            return annotationError(annotation, message);
          }
          if (!(type instanceof PsiPrimitiveType)) {
            PsiJavaCodeReferenceElement ref = getOutermostReferenceElement(typeElement.getInnermostComponentReferenceElement());
            HighlightInfo info = checkReferenceTarget(annotation, ref);
            if (info != null) return info;
          }
          PsiElement context = PsiTreeUtil.skipParentsOfType(typeElement, PsiTypeElement.class);
          if (context instanceof PsiClassObjectAccessExpression) {
            String message = JavaErrorMessages.message("annotation.not.allowed.class");
            return annotationError(annotation, message);
          }
        }
      }
    }

    return null;
  }

  private static HighlightInfo annotationError(PsiAnnotation annotation, String message) {
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(annotation).descriptionAndTooltip(message).create();
    QuickFixAction.registerQuickFixAction(info, new DeleteAnnotationAction(annotation));
    return info;
  }

  @Nullable
  private static HighlightInfo checkReferenceTarget(PsiAnnotation annotation, @Nullable PsiJavaCodeReferenceElement ref) {
    if (ref == null) return null;
    PsiElement refTarget = ref.resolve();
    if (refTarget == null) return null;

    String message = null;
    if (!(refTarget instanceof PsiClass)) {
      message = JavaErrorMessages.message("annotation.not.allowed.ref");
    }
    else {
      PsiElement parent = ref.getParent();
      if (parent instanceof PsiJavaCodeReferenceElement) {
        PsiElement qualified = ((PsiJavaCodeReferenceElement)parent).resolve();
        if (qualified instanceof PsiMember && ((PsiMember)qualified).hasModifierProperty(PsiModifier.STATIC)) {
          message = JavaErrorMessages.message("annotation.not.allowed.static");
        }
      }
    }

    return message != null ? annotationError(annotation, message) : null;
  }

  @Nullable
  private static PsiJavaCodeReferenceElement getOutermostReferenceElement(@Nullable PsiJavaCodeReferenceElement ref) {
    if (ref == null) return null;

    PsiElement qualifier;
    while ((qualifier = ref.getQualifier()) instanceof PsiJavaCodeReferenceElement) {
      ref = (PsiJavaCodeReferenceElement)qualifier;
    }
    return ref;
  }

  @Nullable
  static HighlightInfo checkAnnotationType(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
    if (nameReferenceElement != null) {
      PsiElement resolved = nameReferenceElement.resolve();
      if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) {
        String description = JavaErrorMessages.message("annotation.annotation.type.expected");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameReferenceElement).descriptionAndTooltip(description).create();
      }
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkCyclicMemberType(PsiTypeElement typeElement, PsiClass aClass) {
    PsiType type = typeElement.getType();
    Set<PsiClass> checked = new HashSet<>();
    if (cyclicDependencies(aClass, type, checked, aClass.getManager())) {
      String description = JavaErrorMessages.message("annotation.cyclic.element.type");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
    }
    return null;
  }

  private static boolean cyclicDependencies(PsiClass aClass, PsiType type, @NotNull Set<PsiClass> checked,@NotNull PsiManager manager) {
    final PsiClass resolvedClass = PsiUtil.resolveClassInType(type);
    if (resolvedClass != null && resolvedClass.isAnnotationType()) {
      if (aClass == resolvedClass) {
        return true;
      }
      if (!checked.add(resolvedClass) || !manager.isInProject(resolvedClass)) return false;
      final PsiMethod[] methods = resolvedClass.getMethods();
      for (PsiMethod method : methods) {
        if (cyclicDependencies(aClass, method.getReturnType(), checked,manager)) return true;
      }
    }
    return false;
  }

  static HighlightInfo checkClashesWithSuperMethods(@NotNull PsiAnnotationMethod psiMethod) {
    final PsiIdentifier nameIdentifier = psiMethod.getNameIdentifier();
    if (nameIdentifier != null) {
      final PsiMethod[] methods = psiMethod.findDeepestSuperMethods();
      for (PsiMethod method : methods) {
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          final String qualifiedName = containingClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName) || CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION.equals(qualifiedName)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameIdentifier).descriptionAndTooltip(
              "@interface member clashes with '" + JavaHighlightUtil.formatMethod(method) + "' in " + HighlightUtil.formatClass(containingClass)).create();
          }
        }
      }
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkAnnotationDeclaration(final PsiElement parent, final PsiReferenceList list) {
    if (PsiUtil.isAnnotationMethod(parent)) {
      PsiAnnotationMethod method = (PsiAnnotationMethod)parent;
      if (list == method.getThrowsList()) {
        String description = JavaErrorMessages.message("annotation.members.may.not.have.throws.list");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
      }
    }
    else if (parent instanceof PsiClass && ((PsiClass)parent).isAnnotationType()) {
      if (PsiKeyword.EXTENDS.equals(list.getFirstChild().getText())) {
        String description = JavaErrorMessages.message("annotation.may.not.have.extends.list");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list).descriptionAndTooltip(description).create();
      }
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkPackageAnnotationContainingFile(PsiPackageStatement statement, PsiFile file) {
    PsiModifierList annotationList = statement.getAnnotationList();
    if (annotationList != null && !PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
      String message = JavaErrorMessages.message("invalid.package.annotation.containing.file");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(annotationList.getTextRange()).descriptionAndTooltip(message).create();
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkTargetAnnotationDuplicates(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;

    PsiElement resolved = nameRef.resolve();
    if (!(resolved instanceof PsiClass) || !CommonClassNames.JAVA_LANG_ANNOTATION_TARGET.equals(((PsiClass)resolved).getQualifiedName())) {
      return null;
    }

    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length < 1) return null;
    PsiAnnotationMemberValue value = attributes[0].getValue();
    if (!(value instanceof PsiArrayInitializerMemberValue)) return null;
    PsiAnnotationMemberValue[] arrayInitializers = ((PsiArrayInitializerMemberValue) value).getInitializers();
    Set<PsiElement> targets = new HashSet<>();
    for (PsiAnnotationMemberValue initializer : arrayInitializers) {
      if (initializer instanceof PsiReferenceExpression) {
        PsiElement target = ((PsiReferenceExpression) initializer).resolve();
        if (target != null) {
          if (targets.contains(target)) {
            String description = JavaErrorMessages.message("repeated.annotation.target");
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(initializer).descriptionAndTooltip(description).create();
          }
          targets.add(target);
        }
      }
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkFunctionalInterface(@NotNull PsiAnnotation annotation, @NotNull LanguageLevel languageLevel) {
    if (languageLevel.isAtLeast(LanguageLevel.JDK_1_8) && Comparing.strEqual(annotation.getQualifiedName(), CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE)) {
      final PsiAnnotationOwner owner = annotation.getOwner();
      if (owner instanceof PsiModifierList) {
        final PsiElement parent = ((PsiModifierList)owner).getParent();
        if (parent instanceof PsiClass) {
          final String errorMessage = LambdaHighlightingUtil.checkInterfaceFunctional((PsiClass)parent, ((PsiClass)parent).getName() + " is not a functional interface");
          if (errorMessage != null) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(annotation).descriptionAndTooltip(errorMessage).create();
          }
        }
      }
    }
    return null;
  }

  @Nullable
  static HighlightInfo checkRepeatableAnnotation(PsiAnnotation annotation) {
    String qualifiedName = annotation.getQualifiedName();
    if (!CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE.equals(qualifiedName)) return null;

    String description = doCheckRepeatableAnnotation(annotation);
    if (description != null) {
      PsiAnnotationMemberValue containerRef = PsiImplUtil.findAttributeValue(annotation, null);
      if (containerRef != null) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(containerRef).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  @Nullable
  private static String doCheckRepeatableAnnotation(@NotNull PsiAnnotation annotation) {
    PsiAnnotationOwner owner = annotation.getOwner();
    if (!(owner instanceof PsiModifierList)) return null;
    PsiElement target = ((PsiModifierList)owner).getParent();
    if (!(target instanceof PsiClass) || !((PsiClass)target).isAnnotationType()) return null;
    PsiClass container = getRepeatableContainer(annotation);
    if (container == null) return null;

    PsiMethod[] methods = container.findMethodsByName("value", false);
    if (methods.length == 0) {
      return JavaErrorMessages.message("annotation.container.no.value", container.getQualifiedName());
    }

    if (methods.length == 1) {
      PsiType expected = new PsiImmediateClassType((PsiClass)target, PsiSubstitutor.EMPTY).createArrayType();
      if (!expected.equals(methods[0].getReturnType())) {
        return JavaErrorMessages.message("annotation.container.bad.type", container.getQualifiedName(), JavaHighlightUtil.formatType(expected));
      }
    }

    RetentionPolicy targetPolicy = getRetentionPolicy((PsiClass)target);
    if (targetPolicy != null) {
      RetentionPolicy containerPolicy = getRetentionPolicy(container);
      if (containerPolicy != null && targetPolicy.compareTo(containerPolicy) > 0) {
        return JavaErrorMessages.message("annotation.container.low.retention", container.getQualifiedName(), containerPolicy);
      }
    }

    Set<PsiAnnotation.TargetType> repeatableTargets = AnnotationTargetUtil.getAnnotationTargets((PsiClass)target);
    if (repeatableTargets != null) {
      Set<PsiAnnotation.TargetType> containerTargets = AnnotationTargetUtil.getAnnotationTargets(container);
      if (containerTargets != null && !repeatableTargets.containsAll(containerTargets)) {
        return JavaErrorMessages.message("annotation.container.wide.target", container.getQualifiedName());
      }
    }

    return null;
  }

  @Nullable
  private static PsiClass getRepeatableContainer(@NotNull PsiAnnotation annotation) {
    PsiAnnotationMemberValue containerRef = PsiImplUtil.findAttributeValue(annotation, null);
    if (!(containerRef instanceof PsiClassObjectAccessExpression)) return null;
    PsiType containerType = ((PsiClassObjectAccessExpression)containerRef).getOperand().getType();
    if (!(containerType instanceof PsiClassType)) return null;
    PsiClass container = ((PsiClassType)containerType).resolve();
    if (container == null || !container.isAnnotationType()) return null;
    return container;
  }

  @Nullable
  static HighlightInfo checkReceiverPlacement(PsiReceiverParameter parameter) {
    PsiElement owner = parameter.getParent().getParent();
    if (owner == null) return null;

    if (!(owner instanceof PsiMethod)) {
      String text = JavaErrorMessages.message("receiver.wrong.context");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter.getIdentifier()).descriptionAndTooltip(text).create();
    }

    PsiMethod method = (PsiMethod)owner;
    if (isStatic(method) || method.isConstructor() && isStatic(method.getContainingClass())) {
      String text = JavaErrorMessages.message("receiver.static.context");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter.getIdentifier()).descriptionAndTooltip(text).create();
    }

    PsiElement leftNeighbour = PsiTreeUtil.skipWhitespacesBackward(parameter);
    if (leftNeighbour != null && !PsiUtil.isJavaToken(leftNeighbour, JavaTokenType.LPARENTH)) {
      String text = JavaErrorMessages.message("receiver.wrong.position");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter.getIdentifier()).descriptionAndTooltip(text).create();
    }

    return null;
  }

  @Nullable
  static HighlightInfo checkReceiverType(PsiReceiverParameter parameter) {
    PsiElement owner = parameter.getParent().getParent();
    if (!(owner instanceof PsiMethod)) return null;

    PsiMethod method = (PsiMethod)owner;
    PsiClass enclosingClass = method.getContainingClass();
    if (method.isConstructor() && enclosingClass != null) {
      enclosingClass = enclosingClass.getContainingClass();
    }

    if (enclosingClass != null) {
      PsiClassType type = PsiElementFactory.SERVICE.getInstance(parameter.getProject()).createType(enclosingClass, PsiSubstitutor.EMPTY);
      if (!type.equals(parameter.getType())) {
        PsiElement range = ObjectUtils.notNull(parameter.getTypeElement(), parameter);
        String text = JavaErrorMessages.message("receiver.type.mismatch");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(text).create();
      }

      PsiThisExpression identifier = parameter.getIdentifier();
      if (!enclosingClass.equals(PsiUtil.resolveClassInType(identifier.getType()))) {
        String text = JavaErrorMessages.message("receiver.name.mismatch");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(text).create();
      }
    }

    return null;
  }

  private static boolean isStatic(PsiModifierListOwner owner) {
    if (owner == null) return false;
    if (owner instanceof PsiClass && ClassUtil.isTopLevelClass((PsiClass)owner)) return true;
    PsiModifierList modifierList = owner.getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC);
  }

  @Nullable
  public static RetentionPolicy getRetentionPolicy(@NotNull PsiClass annotation) {
    PsiModifierList modifierList = annotation.getModifierList();
    if (modifierList != null) {
      PsiAnnotation retentionAnno = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION);
      if (retentionAnno == null) return RetentionPolicy.CLASS;

      PsiAnnotationMemberValue policyRef = PsiImplUtil.findAttributeValue(retentionAnno, null);
      if (policyRef instanceof PsiReference) {
        PsiElement field = ((PsiReference)policyRef).resolve();
        if (field instanceof PsiEnumConstant) {
          String name = ((PsiEnumConstant)field).getName();
          try {
            //noinspection ConstantConditions
            return Enum.valueOf(RetentionPolicy.class, name);
          }
          catch (Exception e) {
            LOG.warn("Unknown policy: " + name);
          }
        }
      }
    }

    return null;
  }

  public static class AnnotationReturnTypeVisitor extends PsiTypeVisitor<Boolean> {
    public static final AnnotationReturnTypeVisitor INSTANCE = new AnnotationReturnTypeVisitor();
    @Override
    public Boolean visitType(PsiType type) {
      return Boolean.FALSE;
    }

    @Override
    public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return PsiType.VOID.equals(primitiveType) || PsiType.NULL.equals(primitiveType) ? Boolean.FALSE : Boolean.TRUE;
    }

    @Override
    public Boolean visitArrayType(PsiArrayType arrayType) {
      if (arrayType.getArrayDimensions() != 1) return Boolean.FALSE;
      PsiType componentType = arrayType.getComponentType();
      return componentType.accept(this);
    }

    @Override
    public Boolean visitClassType(PsiClassType classType) {
      if (classType.getParameters().length > 0) {
        PsiClassType rawType = classType.rawType();
        return rawType.equalsToText(CommonClassNames.JAVA_LANG_CLASS);
      }

      PsiClass aClass = classType.resolve();
      if (aClass != null && (aClass.isAnnotationType() || aClass.isEnum())) {
        return Boolean.TRUE;
      }

      return classType.equalsToText(CommonClassNames.JAVA_LANG_CLASS) || classType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    }
  }

  private static class DeleteAnnotationAction implements IntentionAction {
    private final PsiAnnotation myAnnotation;

    private DeleteAnnotationAction(PsiAnnotation annotation) {
      myAnnotation = annotation;
    }

    @NotNull
    @Override
    public String getText() {
      return "Remove";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
      return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      myAnnotation.delete();
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
}
