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

/*
 * User: anna
 * Date: 24-Dec-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.actions.SuppressByJavaCommentFix;
import com.intellij.codeInsight.daemon.impl.actions.SuppressAllForClassFix;
import com.intellij.codeInsight.daemon.impl.actions.SuppressFix;
import com.intellij.codeInsight.daemon.impl.actions.SuppressForClassFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiVariableEx;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Generated;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.regex.Matcher;

public class SuppressManagerImpl extends SuppressManager {

  public SuppressIntentionAction[] createSuppressActions(@NotNull final HighlightDisplayKey displayKey) {
    return new SuppressIntentionAction[]{
        new SuppressByJavaCommentFix(displayKey),
        new SuppressFix(displayKey),
        new SuppressForClassFix(displayKey),
        new SuppressAllForClassFix()
      };
  }

  public boolean isSuppressedFor(@NotNull final PsiElement element, final String toolId) {
    return getElementToolSuppressedIn(element, toolId) != null;
  }

  @Nullable
  public PsiElement getElementMemberSuppressedIn(@NotNull final PsiDocCommentOwner owner, final String inspectionToolID) {
    PsiElement element = getDocCommentToolSuppressedIn(owner, inspectionToolID);
    if (element != null) return element;
    element = getAnnotationMemberSuppressedIn(owner, inspectionToolID);
    if (element != null) return element;
    PsiDocCommentOwner classContainer = PsiTreeUtil.getParentOfType(owner, PsiDocCommentOwner.class);
    while (classContainer != null) {
      element = getDocCommentToolSuppressedIn(classContainer, inspectionToolID);
      if (element != null) return element;

      element = getAnnotationMemberSuppressedIn(classContainer, inspectionToolID);
      if (element != null) return element;

      classContainer = PsiTreeUtil.getParentOfType(classContainer, PsiDocCommentOwner.class);
    }
    return null;
  }

  @Nullable
  public PsiElement getAnnotationMemberSuppressedIn(@NotNull final PsiModifierListOwner owner, final String inspectionToolID) {
    final PsiAnnotation generatedAnnotation = AnnotationUtil.findAnnotation(owner, Generated.class.getName());
    if (generatedAnnotation != null) return generatedAnnotation;
    PsiModifierList modifierList = owner.getModifierList();
    Collection<String> suppressedIds = getInspectionIdsSuppressedInAnnotation(modifierList);
    for (String ids : suppressedIds) {
      if (SuppressionUtil.isInspectionToolIdMentioned(ids, inspectionToolID)) {
        return modifierList != null ? AnnotationUtil.findAnnotation(owner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME) : null;
      }
    }
    return null;
  }

  @Nullable
  public PsiElement getDocCommentToolSuppressedIn(@NotNull final PsiDocCommentOwner owner, final String inspectionToolID) {
    PsiDocComment docComment = owner.getDocComment();
    if (docComment == null && owner.getParent() instanceof PsiDeclarationStatement) {
      final PsiElement el = PsiTreeUtil.skipSiblingsBackward(owner.getParent(), PsiWhiteSpace.class);
      if (el instanceof PsiDocComment) {
        docComment = (PsiDocComment)el;
      }
    }
    if (docComment != null) {
      PsiDocTag inspectionTag = docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME);
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

  @NotNull
  public Collection<String> getInspectionIdsSuppressedInAnnotation(@NotNull final PsiModifierListOwner owner) {
    if (!PsiUtil.isLanguageLevel5OrHigher(owner)) return Collections.emptyList();
    PsiModifierList modifierList = owner.getModifierList();
    return getInspectionIdsSuppressedInAnnotation(modifierList);
  }

  @Nullable
  public String getSuppressedInspectionIdsIn(@NotNull PsiElement element) {
    if (element instanceof PsiComment) {
      String text = element.getText();
      Matcher matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(text);
      if (matcher.matches()) {
        return matcher.group(1).trim();
      }
    }
    if (element instanceof PsiDocCommentOwner) {
      PsiDocComment docComment = ((PsiDocCommentOwner)element).getDocComment();
      if (docComment != null) {
        PsiDocTag inspectionTag = docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME);
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

  @Nullable
  public PsiElement getElementToolSuppressedIn(@NotNull final PsiElement place, final String toolId) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiElement>() {
      @Nullable
      public PsiElement compute() {
        final PsiElement statement = SuppressionUtil.getStatementToolSuppressedIn(place, toolId, PsiStatement.class);
        if (statement != null) {
          return statement;
        }

        PsiLocalVariable local = PsiTreeUtil.getParentOfType(place, PsiLocalVariable.class);
        if (local != null && getAnnotationMemberSuppressedIn(local, toolId) != null) {
          PsiModifierList modifierList = local.getModifierList();
          return modifierList != null ? modifierList.findAnnotation(SUPPRESS_INSPECTIONS_ANNOTATION_NAME) : null;
        }

        PsiDocCommentOwner container = PsiTreeUtil.getNonStrictParentOfType(place, PsiDocCommentOwner.class);
        while (true) {
          if (!(container instanceof PsiTypeParameter)) break;
          container = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class);
        }

        if (container != null) {
          PsiElement element = getElementMemberSuppressedIn(container, toolId);
          if (element != null) return element;
        }
        PsiDocCommentOwner classContainer = PsiTreeUtil.getParentOfType(container, PsiDocCommentOwner.class, true);
        if (classContainer != null) {
          PsiElement element = getElementMemberSuppressedIn(classContainer, toolId);
          if (element != null) return element;
        }

        return null;
      }
    });
  }

  @NotNull
  public static Collection<String> getInspectionIdsSuppressedInAnnotation(final PsiModifierList modifierList) {
    if (modifierList == null) {
      return Collections.emptyList();
    }
    final PsiModifierListOwner owner = (PsiModifierListOwner)modifierList.getParent();
    PsiAnnotation annotation = AnnotationUtil.findAnnotation(owner, SUPPRESS_INSPECTIONS_ANNOTATION_NAME);
    if (annotation == null) {
      return Collections.emptyList();
    }
    final PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    if (attributes.length == 0) {
      return Collections.emptyList();
    }
    final PsiAnnotationMemberValue attributeValue = attributes[0].getValue();
    Collection<String> result = new ArrayList<String>();
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

  @Nullable
  public static String getInspectionIdSuppressedInAnnotationAttribute(PsiElement element) {
    if (element instanceof PsiLiteralExpression) {
      final Object value = ((PsiLiteralExpression)element).getValue();
      if (value instanceof String) {
        return (String)value;
      }
    }
    else if (element instanceof PsiReferenceExpression) {
      final PsiElement psiElement = ((PsiReferenceExpression)element).resolve();
      if (psiElement instanceof PsiVariableEx) {
        final Object val = ((PsiVariableEx)psiElement).computeConstantValue(new HashSet<PsiVariable>());
        if (val instanceof String) {
          return (String)val;
        }
      }
    }
    return null;
  }

  public boolean canHave15Suppressions(@NotNull final PsiElement file) {
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) return false;
    final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
    if (jdk == null) return false;
    final String jdkVersion = jdk.getVersionString();
    if (jdkVersion == null) return false;
    final boolean is_1_5 = JavaSdk.getInstance().compareTo(jdkVersion, "1.5") >= 0;
    return DaemonCodeAnalyzerSettings.getInstance().SUPPRESS_WARNINGS && is_1_5 && PsiUtil.isLanguageLevel5OrHigher(file);
  }

  public boolean alreadyHas14Suppressions(@NotNull final PsiDocCommentOwner commentOwner) {
    final PsiDocComment docComment = commentOwner.getDocComment();
    return docComment != null && docComment.findTagByName(SuppressionUtil.SUPPRESS_INSPECTIONS_TAG_NAME) != null;
  }
}
