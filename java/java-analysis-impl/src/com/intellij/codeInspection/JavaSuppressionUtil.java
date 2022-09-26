// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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

  @Nullable
  private static String getInspectionIdSuppressedInAnnotationAttribute(@NotNull PsiElement element) {
    if (element instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)element).getValue();
      if (value instanceof String) {
        return (String)value;
      }
    }
    else if (element instanceof PsiReferenceExpression) {
      PsiElement psiElement = ((PsiReferenceExpression)element).resolve();
      if (psiElement instanceof PsiVariableEx) {
        Object val = ((PsiVariableEx)psiElement).computeConstantValue(new HashSet<>());
        if (val instanceof String) {
          return (String)val;
        }
      }
    }
    return null;
  }

  @NotNull
  public static Collection<String> getInspectionIdsSuppressedInAnnotation(@Nullable PsiModifierList modifierList) {
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

  @NotNull
  private static Collection<String> getInspectionIdsSuppressedInAnnotation(@NotNull PsiAnnotation annotation) {
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length == 0) {
      return Collections.emptyList();
    }
    return ContainerUtil.mapNotNull(AnnotationUtil.arrayAttributeValues(attributes[0].getValue()),
                                    JavaSuppressionUtil::getInspectionIdSuppressedInAnnotationAttribute);
  }

  static <T extends PsiElement> PsiElement getElementMemberSuppressedIn(@NotNull T owner, @NotNull String inspectionToolID) {
    PsiElement element = null;
    if (owner instanceof PsiJavaDocumentedElement) {
      element = getDocCommentToolSuppressedIn((PsiJavaDocumentedElement)owner, inspectionToolID);
    }
    if (element != null) return element;
    if (owner instanceof PsiModifierListOwner) {
      element = getAnnotationMemberSuppressedIn((PsiModifierListOwner)owner, inspectionToolID);
      if (element != null) return element;
    }
    PsiJavaDocumentedElement container = PsiTreeUtil.getParentOfType(owner, PsiJavaDocumentedElement.class);
    while (container != null) {
      element = getDocCommentToolSuppressedIn(container, inspectionToolID);
      if (element != null) return element;

      if (container instanceof PsiModifierListOwner) {
        element = getAnnotationMemberSuppressedIn((PsiModifierListOwner)container, inspectionToolID);
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
      if (el instanceof PsiDocComment) {
        docComment = (PsiDocComment)el;
      }
    }
    if (docComment != null) {
      PsiDocTag inspectionTag = docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
      if (inspectionTag != null) {
        PsiElement[] dataElements = inspectionTag.getDataElements();
        for (PsiElement dataElement : dataElements) {
          String valueText = dataElement.getText();
          if (SuppressionUtil.isInspectionToolIdMentioned(valueText, inspectionToolID)) {
            return docComment;
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static Collection<String> getInspectionIdsSuppressedInAnnotation(@NotNull PsiModifierListOwner owner) {
    if (!PsiUtil.isLanguageLevel5OrHigher(owner)) return Collections.emptyList();
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
    if (element instanceof PsiJavaDocumentedElement) {
      PsiDocComment docComment = ((PsiJavaDocumentedElement)element).getDocComment();
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
    if (element instanceof PsiModifierListOwner) {
      Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation((PsiModifierListOwner)element);
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
      if (up instanceof PsiModifierListOwner && ((PsiModifierListOwner)up).getModifierList() == null) {
        up = PsiTreeUtil.getParentOfType(up, PsiVariable.class, PsiJavaDocumentedElement.class);
      }
      if (up instanceof PsiVariable) {
        PsiElement annotation = getAnnotationMemberSuppressedIn((PsiVariable)up, toolId);
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
                                           PsiModifierListOwner modifierOwner,
                                           @NotNull String id) throws IncorrectOperationException {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierOwner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    PsiAnnotation newAnnotation = createNewAnnotation(project, container, annotation, id);
    if (newAnnotation != null) {
      if (annotation != null && annotation.isPhysical()) {
        WriteCommandAction.runWriteCommandAction(project, null, null, () -> annotation.replace(newAnnotation), annotation.getContainingFile());
      }
      else {
        PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();
        new AddAnnotationPsiFix(SUPPRESS_INSPECTIONS_ANNOTATION_NAME, modifierOwner, attributes).applyFix();
      }
    }
  }

  private static PsiAnnotation createNewAnnotation(@NotNull Project project,
                                                   PsiElement container,
                                                   PsiAnnotation annotation,
                                                   @NotNull String id) throws IncorrectOperationException {
    if (annotation == null) {
      return JavaPsiFacade.getElementFactory(project)
        .createAnnotationFromText("@" + SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "(\"" + id + "\")", container);
    }
    String currentSuppressedId = "\"" + id + "\"";
    String annotationText = annotation.getText();
    if (annotationText.contains("{")) {
      int curlyBraceIndex = annotationText.lastIndexOf('}');
      if (curlyBraceIndex > 0) {
        String oldSuppressWarning = annotationText.substring(0, curlyBraceIndex);
        if (oldSuppressWarning.contains(currentSuppressedId)) return null;
        return JavaPsiFacade.getElementFactory(project).createAnnotationFromText(
          oldSuppressWarning + ", " + currentSuppressedId + "})", container);
      }
      else {
        throw new IncorrectOperationException(annotationText);
      }
    }
    else {
      PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      if (attributes.length == 1) {
        String suppressedWarnings = attributes[0].getText();
        if (suppressedWarnings.contains(currentSuppressedId)) return null;
        return JavaPsiFacade.getElementFactory(project).createAnnotationFromText(
            "@" + SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "({" + suppressedWarnings + ", " + currentSuppressedId + "})", container);

      }
    }
    return null;
  }

  public static boolean canHave15Suppressions(@NotNull PsiElement file) {
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return false;
    Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
    if (jdk == null) return false;
    JavaSdkVersion version = JavaSdkVersionUtil.getJavaSdkVersion(jdk);
    if (version == null) return false;
    boolean is_1_5 = version.isAtLeast(JavaSdkVersion.JDK_1_5);
    return DaemonCodeAnalyzerSettings.getInstance().isSuppressWarnings() && is_1_5 && PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Nullable
  public static PsiElement getElementToAnnotate(@NotNull PsiElement element, @NotNull PsiElement container) {
    if (container instanceof PsiDeclarationStatement) {
      if (canHave15Suppressions(element)) {
        PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)container;
        PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiLocalVariable) {
            PsiModifierList modifierList = ((PsiLocalVariable)declaredElement).getModifierList();
            if (modifierList != null) {
              return declaredElement;
            }
          }
        }
      }
    }
    else if (container instanceof PsiForeachStatement) {
      if (canHave15Suppressions(element)) {
        PsiParameter parameter = ((PsiForeachStatement)container).getIterationParameter();
        PsiModifierList modifierList = element.getParent() == parameter ? parameter.getModifierList() : null;
        if (modifierList != null) {
          return parameter;
        }
      }
    }
    else if (container instanceof PsiTryStatement) {
      PsiResourceList resourceList = ((PsiTryStatement)container).getResourceList();
      if (resourceList != null) {
        for (PsiResourceListElement listElement : resourceList) {
          if (listElement instanceof PsiResourceVariable && listElement == element.getParent()) {
            PsiModifierList modifierList = ((PsiResourceVariable)listElement).getModifierList();
            if (modifierList != null) {
              return listElement;
            }
          }
        }
      }
    }
    return null;
  }
}
