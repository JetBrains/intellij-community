// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationMethod;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class SimplifiableAnnotationInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    if (infos[0] instanceof PsiWhiteSpace) {
      return InspectionGadgetsBundle.message("simplifiable.annotation.whitespace.problem.descriptor");
    }
    else if (infos[0] instanceof PsiArrayInitializerMemberValue arrayValue) {
      return InspectionGadgetsBundle.message("simplifiable.annotation.braces.problem.descriptor", arrayValue.getText());
    }
    else {
      return InspectionGadgetsBundle.message("simplifiable.annotation.problem.descriptor");
    }
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new SimplifiableAnnotationFix();
  }

  private static class SimplifiableAnnotationFix extends PsiUpdateModCommandQuickFix {

    SimplifiableAnnotationFix() {}

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("simplifiable.annotation.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(element, PsiAnnotation.class, false);
      if (annotation == null) {
        return;
      }
      final CommentTracker tracker = new CommentTracker();
      final String annotationText = buildAnnotationText(annotation, tracker);
      final PsiAnnotation newAnnotation = JavaPsiFacade.getElementFactory(project).createAnnotationFromText(annotationText, element);
      tracker.replaceAndRestoreComments(annotation, newAnnotation);
    }

    private static String buildAnnotationText(PsiAnnotation annotation, CommentTracker tracker) {
      final StringBuilder out = new StringBuilder("@");
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      assert nameReferenceElement != null;
      out.append(tracker.text(nameReferenceElement));
      final PsiAnnotationParameterList parameterList = annotation.getParameterList();
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      if (attributes.length == 0) {
        return out.toString();
      }
      PsiElement child = parameterList.getFirstChild();
      while (child != null) {
        if (child instanceof PsiNameValuePair attribute) {
          final String name = attribute.getName();
          if (attributes.length > 1 || name != null && !PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(name)) {
            out.append(name).append('=');
          }
          buildAttributeValueText(attribute.getValue(), out, tracker);
        }
        else {
          tracker.markUnchanged(child);
          out.append(child.getText());
        }
        child = child.getNextSibling();
      }
      return out.toString();
    }

    private static void buildAttributeValueText(PsiAnnotationMemberValue value, StringBuilder out, CommentTracker tracker) {
      if (value instanceof PsiArrayInitializerMemberValue arrayValue) {
        final PsiAnnotationMemberValue[] initializers = arrayValue.getInitializers();
        if (initializers.length == 1) {
          out.append(tracker.text(initializers[0]));
          return;
        }
      }
      else if (value instanceof PsiAnnotation annotation) {
        out.append(buildAnnotationText(annotation, tracker));
        return;
      }
      out.append(tracker.text(value));
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new SimplifiableAnnotationVisitor();
  }

  private static class SimplifiableAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      final PsiAnnotationParameterList parameterList = annotation.getParameterList();
      final PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
      if (nameReferenceElement == null) {
        return;
      }
      final PsiElement[] annotationChildren = annotation.getChildren();
      if (annotationChildren.length >= 2 && annotationChildren[1] instanceof PsiWhiteSpace && !containsError(annotation)) {
        if (registerProblem(annotation, annotationChildren[1])) return;
      }
      if (annotationChildren.length >= 4) {
        final PsiElement child = annotationChildren[annotationChildren.length - 2];
        if (child instanceof PsiWhiteSpace && !containsError(annotation)) {
          if (registerProblem(annotation, child)) return;
        }
      }
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      if (attributes.length == 0) {
        if (parameterList.getFirstChild() != null && !containsError(annotation)) {
          registerProblem(annotation, parameterList);
        }
      }
      else if (attributes.length == 1) {
        final PsiNameValuePair attribute = attributes[0];
        final String name = attribute.getName();
        final PsiAnnotationMemberValue attributeValue = attribute.getValue();
        if (attributeValue != null && PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(name) && !containsError(annotation)) {
          if (registerProblem(annotation, attribute)) return;
        }
        if (attributeValue instanceof PsiArrayInitializerMemberValue arrayValue
            && arrayValue.getInitializers().length == 1
            && !containsError(annotation)) {
          registerProblem(annotation, arrayValue);
        }
      }
      else {
        for (PsiNameValuePair attribute : attributes) {
          if (attribute.getValue() instanceof PsiArrayInitializerMemberValue arrayValue
              && arrayValue.getInitializers().length == 1
              && !containsError(annotation)) {
            registerProblem(annotation, arrayValue);
          }
        }
      }
    }

    /**
     * @return true, if entire annotation reported; false otherwise.
     */
    private boolean registerProblem(PsiAnnotation annotation, PsiElement errorElement) {
      final boolean reportAnnotation = !isOnTheFly() || !isVisibleHighlight(errorElement);
      if (errorElement instanceof PsiArrayInitializerMemberValue arrayValue) {
        registerError(reportAnnotation ? annotation : arrayValue.getFirstChild(), ProblemHighlightType.LIKE_UNUSED_SYMBOL, arrayValue);
        if (reportAnnotation) return true;
        registerError(arrayValue.getLastChild(), ProblemHighlightType.LIKE_UNUSED_SYMBOL, errorElement);
      }
      else if (errorElement instanceof PsiNameValuePair attribute) {
        final PsiAnnotationMemberValue attributeValue = attribute.getValue();
        if (attributeValue != null) {
          if (reportAnnotation) {
            registerError(annotation, errorElement);
            return true;
          }
          else {
            registerErrorAtOffset(attribute, 0, attributeValue.getStartOffsetInParent(),
                                  ProblemHighlightType.LIKE_UNUSED_SYMBOL, errorElement);
            return false;
          }
        }
      }
      else {
        final ProblemHighlightType highlightType =
          errorElement instanceof PsiWhiteSpace ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : ProblemHighlightType.LIKE_UNUSED_SYMBOL;
        registerError(reportAnnotation ? annotation : errorElement, highlightType, errorElement);
      }
      return reportAnnotation;
    }

    private static boolean containsError(PsiAnnotation annotation) {
      final PsiClass aClass = annotation.resolveAnnotationType();
      if (aClass == null) {
        return true;
      }
      final Set<String> names = new HashSet<>();
      final PsiAnnotationParameterList annotationParameterList = annotation.getParameterList();
      if (PsiUtilCore.hasErrorElementChild(annotationParameterList)) {
        return true;
      }
      for (PsiNameValuePair attribute : annotationParameterList.getAttributes()) {
        final PsiReference reference = attribute.getReference();
        if (reference == null) {
          return true;
        }
        final PsiMethod method = (PsiMethod)reference.resolve();
        if (method == null) {
          return true;
        }
        final PsiAnnotationMemberValue value = attribute.getValue();
        if (value == null || PsiUtilCore.hasErrorElementChild(value)) {
          return true;
        }
        if (value instanceof PsiAnnotation a && containsError(a)) {
          return true;
        }
        if (!hasCorrectType(value, method.getReturnType())) {
          return true;
        }
        final String name = attribute.getName();
        if (!names.add(name != null ? name : PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME)) {
          return true;
        }
      }

      for (PsiMethod method : aClass.getMethods()) {
        if (!(method instanceof PsiAnnotationMethod annotationMethod)) {
          continue;
        }
        if (annotationMethod.getDefaultValue() == null && !names.contains(annotationMethod.getName())) {
          return true; // missing a required argument
        }
      }
      return false;
    }

    private static boolean hasCorrectType(@Nullable PsiAnnotationMemberValue value, PsiType expectedType) {
      if (value == null) return false;

      if (expectedType instanceof PsiClassType &&
          expectedType.equalsToText(CommonClassNames.JAVA_LANG_CLASS) &&
          !(value instanceof PsiClassObjectAccessExpression)) {
        return false;
      }

      if (value instanceof PsiAnnotation annotation) {
        final PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
        if (nameRef == null) return true;

        if (expectedType instanceof PsiClassType type) {
          final PsiClass aClass = type.resolve();
          if (aClass != null && nameRef.isReferenceTo(aClass)) return true;
        }

        if (expectedType instanceof PsiArrayType type && type.getComponentType() instanceof PsiClassType classType) {
          final PsiClass aClass = classType.resolve();
          if (aClass != null && nameRef.isReferenceTo(aClass)) return true;
        }
        return false;
      }
      if (value instanceof PsiArrayInitializerMemberValue) {
        return expectedType instanceof PsiArrayType;
      }
      if (value instanceof PsiExpression expression) {
        return expression.getType() != null && TypeConversionUtil.areTypesAssignmentCompatible(expectedType, expression) ||
               expectedType instanceof PsiArrayType type &&
               TypeConversionUtil.areTypesAssignmentCompatible(type.getComponentType(), expression);
      }
      return true;
    }
  }
}
