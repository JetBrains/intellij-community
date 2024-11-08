// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveAnnotationOnStaticMemberQualifyingTypeFix;
import com.intellij.codeInsight.daemon.impl.quickfix.MoveAnnotationToPackageInfoFileFix;
import com.intellij.codeInsight.daemon.impl.quickfix.ReplaceVarWithExplicitTypeFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public final class AnnotationsHighlightUtil {
  private static final Logger LOG = Logger.getInstance(AnnotationsHighlightUtil.class);

  static HighlightInfo.Builder checkNameValuePair(@NotNull PsiNameValuePair pair) {
    if (pair.getFirstChild() instanceof PsiErrorElement) return null;
    PsiIdentifier identifier = pair.getNameIdentifier();
    if (identifier == null && pair.getParent() instanceof PsiAnnotationParameterList list) {
      PsiNameValuePair[] attributes = list.getAttributes();
      if (attributes.length > 1) {
        String message = JavaPsiBundle.message("annotation.name.is.missing");
        HighlightInfo.Builder highlightInfo = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(pair)
          .descriptionAndTooltip(message);
        for (IntentionAction action : QuickFixFactory.getInstance().createAddAnnotationAttributeNameFixes(pair)) {
          highlightInfo.registerFix(action, null, null, null, null);
        }
        return highlightInfo;
      }
    }
    PsiAnnotation annotation = PsiTreeUtil.getParentOfType(pair, PsiAnnotation.class);
    if (annotation == null) return null;
    PsiClass annotationClass = annotation.resolveAnnotationType();
    if (annotationClass == null) return null;
    PsiReference ref = pair.getReference();
    if (ref == null) return null;
    PsiMethod method = (PsiMethod)ref.resolve();
    if (method == null) {
      boolean noName = pair.getName() == null;
      String description = JavaErrorBundle.message("annotation.unknown.method", ref.getCanonicalText());
      HighlightInfo.Builder highlightInfo = HighlightInfo.newHighlightInfo(noName ? HighlightInfoType.ERROR : HighlightInfoType.WRONG_REF)
        .range(ref.getElement())
        .descriptionAndTooltip(description);
      if (noName) {
        for (IntentionAction action : QuickFixFactory.getInstance().createAddAnnotationAttributeNameFixes(pair)) {
          highlightInfo.registerFix(action, null, null, null, null);
        }
      }
      highlightInfo.registerFix(QuickFixFactory.getInstance().createCreateAnnotationMethodFromUsageFix(pair), null, null, null, null);
      return highlightInfo;
    }
    else {
      PsiType returnType = method.getReturnType();
      assert returnType != null : method;
      PsiAnnotationMemberValue value = pair.getValue();
      if (value != null) {
        HighlightInfo.Builder info = checkMemberValueType(value, returnType, method);
        if (info != null) return info;
      }

      return checkDuplicateAttribute(pair);
    }
  }

  private static HighlightInfo.Builder checkDuplicateAttribute(@NotNull PsiNameValuePair pair) {
    PsiAnnotationParameterList annotation = (PsiAnnotationParameterList)pair.getParent();
    PsiNameValuePair[] attributes = annotation.getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      if (attribute == pair) break;
      String name = pair.getName();
      if (Objects.equals(attribute.getName(), name)) {
        String description = JavaErrorBundle.message("annotation.duplicate.attribute",
                                                     name == null ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME : name);
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(pair).descriptionAndTooltip(description);
        IntentionAction action1 = QuickFixFactory.getInstance().createDeleteFix(pair);
        info.registerFix(action1, null, null, null, null);
        IntentionAction action = QuickFixFactory.getInstance().createMergeDuplicateAttributesFix(pair);
        if (action != null) {
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkMemberValueType(@NotNull PsiAnnotationMemberValue value,
                                            @NotNull PsiType expectedType,
                                            @NotNull PsiMethod method) {
    if (expectedType instanceof PsiClassType && expectedType.equalsToText(CommonClassNames.JAVA_LANG_CLASS)) {
      if (!(value instanceof PsiClassObjectAccessExpression)) {
        String description = JavaErrorBundle.message("annotation.non.class.literal.attribute.value");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description);
      }
    }
    PsiAnnotationMethod annotationMethod = ObjectUtils.tryCast(method, PsiAnnotationMethod.class);
    if (annotationMethod == null || annotationMethod instanceof SyntheticElement) return null;

    boolean fromDefaultValue = PsiTreeUtil.isAncestor(annotationMethod.getDefaultValue(), value, false);
    if (value instanceof PsiAnnotation annotation) {
      PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef == null) return null;

      if (expectedType instanceof PsiClassType classType) {
        PsiClass aClass = classType.resolve();
        if (aClass != null && nameRef.isReferenceTo(aClass)) return null;
      }

      if (expectedType instanceof PsiArrayType arrayType) {
        PsiType componentType = arrayType.getComponentType();
        if (componentType instanceof PsiClassType classType) {
          PsiClass aClass = classType.resolve();
          if (aClass != null && nameRef.isReferenceTo(aClass)) return null;
        }
      }

      String description = JavaErrorBundle.message("incompatible.types", JavaHighlightUtil.formatType(expectedType),
                                                   nameRef.getCanonicalText());
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description);
      PsiClass annotationClass = annotation.resolveAnnotationType();
      if (annotationClass != null) {
        IntentionAction annotationMethodReturnFix =
          QuickFixFactory.getInstance()
            .createAnnotationMethodReturnFix(annotationMethod, TypeUtils.getType(annotationClass), fromDefaultValue);
        info.registerFix(annotationMethodReturnFix, null, null, null, null);
      }
      return info;
    }

    if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
      if (expectedType instanceof PsiArrayType) return null;
      String description = JavaErrorBundle.message("annotation.illegal.array.initializer", JavaHighlightUtil.formatType(expectedType));
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description);
      PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
      if (initializers.length == 0) {
        PsiType arrayType = PsiTypesUtil.createArrayType(expectedType, 1);
        IntentionAction annotationMethodReturnFix =
          QuickFixFactory.getInstance().createAnnotationMethodReturnFix(method, arrayType, fromDefaultValue);
        info.registerFix(annotationMethodReturnFix, null, null, null, null);
      }
      PsiExpression firstInitializer = ObjectUtils.tryCast(ArrayUtil.getFirstElement(initializers), PsiExpression.class);
      if (firstInitializer == null || firstInitializer.getType() == null) return info;
      if (initializers.length == 1 &&
          TypeConversionUtil.areTypesAssignmentCompatible(expectedType, firstInitializer)) {
        IntentionAction action = QuickFixFactory.getInstance().createUnwrapArrayInitializerMemberValueAction(arrayValue);
        if (action != null) {
          info.registerFix(action, null, null, null, null);
        }
      }
      PsiType arrayType = PsiTypesUtil.createArrayType(firstInitializer.getType(), 1);
      IntentionAction annotationMethodReturnFix =
        QuickFixFactory.getInstance().createAnnotationMethodReturnFix(method, arrayType, fromDefaultValue);
      info.registerFix(annotationMethodReturnFix, null, null, null, null);
      return info;
    }

    if (value instanceof PsiExpression expr) {
      PsiType type = expr.getType();

      PsiClass psiClass = PsiUtil.resolveClassInType(type);
      if (psiClass != null &&
          psiClass.isEnum() &&
          !(expr instanceof PsiReferenceExpression referenceExpression && referenceExpression.resolve() instanceof PsiEnumConstant)) {
        String description = JavaErrorBundle.message("annotation.non.enum.constant.attribute.value");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description);
      }

      if (type != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expr) ||
          expectedType instanceof PsiArrayType arrayType &&
          TypeConversionUtil.areTypesAssignmentCompatible(arrayType.getComponentType(), expr)) {
        return null;
      }

      String description = JavaErrorBundle
        .message("incompatible.types", JavaHighlightUtil.formatType(expectedType), JavaHighlightUtil.formatType(type));
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description);
      IntentionAction action1 = QuickFixFactory.getInstance().createSurroundWithQuotesAnnotationParameterValueFix(value, expectedType);
      info.registerFix(action1, null, null, null, null);
      if (type != null) {
        IntentionAction action = QuickFixFactory.getInstance().createAnnotationMethodReturnFix(method, type, fromDefaultValue);
        info.registerFix(action, null, null, null, null);
      }
      return info;
    }

    LOG.error("Unknown annotation member value: " + value);
    return null;
  }

  static HighlightInfo.Builder checkDuplicateAnnotations(@NotNull PsiAnnotation annotationToCheck, @NotNull LanguageLevel languageLevel) {
    PsiAnnotationOwner owner = annotationToCheck.getOwner();
    if (owner == null) return null;

    PsiJavaCodeReferenceElement element = annotationToCheck.getNameReferenceElement();
    if (element == null) return null;
    PsiElement resolved = element.resolve();
    if (!(resolved instanceof PsiClass annotationType)) return null;

    PsiClass contained = contained(annotationType);
    String containedElementFQN = contained == null ? null : contained.getQualifiedName();

    if (containedElementFQN != null) {
      String containerName = annotationType.getQualifiedName();
      if (isAnnotationRepeatedTwice(owner, containedElementFQN)) {
        String description = JavaErrorBundle.message("annotation.container.wrong.place", containerName);
        return createAnnotationError(annotationToCheck, description);
      }
    }
    if (isAnnotationRepeatedTwice(owner, annotationType.getQualifiedName())) {
      if (!languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
        String description = JavaErrorBundle.message("annotation.duplicate.annotation");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
      }

      PsiAnnotation metaAnno = PsiImplUtil.findAnnotation(annotationType.getModifierList(), CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE);
      if (metaAnno == null) {
        String explanation = JavaErrorBundle.message("annotation.non.repeatable", annotationType.getQualifiedName());
        String description = JavaErrorBundle.message("annotation.duplicate.explained", explanation);
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
        IntentionAction action = QuickFixFactory.getInstance().createCollapseAnnotationsFix(annotationToCheck);
        if (action != null) {
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }

      String explanation = doCheckRepeatableAnnotation(metaAnno);
      if (explanation != null) {
        String description = JavaErrorBundle.message("annotation.duplicate.explained", explanation);
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description);
      }

      PsiClass container = getRepeatableContainer(metaAnno);
      if (container != null) {
        PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(owner);
        PsiAnnotation.TargetType applicable = AnnotationTargetUtil.findAnnotationTarget(container, targets);
        if (applicable == null) {
          String target = JavaAnalysisBundle.message("annotation.target." + targets[0]);
          String message = JavaErrorBundle.message("annotation.container.not.applicable", container.getName(), target);
          return createAnnotationError(annotationToCheck, message);
        }
      }
    }

    return null;
  }

  // returns contained element
  private static PsiClass contained(@NotNull PsiClass annotationType) {
    if (!annotationType.isAnnotationType()) return null;
    PsiMethod[] values = annotationType.findMethodsByName("value", false);
    if (values.length != 1) return null;
    PsiMethod value = values[0];
    PsiType returnType = value.getReturnType();
    if (!(returnType instanceof PsiArrayType arrayType)) return null;
    PsiType type = arrayType.getComponentType();
    if (!(type instanceof PsiClassType classType)) return null;
    PsiClass contained = classType.resolve();
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
      if (!(resolved instanceof PsiClass psiClass) || !Objects.equals(qualifiedName, psiClass.getQualifiedName())) continue;
      if (++count == 2) return true;
    }
    return false;
  }

  static HighlightInfo.Builder checkMissingAttributes(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;
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
        StringBuffer buff = new StringBuffer("'" + missed.get(0) + "'");
        for (int i = 1; i < missed.size(); i++) {
          buff.append(", ");
          buff.append("'").append(missed.get(i)).append("'");
        }

        String description = JavaErrorBundle.message("annotation.missing.attribute", buff);
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameRef).descriptionAndTooltip(description);
        IntentionAction fix = QuickFixFactory.getInstance().createAddMissingRequiredAnnotationParametersFix(
          annotation, annotationMethods, missed);
        info.registerFix(fix, null, null, null, null);
        return info;
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkConstantExpression(@NotNull PsiExpression expression) {
    PsiElement parent = expression.getParent();
    if (PsiUtil.isAnnotationMethod(parent) || parent instanceof PsiNameValuePair || parent instanceof PsiArrayInitializerMemberValue) {
      if (!PsiUtil.isConstantExpression(expression)) {
        if (IncompleteModelUtil.isIncompleteModel(expression) && 
            IncompleteModelUtil.mayHaveUnknownTypeDueToPendingReference(expression)) {
          return null;
        }
        String description = JavaErrorBundle.message("annotation.non.constant.attribute.value");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description);
      }
    }

    return null;
  }

  static HighlightInfo.Builder checkValidAnnotationType(@Nullable PsiType type, @NotNull PsiTypeElement typeElement) {
    if (type != null && type.accept(AnnotationReturnTypeVisitor.INSTANCE).booleanValue()) {
      return null;
    }
    String description = JavaErrorBundle
      .message("annotation.invalid.annotation.member.type", type != null ? type.getPresentableText() : null);
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
  }

  private static final ElementPattern<PsiElement> ANY_ANNOTATION_ALLOWED = psiElement().andOr(
    psiElement().withParent(PsiNameValuePair.class),
    psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiNameValuePair.class),
    psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiAnnotationMethod.class),
    psiElement().withParent(PsiAnnotationMethod.class).afterLeaf(PsiKeyword.DEFAULT),
    // Unterminated parameter list like "void test(@NotNull String)": error on annotation looks annoying here
    psiElement().withParents(PsiModifierList.class, PsiParameterList.class)
  );

  public static HighlightInfo.Builder checkApplicability(@NotNull PsiAnnotation annotation, @NotNull LanguageLevel level, @NotNull PsiFile file) {
    if (ANY_ANNOTATION_ALLOWED.accepts(annotation)) {
      return null;
    }

    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;

    PsiAnnotationOwner owner = annotation.getOwner();
    PsiAnnotation.TargetType[] targets = AnnotationTargetUtil.getTargetsForLocation(owner);
    if (owner == null || targets.length == 0) {
      String message = JavaErrorBundle.message("annotation.not.allowed.here");
      return createAnnotationError(annotation, message);
    }

    if (!(owner instanceof PsiModifierList)) {
      HighlightInfo.Builder info = HighlightUtil.checkFeature(annotation, JavaFeature.TYPE_ANNOTATIONS, level, file);
      if (info != null) return info;
    }

    PsiAnnotation.TargetType applicable = AnnotationTargetUtil.findAnnotationTarget(annotation, targets);
    if (applicable == PsiAnnotation.TargetType.UNKNOWN) return null;

    if (applicable == null) {
      if (targets.length == 1 && targets[0] == PsiAnnotation.TargetType.TYPE_USE) {
        PsiElement parent = annotation.getParent();
        if (parent instanceof PsiTypeElement) {
          PsiElement modifierList = PsiTreeUtil.skipSiblingsBackward(parent, PsiWhiteSpace.class, PsiComment.class, PsiTypeParameterList.class);
          if (modifierList instanceof PsiModifierList psiModifierList) {
            targets = AnnotationTargetUtil.getTargetsForLocation(psiModifierList);
            if (AnnotationTargetUtil.findAnnotationTarget(annotation, targets) == null) {
              return createNotApplicableTargetInfo(annotation, nameRef, targets);
            }
            return null;
          }
        }
      }
      return createNotApplicableTargetInfo(annotation, nameRef, targets);
    }


    if (applicable == PsiAnnotation.TargetType.TYPE_USE) {
      if (owner instanceof PsiClassReferenceType referenceType) {
        PsiJavaCodeReferenceElement ref = referenceType.getReference();
        HighlightInfo.Builder info = checkReferenceTarget(annotation, ref);
        if (info != null) return info;
      }
      else if (owner instanceof PsiModifierList || owner instanceof PsiTypeElement) {
        PsiElement nextElement = owner instanceof PsiTypeElement typeElementOwner
            ? typeElementOwner
            : PsiTreeUtil.skipSiblingsForward((PsiModifierList)owner, PsiComment.class, PsiWhiteSpace.class, PsiTypeParameterList.class);
        if (nextElement instanceof PsiTypeElement typeElement) {
          PsiType type = typeElement.getType();
          //see JLS 9.7.4 Where Annotations May Appear
          if (PsiTypes.voidType().equals(type)) {
            String message = JavaErrorBundle.message("annotation.not.allowed.void");
            return createAnnotationError(annotation, message);
          }
          if (typeElement.isInferredType()) {
            HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(annotation)
              .descriptionAndTooltip(JavaErrorBundle.message("annotation.not.allowed.var"));
            IntentionAction action1 =
              QuickFixFactory.getInstance().createDeleteFix(annotation, JavaAnalysisBundle.message("intention.text.remove.annotation"));
            info.registerFix(action1, null, null, null, null);
            ModCommandAction action = new ReplaceVarWithExplicitTypeFix(typeElement);
            info.registerFix(action, null, null, null, null);
            return info;
          }
          if (!(type instanceof PsiPrimitiveType || type instanceof PsiArrayType)) {
            PsiJavaCodeReferenceElement ref = getOutermostReferenceElement(typeElement.getInnermostComponentReferenceElement());
            HighlightInfo.Builder info = checkReferenceTarget(annotation, ref);
            if (info != null) return info;
          }
        }
      }
      if (PsiTreeUtil.skipParentsOfType(annotation, PsiTypeElement.class) instanceof PsiClassObjectAccessExpression) {
        String message = JavaErrorBundle.message("annotation.not.allowed.class");
        return createAnnotationError(annotation, message);
      }
    }

    return null;
  }

  @NotNull
  private static HighlightInfo.Builder createNotApplicableTargetInfo(@NotNull PsiAnnotation annotation,
                                                                     PsiJavaCodeReferenceElement nameRef,
                                                                     PsiAnnotation.TargetType[] targets) {
    String target = JavaAnalysisBundle.message("annotation.target." + targets[0]);
    String message = JavaErrorBundle.message("annotation.not.applicable", nameRef.getText(), target);
    HighlightInfo.Builder info = createAnnotationError(annotation, message);
    if (BaseIntentionAction.canModify(Objects.requireNonNull(annotation.resolveAnnotationType()))) {
      for (PsiAnnotation.TargetType targetType : targets) {
        IntentionAction action = QuickFixFactory.getInstance().createAddAnnotationTargetFix(annotation, targetType);
        info.registerFix(action, null, null, null, null);
      }
    }
    return info;
  }

  @NotNull
  private static HighlightInfo.Builder createAnnotationError(@NotNull PsiAnnotation annotation, @NotNull @NlsContexts.DetailedDescription String message) {
    LocalQuickFixAndIntentionActionOnPsiElement fix = QuickFixFactory.getInstance()
      .createDeleteFix(annotation, JavaAnalysisBundle.message("intention.text.remove.annotation"));
    return createAnnotationError(annotation, message, fix);
  }

  @NotNull
  private static HighlightInfo.Builder createAnnotationError(@NotNull PsiAnnotation annotation,
                                                             @NotNull @NlsContexts.DetailedDescription String message,
                                                             @NotNull IntentionAction fix) {
    HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
      .range(annotation)
      .descriptionAndTooltip(message);
    info.registerFix(fix, null, null, null, null);
    return info;
  }

  @Nullable
  private static HighlightInfo.Builder checkReferenceTarget(@NotNull PsiAnnotation annotation, @Nullable PsiJavaCodeReferenceElement ref) {
    if (ref == null) return null;
    PsiElement refTarget = ref.resolve();
    if (refTarget == null) return null;

    if (!(refTarget instanceof PsiClass)) {
      return createAnnotationError(annotation, JavaErrorBundle.message("annotation.not.allowed.ref"));
    }

    PsiElement parent = ref.getParent();
    while (parent instanceof PsiJavaCodeReferenceElement referenceElement) {
      PsiElement qualified = referenceElement.resolve();
      if (qualified instanceof PsiMember member && member.hasModifierProperty(PsiModifier.STATIC)) {
        return createAnnotationError(annotation,
                                     JavaErrorBundle.message("annotation.not.allowed.static"),
                                     new MoveAnnotationOnStaticMemberQualifyingTypeFix(annotation).asIntention());
      }
      if (qualified instanceof PsiClass) {
        parent = parent.getParent();
      }
      else {
        break;
      }
    }
    return null;
  }

  @Contract("null->null; !null->!null")
  private static PsiJavaCodeReferenceElement getOutermostReferenceElement(@Nullable PsiJavaCodeReferenceElement ref) {
    if (ref == null) return null;

    while (ref.getQualifier() instanceof PsiJavaCodeReferenceElement referenceElement) {
      ref = referenceElement;
    }
    return ref;
  }

  static HighlightInfo.Builder checkAnnotationType(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
    if (nameReferenceElement != null) {
      PsiElement resolved = nameReferenceElement.resolve();
      if (resolved != null && (!(resolved instanceof PsiClass psiClass) || !psiClass.isAnnotationType())) {
        String description = JavaErrorBundle.message("annotation.annotation.type.expected");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameReferenceElement).descriptionAndTooltip(description);
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkCyclicMemberType(@NotNull PsiTypeElement typeElement, @NotNull PsiClass aClass) {
    PsiType type = typeElement.getType();
    Set<PsiClass> checked = new HashSet<>();
    if (cyclicDependencies(aClass, type, checked)) {
      String description = JavaErrorBundle.message("annotation.cyclic.element.type");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description);
    }
    return null;
  }

  private static boolean cyclicDependencies(@NotNull PsiClass aClass,
                                            @Nullable PsiType type,
                                            @NotNull Set<? super PsiClass> checked) {
    PsiClass resolvedClass = PsiUtil.resolveClassInType(type);
    if (resolvedClass != null && resolvedClass.isAnnotationType()) {
      if (aClass == resolvedClass) {
        return true;
      }
      if (!checked.add(resolvedClass) || !BaseIntentionAction.canModify(resolvedClass)) return false;
      PsiMethod[] methods = resolvedClass.getMethods();
      for (PsiMethod method : methods) {
        if (cyclicDependencies(aClass, method.getReturnType(), checked)) return true;
      }
    }
    return false;
  }

  static HighlightInfo.Builder checkClashesWithSuperMethods(@NotNull PsiAnnotationMethod psiMethod) {
    PsiIdentifier nameIdentifier = psiMethod.getNameIdentifier();
    if (nameIdentifier != null) {
      PsiMethod[] methods = psiMethod.findDeepestSuperMethods();
      for (PsiMethod method : methods) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          String qualifiedName = containingClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(qualifiedName) || CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION.equals(qualifiedName)) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameIdentifier).descriptionAndTooltip(
              JavaErrorBundle.message("error.interface.member.clashes", JavaHighlightUtil.formatMethod(method),
                                      HighlightUtil.formatClass(containingClass)));
          }
        }
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkAnnotationDeclaration(@Nullable PsiElement parent, @NotNull PsiReferenceList list) {
    if (PsiUtil.isAnnotationMethod(parent)) {
      PsiAnnotationMethod method = (PsiAnnotationMethod)parent;
      if (list == method.getThrowsList()) {
        String description = JavaErrorBundle.message("annotation.members.may.not.have.throws.list");
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(list.getFirstChild()).descriptionAndTooltip(description);
        IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(list);
        info.registerFix(action, null, null, null, null);
        return info;
      }
    }
    else if (parent instanceof PsiClass aClass && aClass.isAnnotationType()) {
      PsiElement child = list.getFirstChild();
      if (PsiUtil.isJavaToken(child, JavaTokenType.EXTENDS_KEYWORD)) {
        String description = JavaErrorBundle.message("annotation.may.not.have.extends.list");
        HighlightInfo.Builder info =
          HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(child).descriptionAndTooltip(description);
        IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(list);
        info.registerFix(action, null, null, null, null);
        return info;
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkPackageAnnotationContainingFile(@NotNull PsiPackageStatement statement, @NotNull PsiFile file) {
    PsiModifierList annotationList = statement.getAnnotationList();
    if (annotationList != null && !PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
      String message = JavaErrorBundle.message("invalid.package.annotation.containing.file");
      IntentionAction deleteFix =
        QuickFixFactory.getInstance().createDeleteFix(annotationList, JavaAnalysisBundle.message("intention.text.remove.annotation"));
      HighlightInfo.Builder builder =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(annotationList).descriptionAndTooltip(message);
      var moveAnnotationToPackageInfoFileFix = new MoveAnnotationToPackageInfoFileFix(statement);
      return builder.registerFix(deleteFix, null, null, null, null)
        .registerFix(moveAnnotationToPackageInfoFileFix, null, null, null, null);
    }
    return null;
  }

  static HighlightInfo.Builder checkTargetAnnotationDuplicates(@NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;

    PsiElement resolved = nameRef.resolve();
    if (!(resolved instanceof PsiClass psiClass) || !CommonClassNames.JAVA_LANG_ANNOTATION_TARGET.equals(psiClass.getQualifiedName())) {
      return null;
    }

    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length < 1) return null;
    PsiAnnotationMemberValue value = attributes[0].getValue();
    if (!(value instanceof PsiArrayInitializerMemberValue initializerMemberValue)) return null;
    PsiAnnotationMemberValue[] arrayInitializers = initializerMemberValue.getInitializers();
    Set<PsiElement> targets = new HashSet<>();
    for (PsiAnnotationMemberValue initializer : arrayInitializers) {
      if (initializer instanceof PsiReferenceExpression referenceExpression) {
        PsiElement target = referenceExpression.resolve();
        if (target != null) {
          if (targets.contains(target)) {
            String description = JavaErrorBundle.message("repeated.annotation.target");
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(initializer).descriptionAndTooltip(description);
          }
          targets.add(target);
        }
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkInvalidAnnotationOnRecordComponent(@NotNull PsiAnnotation annotation) {
    if (!Comparing.strEqual(annotation.getQualifiedName(), CommonClassNames.JAVA_LANG_SAFE_VARARGS)) return null;
    PsiAnnotationOwner owner = annotation.getOwner();
    if (!(owner instanceof PsiModifierList list)) return null;
    PsiElement parent = list.getParent();
    if (!(parent instanceof PsiRecordComponent)) return null;
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(annotation)
      .descriptionAndTooltip(JavaErrorBundle.message("safevararg.annotation.cannot.be.applied.for.record.component"));
  }

  static HighlightInfo.Builder checkFunctionalInterface(@NotNull PsiAnnotation annotation, @NotNull LanguageLevel languageLevel) {
    if (JavaFeature.LAMBDA_EXPRESSIONS.isSufficient(languageLevel) && 
        Comparing.strEqual(annotation.getQualifiedName(), CommonClassNames.JAVA_LANG_FUNCTIONAL_INTERFACE)) {
      PsiAnnotationOwner owner = annotation.getOwner();
      if (owner instanceof PsiModifierList list) {
        PsiElement parent = list.getParent();
        if (parent instanceof PsiClass psiClass) {
          String errorMessage = LambdaHighlightingUtil.checkInterfaceFunctional(psiClass, JavaErrorBundle.message("not.a.functional.interface", psiClass.getName()));
          if (errorMessage != null) {
            HighlightInfo.Builder info =
              HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(annotation).descriptionAndTooltip(errorMessage);
            IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(annotation);
            info.registerFix(action, null, null, null, null);
            return info;
          }

          if (psiClass.hasModifierProperty(PsiModifier.SEALED)) {
            HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
              .range(annotation)
              .descriptionAndTooltip(
                JavaErrorBundle.message("functional.interface.must.not.be.sealed.error.description", PsiModifier.SEALED));
            IntentionAction action = QuickFixFactory.getInstance().createDeleteFix(annotation);
            info.registerFix(action, null, null, null, null);
            return info;
          }
        }
      }
    }
    return null;
  }

  static HighlightInfo.Builder checkRepeatableAnnotation(@NotNull PsiAnnotation annotation) {
    String qualifiedName = annotation.getQualifiedName();
    if (!CommonClassNames.JAVA_LANG_ANNOTATION_REPEATABLE.equals(qualifiedName)) return null;

    String description = doCheckRepeatableAnnotation(annotation);
    if (description != null) {
      PsiAnnotationMemberValue containerRef = PsiImplUtil.findAttributeValue(annotation, null);
      if (containerRef != null) {
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(containerRef).descriptionAndTooltip(description);
      }
    }

    return null;
  }

  public static @NlsContexts.DetailedDescription String doCheckRepeatableAnnotation(@NotNull PsiAnnotation annotation) {
    PsiAnnotationOwner owner = annotation.getOwner();
    if (!(owner instanceof PsiModifierList list)) return null;
    PsiElement target = list.getParent();
    if (!(target instanceof PsiClass psiClass) || !psiClass.isAnnotationType()) return null;
    PsiClass container = getRepeatableContainer(annotation);
    if (container == null) return null;

    PsiMethod[] methods = !container.isAnnotationType() ? PsiMethod.EMPTY_ARRAY
                                                        : container.findMethodsByName("value", false);
    if (methods.length == 0) {
      return JavaErrorBundle.message("annotation.container.no.value", container.getQualifiedName());
    }

    if (methods.length == 1) {
      PsiType expected = new PsiImmediateClassType(psiClass, PsiSubstitutor.EMPTY).createArrayType();
      if (!expected.equals(methods[0].getReturnType())) {
        return JavaErrorBundle.message("annotation.container.bad.type", container.getQualifiedName(), JavaHighlightUtil.formatType(expected));
      }
    }

    RetentionPolicy targetPolicy = getRetentionPolicy(psiClass);
    if (targetPolicy != null) {
      RetentionPolicy containerPolicy = getRetentionPolicy(container);
      if (containerPolicy != null && targetPolicy.compareTo(containerPolicy) > 0) {
        return JavaErrorBundle.message("annotation.container.low.retention", container.getQualifiedName(), containerPolicy);
      }
    }

    Set<PsiAnnotation.TargetType> repeatableTargets = AnnotationTargetUtil.getAnnotationTargets(psiClass);
    if (repeatableTargets != null) {
      Set<PsiAnnotation.TargetType> containerTargets = AnnotationTargetUtil.getAnnotationTargets(container);
      if (containerTargets != null) {
        for (PsiAnnotation.TargetType containerTarget : containerTargets) {
          if (repeatableTargets.contains(containerTarget)) {
            continue;
          }
          if (containerTarget == PsiAnnotation.TargetType.ANNOTATION_TYPE &&
              (repeatableTargets.contains(PsiAnnotation.TargetType.TYPE) || repeatableTargets.contains(PsiAnnotation.TargetType.TYPE_USE))) {
            continue;
          }
          if ((containerTarget == PsiAnnotation.TargetType.TYPE || containerTarget == PsiAnnotation.TargetType.TYPE_PARAMETER) &&
              repeatableTargets.contains(PsiAnnotation.TargetType.TYPE_USE)) {
            continue;
          }
          return JavaErrorBundle.message("annotation.container.wide.target", container.getQualifiedName());
        }
      }
    }

    for (PsiMethod method : container.getMethods()) {
      if (method instanceof PsiAnnotationMethod annotationMethod && !"value".equals(method.getName()) && annotationMethod.getDefaultValue() == null) {
        return JavaErrorBundle.message("annotation.container.abstract", container.getQualifiedName(), method.getName());
      }
    }

    @Nullable String missedAnnotationError = getMissedAnnotationError(psiClass, container, Inherited.class.getName());
    if (missedAnnotationError != null) {
      return missedAnnotationError;
    }
    return getMissedAnnotationError(psiClass, container, Documented.class.getName());
  }

  @Nls
  private static String getMissedAnnotationError(PsiClass target, PsiClass container, String annotationFqn) {
    if (AnnotationUtil.isAnnotated(target, annotationFqn, 0) && !AnnotationUtil.isAnnotated(container, annotationFqn, 0)) {
      return JavaErrorBundle.message("annotation.container.missed.annotation", container.getQualifiedName(), StringUtil.getShortName(annotationFqn));
    }
    return null;
  }

  @Nullable
  private static PsiClass getRepeatableContainer(@NotNull PsiAnnotation annotation) {
    PsiAnnotationMemberValue containerRef = PsiImplUtil.findAttributeValue(annotation, null);
    if (!(containerRef instanceof PsiClassObjectAccessExpression expression)) return null;
    PsiType containerType = expression.getOperand().getType();
    if (!(containerType instanceof PsiClassType classType)) return null;
    return classType.resolve();
  }

  static HighlightInfo.Builder checkReceiverPlacement(@NotNull PsiReceiverParameter parameter) {
    PsiElement owner = parameter.getParent().getParent();
    if (owner == null) return null;

    if (!(owner instanceof PsiMethod method)) {
      String text = JavaErrorBundle.message("receiver.wrong.context");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter.getIdentifier()).descriptionAndTooltip(text);
    }

    if (isStatic(method) || method.isConstructor() && isStatic(method.getContainingClass())) {
      String text = JavaErrorBundle.message("receiver.static.context");
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter.getIdentifier()).descriptionAndTooltip(text);
      IntentionAction action1 = QuickFixFactory.getInstance().createDeleteFix(parameter);
      info.registerFix(action1, null, null, null, null);
      IntentionAction action =
        QuickFixFactory.getInstance().createModifierListFix(method.getModifierList(), PsiModifier.STATIC, false, false);
      info.registerFix(action, null, null, null, null);
      return info;
    }

    if (!PsiUtil.isJavaToken(PsiTreeUtil.skipWhitespacesAndCommentsBackward(parameter), JavaTokenType.LPARENTH)) {
      String text = JavaErrorBundle.message("receiver.wrong.position");
      HighlightInfo.Builder info =
        HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(parameter.getIdentifier()).descriptionAndTooltip(text);
      IntentionAction action1 = QuickFixFactory.getInstance().createDeleteFix(parameter);
      info.registerFix(action1, null, null, null, null);
      PsiReceiverParameter firstReceiverParameter = PsiTreeUtil.getChildOfType(method.getParameterList(), PsiReceiverParameter.class);
      if (!PsiUtil.isJavaToken(PsiTreeUtil.skipWhitespacesAndCommentsBackward(firstReceiverParameter), JavaTokenType.LPARENTH)) {
        IntentionAction action = QuickFixFactory.getInstance().createMakeReceiverParameterFirstFix(parameter);
        info.registerFix(action, null, null, null, null);
      }
      return info;
    }

    return null;
  }

  static HighlightInfo.Builder checkReceiverType(@NotNull PsiReceiverParameter parameter) {
    PsiElement owner = parameter.getParent().getParent();
    if (!(owner instanceof PsiMethod method)) return null;

    PsiClass enclosingClass = method.getContainingClass();
    boolean isConstructor = method.isConstructor();
    if (isConstructor && enclosingClass != null) {
      enclosingClass = enclosingClass.getContainingClass();
    }

    if (enclosingClass != null) {
      PsiClassType type = PsiElementFactory.getInstance(parameter.getProject()).createType(enclosingClass, PsiSubstitutor.EMPTY);
      if (!type.equals(parameter.getType())) {
        PsiElement range = ObjectUtils.notNull(parameter.getTypeElement(), parameter);
        String text = JavaErrorBundle.message("receiver.type.mismatch");
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(range).descriptionAndTooltip(text);
        IntentionAction action = QuickFixFactory.getInstance().createReceiverParameterTypeFix(parameter, type);
        info.registerFix(action, null, null, null, null);
        return info;
      }

      PsiThisExpression identifier = parameter.getIdentifier();
      if (!enclosingClass.equals(PsiUtil.resolveClassInType(identifier.getType()))) {
        String text = JavaErrorBundle.message("receiver.name.mismatch");
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(identifier).descriptionAndTooltip(text);
        String name;
        if (isConstructor) {
          String className = enclosingClass.getName();
          name = className != null ? className + ".this" : null;
        }
        else {
          name = "this";
        }
        if (name != null) {
          IntentionAction action = QuickFixFactory.getInstance().createReceiverParameterNameFix(parameter, name);
          info.registerFix(action, null, null, null, null);
        }
        return info;
      }
    }

    return null;
  }

  private static boolean isStatic(@Nullable PsiModifierListOwner owner) {
    if (owner == null) return false;
    if (owner instanceof PsiClass psiClass && ClassUtil.isTopLevelClass(psiClass)) return true;
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
      if (policyRef instanceof PsiReference psiReference) {
        PsiElement field = psiReference.resolve();
        if (field instanceof PsiEnumConstant constant) {
          String name = constant.getName();
          try {
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
    public Boolean visitType(@NotNull PsiType type) {
      return Boolean.FALSE;
    }

    @Override
    public Boolean visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
      return PsiTypes.voidType().equals(primitiveType) || PsiTypes.nullType().equals(primitiveType) ? Boolean.FALSE : Boolean.TRUE;
    }

    @Override
    public Boolean visitArrayType(@NotNull PsiArrayType arrayType) {
      if (arrayType.getArrayDimensions() != 1) return Boolean.FALSE;
      PsiType componentType = arrayType.getComponentType();
      return componentType.accept(this);
    }

    @Override
    public Boolean visitClassType(@NotNull PsiClassType classType) {
      if (classType.getParameters().length > 0) {
        return PsiTypesUtil.classNameEquals(classType, CommonClassNames.JAVA_LANG_CLASS);
      }

      PsiClass aClass = classType.resolve();
      if (aClass != null && (aClass.isAnnotationType() || aClass.isEnum())) {
        return Boolean.TRUE;
      }

      return classType.equalsToText(CommonClassNames.JAVA_LANG_CLASS) || classType.equalsToText(CommonClassNames.JAVA_LANG_STRING);
    }
  }
}
