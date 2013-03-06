/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateAnnotationMethodFromUsageFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public static HighlightInfo checkNameValuePair(PsiNameValuePair pair) {
    PsiReference ref = pair.getReference();
    if (ref == null) return null;
    PsiMethod method = (PsiMethod)ref.resolve();
    if (method == null) {
      if (pair.getName() != null) {
        final String description = JavaErrorMessages.message("annotation.unknown.method", ref.getCanonicalText());
        PsiElement element = ref.getElement();
        final HighlightInfo highlightInfo =
          HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF).range(element).descriptionAndTooltip(description).create();
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateAnnotationMethodFromUsageFix(pair));
        return highlightInfo;
      }
      else {
        String description = JavaErrorMessages.message("annotation.missing.method", ref.getCanonicalText());
        PsiElement element = ref.getElement();
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
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
  public static HighlightInfo checkMemberValueType(@Nullable PsiAnnotationMemberValue value, PsiType expectedType) {
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

      String description = JavaErrorMessages.message("annotation.incompatible.types",
                                                     formatReference(nameRef), HighlightUtil.formatType(expectedType));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description).create();
    }
    if (value instanceof PsiArrayInitializerMemberValue) {
      if (expectedType instanceof PsiArrayType) return null;
      String description = JavaErrorMessages.message("annotation.illegal.array.initializer", HighlightUtil.formatType(expectedType));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description).create();
    }
    if (value instanceof PsiExpression) {
      PsiExpression expr = (PsiExpression)value;
      PsiType type = expr.getType();
      if (type != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expr) ||
          expectedType instanceof PsiArrayType &&
          TypeConversionUtil.areTypesAssignmentCompatible(((PsiArrayType)expectedType).getComponentType(), expr)) {
        return null;
      }

      String description = JavaErrorMessages.message("annotation.incompatible.types",
                                                     HighlightUtil.formatType(type), HighlightUtil.formatType(expectedType));
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(value).descriptionAndTooltip(description).create();
    }

    LOG.error("Unknown annotation member value: " + value);
    return null;
  }

  @Nullable
  public static HighlightInfo checkDuplicateAnnotations(PsiAnnotation annotationToCheck) {
    PsiAnnotationOwner owner = annotationToCheck.getOwner();
    if (owner == null) return null;

    PsiJavaCodeReferenceElement element = annotationToCheck.getNameReferenceElement();
    if (element == null) return null;
    PsiElement resolved = element.resolve();
    if (!(resolved instanceof PsiClass)) return null;

    for (PsiAnnotation annotation : owner.getAnnotations()) {
      if (annotation == annotationToCheck) continue;
      PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef == null) continue;
      PsiElement aClass = nameRef.resolve();
      if (resolved.equals(aClass)) {
        String description = JavaErrorMessages.message("annotation.duplicate.annotation");
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkMissingAttributes(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;
    PsiClass aClass = (PsiClass)nameRef.resolve();
    if (aClass != null && aClass.isAnnotationType()) {
      Set<String> names = new HashSet<String>();
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
      List<String> missed = new ArrayList<String>();
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
        return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(nameRef).descriptionAndTooltip(description).create();
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkConstantExpression(PsiExpression expression) {
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
  public static HighlightInfo checkValidAnnotationType(final PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (type.accept(AnnotationReturnTypeVisitor.INSTANCE).booleanValue()) {
      return null;
    }
    String description = JavaErrorMessages.message("annotation.invalid.annotation.member.type");
    return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
  }

  private static final ElementPattern<PsiElement> ANY_ANNOTATION_ALLOWED = psiElement().andOr(
    psiElement().withParent(PsiNameValuePair.class),
    psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiNameValuePair.class),
    psiElement().withParent(PsiAnnotationMethod.class).afterLeaf(PsiKeyword.DEFAULT)
  );

  @Nullable
  public static HighlightInfo checkApplicability(@NotNull PsiAnnotation annotation) {
    if (ANY_ANNOTATION_ALLOWED.accepts(annotation)) {
      return null;
    }

    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;
    PsiElement annotationType = nameRef.resolve();
    if (!(annotationType instanceof PsiClass)) return null;

    PsiAnnotationOwner owner = annotation.getOwner();
    PsiAnnotation.TargetType[] targets = PsiImplUtil.getApplicableTargets(owner);
    if (owner == null || targets == null) {
      String message = JavaErrorMessages.message("annotation.not.allowed.here");
      return annotationError(annotation, message);
    }

    if (!(owner instanceof PsiModifierList)) {
      HighlightInfo info = HighlightUtil.checkTypeAnnotationFeature(annotation);
      if (info != null) return info;
    }

    PsiAnnotation.TargetType applicable = PsiImplUtil.findApplicableTarget((PsiClass)annotationType, targets);
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
      else if (owner instanceof PsiModifierList) {
        PsiElement nextElement = PsiTreeUtil.skipSiblingsForward((PsiModifierList)owner,
                                                                 PsiComment.class, PsiWhiteSpace.class, PsiTypeParameterList.class);
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
        }
      }
      else if (owner instanceof PsiTypeElement) {
        PsiElement context = PsiTreeUtil.skipParentsOfType((PsiTypeElement)owner, PsiTypeElement.class);
        if (context instanceof PsiClassObjectAccessExpression) {
          String message = JavaErrorMessages.message("annotation.not.allowed.class");
          return annotationError(annotation, message);
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

  public static HighlightInfo checkForeignInnerClassesUsed(final PsiAnnotation annotation) {
    final HighlightInfo[] infos = new HighlightInfo[1];
    final PsiAnnotationOwner owner = annotation.getOwner();
    if (owner instanceof PsiModifierList) {
      final PsiElement parent = ((PsiModifierList)owner).getParent();
      if (parent instanceof PsiClass) {
        annotation.accept(new JavaRecursiveElementWalkingVisitor() {
          @Override
          public void visitElement(PsiElement element) {
            if (infos[0] != null) return;
            super.visitElement(element);
          }

          @Override
          public void visitReferenceExpression(PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);
            final PsiElement resolve = expression.resolve();
            if (resolve instanceof PsiField &&
                ((PsiMember)resolve).hasModifierProperty(PsiModifier.PRIVATE) &&
                PsiTreeUtil.isAncestor(parent, resolve, true)) {
              String description = JavaErrorMessages.message("private.symbol",
                                                             HighlightUtil.formatField((PsiField)resolve),
                                                             HighlightUtil.formatClass((PsiClass)parent));
              HighlightInfo result =
                HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression).descriptionAndTooltip(description).create();
              infos[0] = result;
            }
          }
        });
      }
    }
    return infos[0];
  }

  @Nullable
  public static HighlightInfo checkAnnotationType(PsiAnnotation annotation) {
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
  public static HighlightInfo checkCyclicMemberType(PsiTypeElement typeElement, PsiClass aClass) {
    LOG.assertTrue(aClass.isAnnotationType());
    PsiType type = typeElement.getType();
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) {
      String description = JavaErrorMessages.message("annotation.cyclic.element.type");
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(typeElement).descriptionAndTooltip(description).create();
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkAnnotationDeclaration(final PsiElement parent, final PsiReferenceList list) {
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
  public static HighlightInfo checkPackageAnnotationContainingFile(final PsiPackageStatement statement) {
    if (statement.getAnnotationList() == null) {
      return null;
    }
    PsiFile file = statement.getContainingFile();
    if (file != null && !PsiPackage.PACKAGE_INFO_FILE.equals(file.getName())) {
      String description = JavaErrorMessages.message("invalid.package.annotation.containing.file");
      HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR);
      builder.range(statement.getAnnotationList().getTextRange());
      builder.descriptionAndTooltip(description);
      return builder.create();
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkTargetAnnotationDuplicates(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;
    PsiElement resolved = nameRef.resolve();
    if (!(resolved instanceof PsiClass) ||
        !CommonClassNames.TARGET_ANNOTATION_FQ_NAME.equals(((PsiClass) resolved).getQualifiedName())) return null;

    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length < 1) return null;
    PsiAnnotationMemberValue value = attributes[0].getValue();
    if (!(value instanceof PsiArrayInitializerMemberValue)) return null;
    PsiAnnotationMemberValue[] arrayInitializers = ((PsiArrayInitializerMemberValue) value).getInitializers();
    Set<PsiElement> targets = new HashSet<PsiElement>();
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

  public static HighlightInfo checkFunctionalInterface(PsiAnnotation annotation) {
    final String errorMessage = LambdaUtil.checkFunctionalInterface(annotation);
    if (errorMessage != null) {
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(annotation).descriptionAndTooltip(errorMessage).create();
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
        if (rawType.equalsToText("java.lang.Class")) {
          return Boolean.TRUE;
        }
        return Boolean.FALSE;
      }
      PsiClass aClass = classType.resolve();
      if (aClass != null && (aClass.isAnnotationType() || aClass.isEnum())) return Boolean.TRUE;

      return classType.equalsToText("java.lang.Class") || classType.equalsToText("java.lang.String") ? Boolean.TRUE : Boolean.FALSE;
    }
  }

  private static class DeleteAnnotationAction implements IntentionAction {
    private final PsiAnnotation myAnnotation;

    public DeleteAnnotationAction(PsiAnnotation annotation) {
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
      if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
      myAnnotation.delete();
    }

    @Override
    public boolean startInWriteAction() {
      return true;
    }
  }
}
