/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
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
        final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getElement(), description);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateAnnotationMethodFromUsageFix(pair));
        return highlightInfo;
      }
      else {
        String description = JavaErrorMessages.message("annotation.missing.method", ref.getCanonicalText());
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, ref.getElement(), description);
      }
    }
    else {
      PsiType returnType = method.getReturnType();
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
      if (Comparing.equal(attribute.getName(), pair.getName())) {
        String description = JavaErrorMessages.message("annotation.duplicate.attribute",
                                                       pair.getName() == null
                                                       ? PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME
                                                       : pair.getName());
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, pair, description);
      }
    }

    return null;
  }

  private static String formatReference(PsiJavaCodeReferenceElement ref) {
    return ref.getCanonicalText();
  }

  @Nullable
  public static HighlightInfo checkMemberValueType(PsiAnnotationMemberValue value, PsiType expectedType) {
    if (value == null) return null;
    if (value instanceof PsiAnnotation) {
      PsiJavaCodeReferenceElement nameRef = ((PsiAnnotation)value).getNameReferenceElement();
      if (nameRef == null) return null;
      if (expectedType instanceof PsiClassType) {
        PsiClass aClass = ((PsiClassType)expectedType).resolve();
        if (nameRef.isReferenceTo(aClass)) return null;
      }

      if (expectedType instanceof PsiArrayType) {
        PsiType componentType = ((PsiArrayType)expectedType).getComponentType();
        if (componentType instanceof PsiClassType) {
          PsiClass aClass = ((PsiClassType)componentType).resolve();
          if (nameRef.isReferenceTo(aClass)) return null;
        }
      }

      String description = JavaErrorMessages.message("annotation.incompatible.types",
                                                     formatReference(nameRef),
                                                     HighlightUtil.formatType(expectedType));

      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value, description);
    }
    else if (value instanceof PsiArrayInitializerMemberValue) {
      if (expectedType instanceof PsiArrayType) return null;
      String description = JavaErrorMessages.message("annotation.illegal.array.initializer", HighlightUtil.formatType(expectedType));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value, description);
    }
    else if (value instanceof PsiExpression) {
      PsiExpression expr = (PsiExpression)value;
      PsiType type = expr.getType();
      if (type != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expr) ||
          expectedType instanceof PsiArrayType && TypeConversionUtil.areTypesAssignmentCompatible(((PsiArrayType)expectedType).getComponentType(), expr)) {
        return null;
      }

      String description = JavaErrorMessages.message("annotation.incompatible.types",
                                                     HighlightUtil.formatType(type), HighlightUtil.formatType(expectedType));
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, value, description);
    }

    LOG.error("Unknown annotation member value: "+value);
    return null;
  }

  @Nullable
  public static HighlightInfo checkDuplicateAnnotations(PsiAnnotation annotationToCheck) {
    PsiAnnotationOwner owner = annotationToCheck.getOwner();
    if (owner == null) {
      return null;
    }

    PsiJavaCodeReferenceElement element = annotationToCheck.getNameReferenceElement();
    if (element == null) return null;
    PsiElement resolved = element.resolve();
    if (!(resolved instanceof PsiClass)) return null;

    PsiAnnotation[] annotations = owner.getApplicableAnnotations();
    for (PsiAnnotation annotation : annotations) {
      if (annotation == annotationToCheck) continue;
      PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef == null) continue;
      PsiElement aClass = nameRef.resolve();
      if (resolved.equals(aClass)) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, element, JavaErrorMessages.message("annotation.duplicate.annotation"));
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
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, nameRef, description);
      }
    }

    return null;
  }

  @Nullable
  public static HighlightInfo checkConstantExpression(PsiExpression expression) {
    final PsiElement parent = expression.getParent();
    if (PsiUtil.isAnnotationMethod(parent) || parent instanceof PsiNameValuePair || parent instanceof PsiArrayInitializerMemberValue) {
      if (!PsiUtil.isConstantExpression(expression)) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, JavaErrorMessages.message("annotation.non.constant.attribute.value"));
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
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, JavaErrorMessages.message("annotation.invalid.annotation.member.type"));
  }

  private static final ElementPattern<PsiElement> ANNOTATION_ALLOWED = psiElement().andOr(
    psiElement().with(new PatternCondition<PsiElement>("annotationOwner") {
      @Override
      public boolean accepts(@NotNull PsiElement element, ProcessingContext context) {
        return element instanceof PsiAnnotationOwner;
      }
    }),
    psiElement().withParent(PsiNameValuePair.class),
    psiElement().withParents(PsiArrayInitializerMemberValue.class, PsiNameValuePair.class)
  );

  @Nullable
  public static HighlightInfo checkApplicability(final PsiAnnotation annotation) {
    PsiAnnotationOwner owner = annotation.getOwner();
    if (owner instanceof PsiModifierList || owner instanceof PsiTypeElement || owner instanceof PsiMethodReceiver || owner instanceof PsiTypeParameter) {
      PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
      if (nameRef == null) {
        return null;
      }
      PsiElement member = owner instanceof PsiModifierList ? ((PsiElement)owner).getParent() : (PsiElement)owner;
      String[] elementTypeFields = PsiAnnotationImpl.getApplicableElementTypeFields(member);
      if (elementTypeFields == null || PsiAnnotationImpl.isAnnotationApplicableTo(annotation, false, elementTypeFields)) {
        return null;
      }
      String target = JavaErrorMessages.message("annotation.target." + elementTypeFields[0]);
      String description = JavaErrorMessages.message("annotation.not.applicable", nameRef.getText(), target);
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, nameRef, description);
      QuickFixAction.registerQuickFixAction(highlightInfo, new DeleteNotApplicableAnnotationAction(annotation));
      return highlightInfo;
    }

    if (!ANNOTATION_ALLOWED.accepts(annotation)) {
      String message = JavaErrorMessages.message("annotation.not.allowed.here");
      HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, annotation, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new DeleteNotApplicableAnnotationAction(annotation));
      return highlightInfo;
    }

    return null;
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
              infos[0] = HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, description);
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
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, nameReferenceElement, JavaErrorMessages.message("annotation.annotation.type.expected"));
      }
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkCyclicMemberType(PsiTypeElement typeElement, PsiClass aClass) {
    LOG.assertTrue(aClass.isAnnotationType());
    PsiType type = typeElement.getType();
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, JavaErrorMessages.message("annotation.cyclic.element.type"));
    }
    return null;
  }

  @Nullable
  public static HighlightInfo checkAnnotationDeclaration(final PsiElement parent, final PsiReferenceList list) {
    if (PsiUtil.isAnnotationMethod(parent)) {
      PsiAnnotationMethod method = (PsiAnnotationMethod)parent;
      if (list == method.getThrowsList()) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, JavaErrorMessages.message("annotation.members.may.not.have.throws.list"));
      }
    }
    else if (parent instanceof PsiClass && ((PsiClass)parent).isAnnotationType()) {
      if (PsiKeyword.EXTENDS.equals(list.getFirstChild().getText())) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, list, JavaErrorMessages.message("annotation.may.not.have.extends.list"));
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
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               statement.getAnnotationList().getTextRange(),
                                               JavaErrorMessages.message("invalid.package.annotation.containing.file"));

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
            return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               initializer,
                                               JavaErrorMessages.message("repeated.annotation.target"));
          }
          targets.add(target);
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

  private static class DeleteNotApplicableAnnotationAction implements IntentionAction {
    private final PsiAnnotation myAnnotation;

    public DeleteNotApplicableAnnotationAction(PsiAnnotation annotation) {
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
