/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author max
 */
public class I18nUtil {
  private I18nUtil() {
  }

  public static boolean mustBePropertyKey(final PsiLiteralExpression expression, final Map<String, Object> annotationAttributeValues) {
    return isPassedToAnnotatedParam(expression, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues, null);
  }

  public static boolean isPassedToAnnotatedParam(PsiExpression expression,
                                                 final String annFqn,
                                                 final Map<String, Object> annotationAttributeValues,
                                                 @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    expression = getToplevelExpression(expression);
    final PsiElement parent = expression.getParent();

    if (parent instanceof PsiExpressionList) {
      int idx = -1;
      final PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
      for (int i = 0; i < args.length; i++) {
        PsiExpression arg = args[i];
        if (PsiTreeUtil.isAncestor(arg, expression, false)) {
          idx = i;
          break;
        }
      }
      if (idx == -1) return false;

      PsiElement grParent = parent.getParent();

      if (grParent instanceof PsiAnonymousClass) {
        grParent = grParent.getParent();
      }

      if (grParent instanceof PsiCall) {
        PsiMethod method = ((PsiCall)grParent).resolveMethod();
        if (method != null) {
          if (isMethodParameterAnnotatedWith(method, idx, new HashSet<PsiMethod>(), annFqn,
                                             annotationAttributeValues, nonNlsTargets)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  public static PsiExpression getToplevelExpression(PsiExpression expression) {
    while (expression.getParent() instanceof PsiExpression) {
      expression = (PsiExpression)expression.getParent();
      if (expression instanceof PsiAssignmentExpression) break;
    }
    return expression;
  }

  public static boolean isMethodParameterAnnotatedWith(final PsiMethod method,
                                                       final int idx,
                                                       Collection<PsiMethod> processed,
                                                       final String annFqn,
                                                       Map<String, Object> annotationAttributeValues,
                                                       @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    if (processed.contains(method)) return false;
    processed.add(method);


    final PsiParameter[] params = method.getParameterList().getParameters();
    PsiParameter param;
    if (idx >= params.length) {
      if (params.length == 0) {
        return false;
      }
      PsiParameter lastParam = params [params.length-1];
      if (lastParam.isVarArgs()) {
        param = lastParam;
      }
      else {
        return false;
      }
    }
    else {
      param = params[idx];
    }
    final PsiAnnotation[] annotations = param.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      final String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName != null && annFqn.equals(qualifiedName)) {
        final PsiAnnotationParameterList parameterList = annotation.getParameterList();
        final PsiNameValuePair[] attributes = parameterList.getAttributes();
        for (PsiNameValuePair attribute : attributes) {
          final String name = attribute.getName();
          if (annotationAttributeValues.containsKey(name)) {
            annotationAttributeValues.put(name, attribute.getValue());
          }
        }
        return true;
      }
    }
    if (nonNlsTargets != null) {
      nonNlsTargets.add(param);
    }

    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      if (isMethodParameterAnnotatedWith(superMethod, idx, processed, annFqn, annotationAttributeValues, null)) return true;
    }

    return false;
  }

  public static boolean isValidPropertyReference(PsiLiteralExpression expression, final String key, String[] outResourceBundle) {
    final HashMap<String, Object> annotationAttributeValues = new HashMap<String, Object>();
    annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
    if (mustBePropertyKey(expression, annotationAttributeValues)) {
      final Object resourceBundleName = annotationAttributeValues.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
      if (!(resourceBundleName instanceof PsiExpression)) {
        return false;
      }
      PsiExpression expr = (PsiExpression)resourceBundleName;
      final Object value = expr.getManager().getConstantEvaluationHelper().computeConstantExpression(expr);
      if (value == null) {
        return false;
      }
      String bundleName = value.toString();
      outResourceBundle[0] = bundleName;
      return isPropertyRef(expression, key, bundleName);
    }
    else {
      return true;
    }
  }

  public static boolean isPropertyRef(final PsiLiteralExpression expression, final String value, final String resourceBundleName) {
    if (resourceBundleName != null) {
      final PropertiesFile propertiesFile = propertiesFileByBundleName(expression, resourceBundleName);
      if (propertiesFile == null) return false;
      return propertiesFile.findPropertyByKey(value) != null;
    }
    else {
      return !PropertiesUtil.findPropertiesByKey(expression.getProject(), value).isEmpty();
    }
  }

  @Nullable
  public static PropertiesFile propertiesFileByBundleName(final PsiElement context, final String resourceBundleName) {
    List<PropertiesFile> propertiesFiles = propertiesFilesByBundleName(context, resourceBundleName);
    return propertiesFiles.size() == 0 ? null : propertiesFiles.get(0);
  }

  @NotNull
  public static List<PropertiesFile> propertiesFilesByBundleName(final PsiElement context, final String resourceBundleName) {
    final PsiFile containingFile = context.getContainingFile();
    VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) {
      final PsiFile originalFile = containingFile.getOriginalFile();
      if (originalFile != null) {
        virtualFile = originalFile.getVirtualFile();
      }
    }
    if (virtualFile != null) {
      final Module module = ProjectRootManager.getInstance(context.getProject()).getFileIndex().getModuleForFile(virtualFile);
      if (module != null) {
        PropertiesReferenceManager refManager = context.getProject().getComponent(PropertiesReferenceManager.class);
        List<PropertiesFile> propFiles = refManager.findPropertiesFiles(module, resourceBundleName);
        if (propFiles.size() > 0) {
          return propFiles;
        }
      }
    }
    return Collections.emptyList();
  }
}
