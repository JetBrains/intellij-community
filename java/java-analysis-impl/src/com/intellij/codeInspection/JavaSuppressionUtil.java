// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiForeachStatement;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiResourceList;
import com.intellij.psi.PsiResourceListElement;
import com.intellij.psi.PsiResourceVariable;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;

public final class JavaSuppressionUtil {
  private static final String GENERATED_ANNOTATION_NAME = "javax.annotation.Generated";
  private static final String JDK9_GENERATED_ANNOTATION_NAME = "javax.annotation.processing.Generated";

  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";

  public static boolean alreadyHas14Suppressions(@NotNull PsiJavaDocumentedElement commentOwner) {
    PsiDocComment docComment = commentOwner.getDocComment();
    return docComment != null && docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME) != null;
  }

  private static @Nullable String getInspectionIdSuppressedInAnnotationAttribute(@NotNull PsiElement element) {
    if (element instanceof PsiLiteralExpression literal) {
      if (literal.getValue() instanceof String s) {
        return s;
      }
    }
    else if (element instanceof PsiReferenceExpression ref) {
      if (ref.resolve() instanceof PsiVariableEx var && var.computeConstantValue(new HashSet<>()) instanceof String s) {
        return s;
      }
    }
    return null;
  }

  public static @NotNull Collection<String> getInspectionIdsSuppressedInAnnotation(@Nullable PsiModifierList modifierList) {
    if (modifierList == null) {
      return Collections.emptyList();
    }
    PsiElement parent = modifierList.getParent();
    if (!(parent instanceof PsiModifierListOwner owner)) {
      return Collections.emptyList();
    }
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    if (annotation == null) {
      return Collections.emptyList();
    }
    return CachedValuesManager.getCachedValue(annotation, () ->
      CachedValueProvider.Result.create(getInspectionIdsSuppressedInAnnotation(annotation),
                                        PsiModificationTracker.MODIFICATION_COUNT));
  }

  private static @NotNull @Unmodifiable Collection<String> getInspectionIdsSuppressedInAnnotation(@NotNull PsiAnnotation annotation) {
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length == 0) {
      return Collections.emptyList();
    }
    return ContainerUtil.mapNotNull(AnnotationUtil.arrayAttributeValues(attributes[0].getValue()),
                                    JavaSuppressionUtil::getInspectionIdSuppressedInAnnotationAttribute);
  }

  static <T extends PsiElement> PsiElement getElementMemberSuppressedIn(@NotNull T owner, @NotNull String inspectionToolID) {
    PsiElement element = null;
    if (owner instanceof PsiJavaDocumentedElement documented) {
      element = getDocCommentToolSuppressedIn(documented, inspectionToolID);
    }
    if (element != null) return element;
    if (owner instanceof PsiModifierListOwner o) {
      element = getAnnotationMemberSuppressedIn(o, inspectionToolID);
      if (element != null) return element;
    }
    PsiJavaDocumentedElement container = PsiTreeUtil.getParentOfType(owner, PsiJavaDocumentedElement.class);
    while (container != null) {
      element = getDocCommentToolSuppressedIn(container, inspectionToolID);
      if (element != null) return element;

      if (container instanceof PsiModifierListOwner o) {
        element = getAnnotationMemberSuppressedIn(o, inspectionToolID);
        if (element != null) return element;
      }

      container = PsiTreeUtil.getParentOfType(container, PsiJavaDocumentedElement.class);
    }

    PsiJavaFile file = PsiTreeUtil.getParentOfType(owner, PsiJavaFile.class);
    if (file != null) {
      PsiDirectory directory = file.getContainingDirectory();
      if (directory != null) {
        PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (aPackage != null) {
          return AnnotationUtil.findAnnotation(aPackage, GENERATED_ANNOTATION_NAME, JDK9_GENERATED_ANNOTATION_NAME);
        }
      }
    }

    return null;
  }

  private static final Set<String> GENERATED_RELATED_ANNOTATIONS = Set.of(GENERATED_ANNOTATION_NAME, JDK9_GENERATED_ANNOTATION_NAME);
  private static PsiElement getAnnotationMemberSuppressedIn(@NotNull PsiModifierListOwner owner, @NotNull String inspectionToolID) {
    PsiModifierList modifierList = owner.getModifierList();
    Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation(modifierList);
    for (String ids : suppressedIds) {
      if (SuppressionUtil.isInspectionToolIdMentioned(ids, inspectionToolID)) {
        return modifierList != null ? AnnotationUtil.findAnnotation(owner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME) : null;
      }
    }
    return AnnotationUtil.findAnnotation(owner, GENERATED_RELATED_ANNOTATIONS, false);
  }

  private static PsiElement getDocCommentToolSuppressedIn(@NotNull PsiJavaDocumentedElement owner, @NotNull String inspectionToolID) {
    PsiDocComment docComment = owner.getDocComment();
    if (docComment == null && owner.getParent() instanceof PsiDeclarationStatement) {
      PsiElement el = PsiTreeUtil.skipWhitespacesBackward(owner.getParent());
      if (el instanceof PsiDocComment c) {
        docComment = c;
      }
    }
    if (docComment != null) {
      PsiDocTag inspectionTag = docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
      if (inspectionTag != null) {
        for (PsiElement dataElement : inspectionTag.getDataElements()) {
          String valueText = dataElement.getText();
          if (SuppressionUtil.isInspectionToolIdMentioned(valueText, inspectionToolID)) {
            return docComment;
          }
        }
      }
    }
    return null;
  }

  private static @NotNull Collection<String> getInspectionIdsSuppressedInAnnotation(@NotNull PsiModifierListOwner owner) {
    if (!PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, owner)) return Collections.emptyList();
    PsiModifierList modifierList = owner.getModifierList();
    return getInspectionIdsSuppressedInAnnotation(modifierList);
  }

  public static String getSuppressedInspectionIdsIn(@NotNull PsiElement element) {
    if (element instanceof PsiComment) {
      String text = element.getText();
      Matcher matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
      if (matcher.matches()) {
        return matcher.group(1).trim();
      }
    }
    if (element instanceof PsiJavaDocumentedElement documented) {
      PsiDocComment docComment = documented.getDocComment();
      if (docComment != null) {
        PsiDocTag inspectionTag = docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
        if (inspectionTag != null) {
          StringBuilder valueText = new StringBuilder();
          for (PsiElement dataElement : inspectionTag.getDataElements()) {
            valueText.append(dataElement.getText());
          }
          return valueText.toString();
        }
      }
    }
    if (element instanceof PsiModifierListOwner owner) {
      Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation(owner);
      return suppressedIds.isEmpty() ? null : StringUtil.join(suppressedIds, ",");
    }
    return null;
  }

  static PsiElement getElementToolSuppressedIn(@NotNull PsiElement place, @NotNull String toolId) {
    if (place instanceof PsiFile) return null;
    return ReadAction.compute(() -> {
      PsiElement statement = SuppressionUtil.getStatementToolSuppressedIn(place, toolId, PsiStatement.class);
      if (statement != null) {
        return statement;
      }

      PsiElement up = PsiTreeUtil.getNonStrictParentOfType(place, PsiVariable.class, PsiJavaDocumentedElement.class);
      if (up instanceof PsiModifierListOwner o && o.getModifierList() == null) {
        up = PsiTreeUtil.getParentOfType(up, PsiVariable.class, PsiJavaDocumentedElement.class);
      }
      if (up instanceof PsiVariable var) {
        PsiElement annotation = getAnnotationMemberSuppressedIn(var, toolId);
        if (annotation != null) {
          return annotation;
        }
      }

      PsiJavaDocumentedElement container = up == null || up instanceof PsiJavaDocumentedElement
                                           ? (PsiJavaDocumentedElement)up
                                           : PsiTreeUtil.getNonStrictParentOfType(up, PsiJavaDocumentedElement.class);
      while (container instanceof PsiTypeParameter) {
        container = PsiTreeUtil.getParentOfType(container, PsiJavaDocumentedElement.class);
      }

      if (container != null) {
        PsiElement element = getElementMemberSuppressedIn(container, toolId);
        if (element != null) return element;
      }
      PsiJavaDocumentedElement classContainer = PsiTreeUtil.getParentOfType(container, PsiJavaDocumentedElement.class, true);
      if (classContainer != null) {
        return getElementMemberSuppressedIn(classContainer, toolId);
      }

      return null;
    });
  }

  public static void addSuppressAnnotation(@NotNull Project project,
                                           PsiElement container,
                                           @NotNull PsiModifierListOwner modifierOwner,
                                           @NotNull String id) throws IncorrectOperationException {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierOwner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    PsiAnnotation newAnnotation = createNewAnnotation(project, container, annotation, id);
    if (newAnnotation != null) {
      if (annotation != null) {
        annotation.replace(newAnnotation);
      }
      else {
        PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();
        AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(SUPPRESS_INSPECTIONS_ANNOTATION_NAME, attributes,
                                                          Objects.requireNonNull(modifierOwner.getModifierList()));
      }
    }
  }

  private static PsiAnnotation createNewAnnotation(@NotNull Project project,
                                                   PsiElement container,
                                                   PsiAnnotation annotation,
                                                   @NotNull String id) {
    String escaped = '"' + StringUtil.escapeStringCharacters(id) + '"';
    if (annotation == null) {
      return JavaPsiFacade.getElementFactory(project)
        .createAnnotationFromText("@" + SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "(" + escaped + ")", container);
    }
    StringBuilder newAnnotationText = new StringBuilder("@").append(SUPPRESS_INSPECTIONS_ANNOTATION_NAME).append("(");
    PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
    if (value instanceof PsiArrayInitializerMemberValue array) {
      PsiAnnotationMemberValue[] initializers = array.getInitializers();
      if (initializers.length > 0) {
        newAnnotationText.append('{');
        for (PsiAnnotationMemberValue initializer : initializers) {
          newAnnotationText.append(initializer.getText()).append(',');
          if (initializer instanceof PsiExpression expression) {
            if (id.equals(ExpressionUtils.computeConstantExpression(expression))) return null;
          }
        }
        newAnnotationText.append(escaped).append('}');
      }
      else {
        newAnnotationText.append(escaped);
      }
    }
    else if (value instanceof PsiExpression expression) {
      if (id.equals(ExpressionUtils.computeConstantExpression(expression))) return null;
      newAnnotationText.append("{").append(expression.getText()).append(", ").append(escaped).append("}");
    }
    else {
      newAnnotationText.append(escaped);
    }
    return JavaPsiFacade.getElementFactory(project).createAnnotationFromText(newAnnotationText.append(")").toString(), container);
  }

  public static boolean canHave15Suppressions(@NotNull PsiElement element) {
    return DaemonCodeAnalyzerSettings.getInstance().isSuppressWarnings() && PsiUtil.isAvailable(JavaFeature.ANNOTATIONS, element);
  }

  public static @Nullable PsiElement getElementToAnnotate(@NotNull PsiElement element, @NotNull PsiElement container) {
    if (container instanceof PsiDeclarationStatement declarationStatement) {
      if (canHave15Suppressions(element)) {
        for (PsiElement declaredElement : declarationStatement.getDeclaredElements()) {
          if (declaredElement instanceof PsiLocalVariable var) {
            if (var.getModifierList() != null) {
              return declaredElement;
            }
          }
        }
      }
    }
    else if (container instanceof PsiForeachStatement foreach) {
      if (canHave15Suppressions(element)) {
        PsiParameter parameter = foreach.getIterationParameter();
        PsiModifierList modifierList = element.getParent() == parameter ? parameter.getModifierList() : null;
        if (modifierList != null) {
          return parameter;
        }
      }
    }
    else if (container instanceof PsiTryStatement statement) {
      PsiResourceList resourceList = statement.getResourceList();
      if (resourceList != null) {
        for (PsiResourceListElement listElement : resourceList) {
          if (listElement == element.getParent() && listElement instanceof PsiResourceVariable var && var.getModifierList() != null) {
            return listElement;
          }
        }
      }
    }
    return null;
  }
}
