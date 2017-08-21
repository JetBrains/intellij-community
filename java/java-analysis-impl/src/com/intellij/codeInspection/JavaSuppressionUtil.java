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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Generated;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;

public class JavaSuppressionUtil {
  public static final String SUPPRESS_INSPECTIONS_ANNOTATION_NAME = "java.lang.SuppressWarnings";
  public static boolean alreadyHas14Suppressions(@NotNull PsiJavaDocumentedElement commentOwner) {
    final PsiDocComment docComment = commentOwner.getDocComment();
    return docComment != null && docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME) != null;
  }

  @Nullable
  private static String getInspectionIdSuppressedInAnnotationAttribute(PsiElement element) {
    if (element instanceof PsiLiteralExpression) {
      final Object value = ((PsiLiteralExpression)element).getValue();
      if (value instanceof String) {
        return (String)value;
      }
    }
    else if (element instanceof PsiReferenceExpression) {
      final PsiElement psiElement = ((PsiReferenceExpression)element).resolve();
      if (psiElement instanceof PsiVariableEx) {
        final Object val = ((PsiVariableEx)psiElement).computeConstantValue(new THashSet<>());
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
    final PsiElement parent = modifierList.getParent();
    if (!(parent instanceof PsiModifierListOwner)) {
      return Collections.emptyList();
    }
    final PsiModifierListOwner owner = (PsiModifierListOwner)parent;
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    if (annotation == null) {
      return Collections.emptyList();
    }
    return CachedValuesManager.getCachedValue(annotation, () ->
      CachedValueProvider.Result.create(getInspectionIdsSuppressedInAnnotation(annotation),
                                        PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT));
  }

  @NotNull
  private static Collection<String> getInspectionIdsSuppressedInAnnotation(PsiAnnotation annotation) {
    final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length == 0) {
      return Collections.emptyList();
    }
    final PsiAnnotationMemberValue attributeValue = attributes[0].getValue();
    Collection<String> result = new ArrayList<>();
    if (attributeValue instanceof PsiArrayInitializerMemberValue) {
      final PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)attributeValue).getInitializers();
      for (PsiAnnotationMemberValue annotationMemberValue : initializers) {
        final String id = getInspectionIdSuppressedInAnnotationAttribute(annotationMemberValue);
        if (id != null) {
          result.add(id);
        }
      }
    }
    else {
      final String id = getInspectionIdSuppressedInAnnotationAttribute(attributeValue);
      if (id != null) {
        result.add(id);
      }
    }
    return result;
  }

  public static PsiElement getElementMemberSuppressedIn(@NotNull PsiJavaDocumentedElement owner, @NotNull String inspectionToolID) {
    PsiElement element = getDocCommentToolSuppressedIn(owner, inspectionToolID);
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

    final PsiJavaFile file = PsiTreeUtil.getParentOfType(owner, PsiJavaFile.class);
    if (file != null) {
      final PsiDirectory directory = file.getContainingDirectory();
      if (directory != null) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
        if (aPackage != null) {
          return AnnotationUtil.findAnnotation(aPackage, Generated.class.getName());
        }
      }
    }

    return null;
  }

  static PsiElement getAnnotationMemberSuppressedIn(@NotNull PsiModifierListOwner owner, @NotNull String inspectionToolID) {
    PsiModifierList modifierList = owner.getModifierList();
    Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation(modifierList);
    for (String ids : suppressedIds) {
      if (SuppressionUtil.isInspectionToolIdMentioned(ids, inspectionToolID)) {
        return modifierList != null ? AnnotationUtil.findAnnotation(owner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME) : null;
      }
    }
    return AnnotationUtil.findAnnotation(owner, Generated.class.getName());
  }

  static PsiElement getDocCommentToolSuppressedIn(@NotNull PsiJavaDocumentedElement owner, @NotNull String inspectionToolID) {
    PsiDocComment docComment = owner.getDocComment();
    if (docComment == null && owner.getParent() instanceof PsiDeclarationStatement) {
      final PsiElement el = PsiTreeUtil.skipWhitespacesBackward(owner.getParent());
      if (el instanceof PsiDocComment) {
        docComment = (PsiDocComment)el;
      }
    }
    if (docComment != null) {
      PsiDocTag inspectionTag = docComment.findTagByName(SuppressionUtilCore.SUPPRESS_INSPECTIONS_TAG_NAME);
      if (inspectionTag != null) {
        final PsiElement[] dataElements = inspectionTag.getDataElements();
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

  public static Collection<String> getInspectionIdsSuppressedInAnnotation(@NotNull PsiModifierListOwner owner) {
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
          String valueText = "";
          for (PsiElement dataElement : inspectionTag.getDataElements()) {
            valueText += dataElement.getText();
          }
          return valueText;
        }
      }
    }
    if (element instanceof PsiModifierListOwner) {
      Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation((PsiModifierListOwner)element);
      return suppressedIds.isEmpty() ? null : StringUtil.join(suppressedIds, ",");
    }
    return null;
  }

  static PsiElement getElementToolSuppressedIn(@NotNull final PsiElement place, @NotNull final String toolId) {
    if (place instanceof PsiFile) return null;
    return ReadAction.compute(() -> {
      final PsiElement statement = SuppressionUtil.getStatementToolSuppressedIn(place, toolId, PsiStatement.class);
      if (statement != null) {
        return statement;
      }

      PsiElement up = PsiTreeUtil.getNonStrictParentOfType(place, PsiVariable.class, PsiJavaDocumentedElement.class);
      if (up instanceof PsiModifierListOwner && ((PsiModifierListOwner)up).getModifierList() == null) {
        up = PsiTreeUtil.getParentOfType(up, PsiVariable.class, PsiJavaDocumentedElement.class);
      }
      if (up instanceof PsiVariable) {
        PsiVariable local = (PsiVariable)up;
        final PsiElement annotation = getAnnotationMemberSuppressedIn(local, toolId);
        if (annotation != null) {
          return annotation;
        }
      }

      PsiJavaDocumentedElement container = up == null || up instanceof PsiJavaDocumentedElement
                                           ? (PsiJavaDocumentedElement)up
                                           : PsiTreeUtil.getNonStrictParentOfType(up, PsiJavaDocumentedElement.class);
      while (true) {
        if (!(container instanceof PsiTypeParameter)) break;
        container = PsiTreeUtil.getParentOfType(container, PsiJavaDocumentedElement.class);
      }

      if (container != null) {
        PsiElement element = getElementMemberSuppressedIn(container, toolId);
        if (element != null) return element;
      }
      PsiJavaDocumentedElement classContainer = PsiTreeUtil.getParentOfType(container, PsiJavaDocumentedElement.class, true);
      if (classContainer != null) {
        PsiElement element = getElementMemberSuppressedIn(classContainer, toolId);
        if (element != null) return element;
      }

      return null;
    });
  }

  public static void addSuppressAnnotation(@NotNull Project project,
                                           final PsiElement container,
                                           final PsiModifierListOwner modifierOwner,
                                           @NotNull String id) throws IncorrectOperationException {
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(modifierOwner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    final PsiAnnotation newAnnotation = createNewAnnotation(project, container, annotation, id);
    if (newAnnotation != null) {
      if (annotation != null && annotation.isPhysical()) {
        WriteCommandAction.runWriteCommandAction(project, null, null, () -> annotation.replace(newAnnotation), annotation.getContainingFile());
      }
      else {
        final PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();
        new AddAnnotationPsiFix(SUPPRESS_INSPECTIONS_ANNOTATION_NAME, modifierOwner, attributes).applyFix();
      }
    }
  }

  private static PsiAnnotation createNewAnnotation(@NotNull Project project,
                                                   PsiElement container,
                                                   PsiAnnotation annotation,
                                                   @NotNull String id) throws IncorrectOperationException {
    if (annotation == null) {
      return JavaPsiFacade.getInstance(project).getElementFactory()
        .createAnnotationFromText("@" + SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "(\"" + id + "\")", container);
    }
    final String currentSuppressedId = "\"" + id + "\"";
    if (!annotation.getText().contains("{")) {
      final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
      if (attributes.length == 1) {
        final String suppressedWarnings = attributes[0].getText();
        if (suppressedWarnings.contains(currentSuppressedId)) return null;
        return JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(
            "@" + SUPPRESS_INSPECTIONS_ANNOTATION_NAME + "({" + suppressedWarnings + ", " + currentSuppressedId + "})", container);

      }
    }
    else {
      final int curlyBraceIndex = annotation.getText().lastIndexOf("}");
      if (curlyBraceIndex > 0) {
        final String oldSuppressWarning = annotation.getText().substring(0, curlyBraceIndex);
        if (oldSuppressWarning.contains(currentSuppressedId)) return null;
        return JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(
          oldSuppressWarning + ", " + currentSuppressedId + "})", container);
      }
      else {
        throw new IncorrectOperationException(annotation.getText());
      }
    }
    return null;
  }

  public static boolean canHave15Suppressions(@NotNull PsiElement file) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(file);
    if (module == null) return false;
    final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
    if (jdk == null) return false;
    JavaSdkVersion version = getVersion(jdk);
    if (version == null) return false;
    final boolean is_1_5 = version.isAtLeast(JavaSdkVersion.JDK_1_5);
    return DaemonCodeAnalyzerSettings.getInstance().isSuppressWarnings() && is_1_5 && PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Nullable
  private static JavaSdkVersion getVersion(@NotNull Sdk sdk) {
    String version = sdk.getVersionString();
    if (version == null) return null;
    return JavaSdkVersion.fromVersionString(version);
  }

  @Nullable
  public static PsiElement getElementToAnnotate(PsiElement element, PsiElement container) {
    if (container instanceof PsiDeclarationStatement) {
      if (canHave15Suppressions(element)) {
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)container;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        for (PsiElement declaredElement : declaredElements) {
          if (declaredElement instanceof PsiLocalVariable) {
            final PsiModifierList modifierList = ((PsiLocalVariable)declaredElement).getModifierList();
            if (modifierList != null) {
              return declaredElement;
            }
          }
        }
      }
    }
    else if (container instanceof PsiForeachStatement) {
      if (canHave15Suppressions(element)) {
        final PsiParameter parameter = ((PsiForeachStatement)container).getIterationParameter();
        final PsiModifierList modifierList = element.getParent() == parameter ? parameter.getModifierList() : null;
        if (modifierList != null) {
          return parameter;
        }
      }
    }
    else if (container instanceof PsiTryStatement) {
      final PsiResourceList resourceList = ((PsiTryStatement)container).getResourceList();
      if (resourceList != null) {
        for (PsiResourceListElement listElement : resourceList) {
          if (listElement instanceof PsiResourceVariable && listElement == element.getParent()) {
            final PsiModifierList modifierList = ((PsiResourceVariable)listElement).getModifierList();
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
