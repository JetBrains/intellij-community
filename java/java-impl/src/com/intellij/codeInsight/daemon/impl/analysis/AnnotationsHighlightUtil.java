/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class AnnotationsHighlightUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil");
  @NonNls private static final String PACKAGE_INFO_JAVA = "package-info.java";

  public static HighlightInfo checkNameValuePair(PsiNameValuePair pair) {
    PsiReference ref = pair.getReference();
    if (ref == null) return null;
    PsiMethod method = (PsiMethod)ref.resolve();
    if (method == null) {
      if (pair.getName() != null) {
        String description = JavaErrorMessages.message("annotation.unknown.method", ref.getCanonicalText());
        return HighlightInfo.createHighlightInfo(HighlightInfoType.WRONG_REF, ref.getElement(), description);
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
        if (attribute.getName() != null) {
          names.add(attribute.getName());
        }
        else {
          names.add(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
        }
      }

      PsiMethod[] annotationMethods = aClass.getMethods();
      List<String> missed = new ArrayList<String>();
      for (PsiMethod method : annotationMethods) {
        if (method instanceof PsiAnnotationMethod) {
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
    if (expression.getParent() instanceof PsiAnnotationMethod || expression.getParent() instanceof PsiNameValuePair) {
      if (PsiType.NULL.equals(expression.getType()) || !PsiUtil.isConstantExpression(expression)) {
        return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, expression, JavaErrorMessages.message("annotation.nonconstant.attribute.value"));
      }
    }

    return null;
  }

  public static HighlightInfo checkValidAnnotationType(final PsiTypeElement typeElement) {
    PsiType type = typeElement.getType();
    if (type.accept(AnnotationReturnTypeVisitor.INSTANCE).booleanValue()) {
      return null;
    }
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, JavaErrorMessages.message("annotation.invalid.annotation.member.type"));
  }

  public static HighlightInfo checkApplicability(PsiAnnotation annotation) {
    PsiAnnotationOwner owner = annotation.getOwner();
    if (!(owner instanceof PsiModifierList || owner instanceof PsiTypeElement || owner instanceof PsiMethodReceiver || owner instanceof PsiTypeParameter)) return null;
    PsiElement member = ((PsiElement)owner).getParent();
    String[] elementTypeFields = getApplicableElementTypeFields(owner instanceof PsiModifierList ? member : (PsiElement)owner);
    if (isAnnotationApplicableTo(annotation, false, elementTypeFields)) return null;
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    String description = JavaErrorMessages.message("annotation.not.applicable",
                                                   nameRef.getText(),
                                                   JavaErrorMessages.message("annotation.target." + elementTypeFields[0]));
    return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, nameRef, description);
  }

  public static boolean isAnnotationApplicableTo(PsiAnnotation annotation, boolean strict, String... elementTypeFields) {
    if (elementTypeFields == null) return true;
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) {
      return !strict;
    }
    PsiElement resolved = nameRef.resolve();
    if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) {
      return !strict;
    }
    PsiClass annotationType = (PsiClass)resolved;
    PsiAnnotation target = annotationType.getModifierList().findAnnotation(AnnotationUtil.TARGET_ANNOTATION_FQ_NAME);
    if (target == null) {
      //todo hack: ambiguity in spec
      return !strict;
      //return !ArrayUtil.contains("TYPE_USE", elementTypeFields);
    }
    PsiNameValuePair[] attributes = target.getParameterList().getAttributes();
    if (attributes.length == 0) {
      return !strict;
    }
    PsiAnnotationMemberValue value = attributes[0].getValue();
    LOG.assertTrue(elementTypeFields.length > 0);

    PsiManager manager = annotation.getManager();
    PsiClass elementTypeClass = JavaPsiFacade.getInstance(manager.getProject()).findClass("java.lang.annotation.ElementType", annotation.getResolveScope());
    if (elementTypeClass == null) {
      //todo hack
      return !strict;
      //return !ArrayUtil.contains("TYPE_USE", elementTypeFields);
    }

    for (String fieldName : elementTypeFields) {
      PsiField field = elementTypeClass.findFieldByName(fieldName, false);
      if (field == null) continue;
      if (value instanceof PsiArrayInitializerMemberValue) {
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
        for (PsiAnnotationMemberValue initializer : initializers) {
          if (initializer instanceof PsiReferenceExpression) {
            PsiReferenceExpression refExpr = (PsiReferenceExpression)initializer;
            if (refExpr.isReferenceTo(field)) return true;
          }
        }
      }
      else if (value instanceof PsiReferenceExpression) {
        if (((PsiReferenceExpression)value).isReferenceTo(field)) return true;
      }
    }
    return false;
  }

  public static String[] getApplicableElementTypeFields(PsiElement owner) {
    if (owner instanceof PsiClass) {
      PsiClass aClass = (PsiClass)owner;
      if (aClass.isAnnotationType()) {
        return new String[]{"ANNOTATION_TYPE", "TYPE"};
      }
      else if (aClass instanceof PsiTypeParameter) {
        return new String[]{"TYPE_PARAMETER"};
      }
      else {
        return new String[]{"TYPE"};
      }
    }
    if (owner instanceof PsiMethod) {
      if (((PsiMethod)owner).isConstructor()) {
        return new String[]{"CONSTRUCTOR"};
      }
      else {
        return new String[]{"METHOD"};
      }
    }
    if (owner instanceof PsiField) {
      return new String[]{"FIELD"};
    }
    if (owner instanceof PsiParameter) {
      return new String[]{"PARAMETER"};
    }
    if (owner instanceof PsiLocalVariable) {
      return new String[]{"LOCAL_VARIABLE"};
    }
    if (owner instanceof PsiPackageStatement) {
      return new String[]{"PACKAGE"};
    }
    if (owner instanceof PsiTypeElement) {
      return new String[]{"TYPE_USE"};
    }

    return null;
  }

  private static PsiField[] getFields(final PsiClass elementTypeClass, @NonNls final String... names) {
    PsiField[] result = new PsiField[names.length];
    for (int i = 0; i < names.length; i++) {
      final PsiField field = elementTypeClass.findFieldByName(names[i], false);
      if (field == null) return null;
      result[i] = field;
    }

    return result;
  }

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

  public static HighlightInfo checkCyclicMemberType(PsiTypeElement typeElement, PsiClass aClass) {
    LOG.assertTrue(aClass.isAnnotationType());
    PsiType type = typeElement.getType();
    if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == aClass) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR, typeElement, JavaErrorMessages.message("annotation.cyclic.element.type"));
    }
    return null;
  }

  public static HighlightInfo checkAnnotationDeclaration(final PsiElement parent, final PsiReferenceList list) {
    if (parent instanceof PsiAnnotationMethod) {
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


  public static HighlightInfo checkPackageAnnotationContainingFile(final PsiPackageStatement statement) {
    if (statement.getAnnotationList() == null) {
      return null;
    }
    PsiFile file = statement.getContainingFile();
    if (file != null && !PACKAGE_INFO_JAVA.equals(file.getName())) {
      return HighlightInfo.createHighlightInfo(HighlightInfoType.ERROR,
                                               statement.getAnnotationList().getTextRange(),
                                               JavaErrorMessages.message("invalid.package.annotation.containing.file"));

    }
    return null;
  }

  public static HighlightInfo checkTargetAnnotationDuplicates(PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) return null;
    PsiElement resolved = nameRef.resolve();
    if (!(resolved instanceof PsiClass) ||
        !AnnotationUtil.TARGET_ANNOTATION_FQ_NAME.equals(((PsiClass) resolved).getQualifiedName())) return null;

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

  private static class AnnotationReturnTypeVisitor extends PsiTypeVisitor<Boolean> {
    private static final AnnotationReturnTypeVisitor INSTANCE = new AnnotationReturnTypeVisitor();
    public Boolean visitType(PsiType type) {
      return Boolean.FALSE;
    }

    public Boolean visitPrimitiveType(PsiPrimitiveType primitiveType) {
      return PsiType.VOID.equals(primitiveType) || PsiType.NULL.equals(primitiveType) ? Boolean.FALSE : Boolean.TRUE;
    }

    public Boolean visitArrayType(PsiArrayType arrayType) {
      if (arrayType.getArrayDimensions() != 1) return Boolean.FALSE;
      PsiType componentType = arrayType.getComponentType();
      return componentType.accept(this);
    }

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
}
