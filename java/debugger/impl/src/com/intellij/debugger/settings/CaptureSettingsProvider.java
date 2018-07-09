// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author egor
 */
public class CaptureSettingsProvider {
  private static final Logger LOG = Logger.getInstance(CaptureSettingsProvider.class);

  private static final KeyProvider THIS_KEY = new StringKeyProvider("this");
  private static final String ANY = "*";

  public static List<AgentPoint> getPoints() {
    if (Registry.is("debugger.capture.points.agent.annotations")) {
      return getAnnotationPoints();
    }
    return Collections.emptyList();
  }

  private static List<AgentPoint> getAnnotationPoints() {
    return ReadAction.compute(() -> {
      List<AgentPoint> annotationPoints = new ArrayList<>();
      CaptureConfigurable.processCaptureAnnotations((capture, e) -> {
        PsiMethod method;
        KeyProvider keyProvider;
        if (e instanceof PsiMethod) {
          method = (PsiMethod)e;
          keyProvider = THIS_KEY;
        }
        else if (e instanceof PsiParameter) {
          PsiParameter psiParameter = (PsiParameter)e;
          method = (PsiMethod)psiParameter.getDeclarationScope();
          keyProvider = param(method.getParameterList().getParameterIndex(psiParameter));
        }
        else {
          return;
        }
        String className = JVMNameUtil.getNonAnonymousClassName(method.getContainingClass()).replaceAll("\\.", "/");
        String methodName = JVMNameUtil.getJVMMethodName(method);
        String methodDesc = ANY;
        try {
          methodDesc = JVMNameUtil.getJVMSignature(method).getName(null);
        }
        catch (EvaluateException ex) {
          LOG.error(ex);
        }

        PsiModifierList modifierList = e.getModifierList();
        if (modifierList != null) {
          PsiAnnotation annotation = modifierList.findAnnotation(CaptureConfigurable.getAnnotationName(capture));
          if (annotation != null) {
            PsiAnnotationMemberValue keyExpressionValue = annotation.findAttributeValue("keyExpression");
            if (keyExpressionValue != null && !"\"\"".equals(keyExpressionValue.getText())) {
              keyProvider = new FieldKeyProvider(className, StringUtil.unquoteString(keyExpressionValue.getText())); //treat as a field
            }
          }
        }
        AgentPoint point = capture ?
                           new AgentCapturePoint(className, methodName, methodDesc, keyProvider) :
                           new AgentInsertPoint(className, methodName, methodDesc, keyProvider);
        annotationPoints.add(point);
      });
      return annotationPoints;
    });
  }

  public static abstract class AgentPoint {
    public final String myClassName;
    public final String myMethodName;
    public final String myMethodDesc;
    public final KeyProvider myKey;

    public static final String SEPARATOR = " ";

    public AgentPoint(String className, String methodName, String methodDesc, KeyProvider key) {
      assert !className.contains(".") : "Classname should not contain . here";
      myClassName = className;
      myMethodName = methodName;
      myMethodDesc = methodDesc;
      myKey = key;
    }

    public abstract boolean isCapture();

    @Override
    public String toString() {
      return myClassName + "." + myMethodName + " " + myKey.asString();
    }
  }

  public static class AgentCapturePoint extends AgentPoint {
    public AgentCapturePoint(String className, String methodName, String methodDesc, KeyProvider key) {
      super(className, methodName, methodDesc, key);
    }

    @Override
    public boolean isCapture() {
      return true;
    }
  }

  public static class AgentInsertPoint extends AgentPoint {
    public AgentInsertPoint(String className, String methodName, String methodDesc, KeyProvider key) {
      super(className, methodName, methodDesc, key);
    }

    @Override
    public boolean isCapture() {
      return false;
    }
  }

  public interface KeyProvider {
    String asString();
  }
 
  private static KeyProvider param(int idx) {
    return new StringKeyProvider(Integer.toString(idx));
  }

  private static class StringKeyProvider implements KeyProvider {
    private final String myValue;

    public StringKeyProvider(String value) {
      myValue = value;
    }

    @Override
    public String asString() {
      return myValue;
    }
  }

  private static class FieldKeyProvider implements KeyProvider {
    private final String myClassName;
    private final String myFieldName;

    public FieldKeyProvider(String className, String fieldName) {
      myClassName = className;
      myFieldName = fieldName;
    }

    @Override
    public String asString() {
      return myClassName + AgentPoint.SEPARATOR + myFieldName;
    }
  }
}
