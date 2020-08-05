// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class CaptureSettingsProvider {
  private static final Logger LOG = Logger.getInstance(CaptureSettingsProvider.class);

  private static final KeyProvider THIS_KEY = new StringKeyProvider("this");
  private static final String ANY = "*";

  @NotNull
  public static Properties getPointsProperties(@Nullable Project project) {
    Properties res = new Properties();
    if (Registry.is("debugger.capture.points.agent.annotations")) {
      int idx = 0;
      for (CaptureSettingsProvider.AgentPoint point : getAnnotationPoints(project)) {
        res.setProperty((point.isCapture() ? "capture" : "insert") + idx++,
                        point.myClassName + AgentPoint.SEPARATOR +
                        point.myMethodName + AgentPoint.SEPARATOR +
                        point.myMethodDesc + AgentPoint.SEPARATOR +
                        point.myKey.asString());
      }
    }
    return res;
  }

  private static List<AgentPoint> getAnnotationPoints(@Nullable Project project) {
    return ReadAction.compute(() -> {
      List<AgentPoint> annotationPoints = new ArrayList<>();
      CaptureConfigurable.processCaptureAnnotations(project, (capture, e, annotation) -> {
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
        String classVMName = JVMNameUtil.getClassVMName(method.getContainingClass());
        if (classVMName == null) {
          LOG.warn("Unable to find VM class name for annotated method: " + method.getName());
          return;
        }
        String className = classVMName.replaceAll("\\.", "/");
        String methodName = JVMNameUtil.getJVMMethodName(method);
        String methodDesc = ANY;
        try {
          methodDesc = JVMNameUtil.getJVMSignature(method).getName(null);
        }
        catch (EvaluateException ex) {
          LOG.error(ex);
        }

        PsiAnnotationMemberValue keyExpressionValue = annotation.findAttributeValue("keyExpression");
        if (keyExpressionValue != null && !"\"\"".equals(keyExpressionValue.getText())) {
          keyProvider = new FieldKeyProvider(className, StringUtil.unquoteString(keyExpressionValue.getText())); //treat as a field
        }
        AgentPoint point = capture ?
                           new AgentCapturePoint(className, methodName, methodDesc, keyProvider) :
                           new AgentInsertPoint(className, methodName, methodDesc, keyProvider);
        annotationPoints.add(point);
      });
      return annotationPoints;
    });
  }

  private static abstract class AgentPoint {
    public final String myClassName;
    public final String myMethodName;
    public final String myMethodDesc;
    public final KeyProvider myKey;

    private static final String SEPARATOR = " ";

    AgentPoint(String className, String methodName, String methodDesc, KeyProvider key) {
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

  private static class AgentCapturePoint extends AgentPoint {
    AgentCapturePoint(String className, String methodName, String methodDesc, KeyProvider key) {
      super(className, methodName, methodDesc, key);
    }

    @Override
    public boolean isCapture() {
      return true;
    }
  }

  private static class AgentInsertPoint extends AgentPoint {
    AgentInsertPoint(String className, String methodName, String methodDesc, KeyProvider key) {
      super(className, methodName, methodDesc, key);
    }

    @Override
    public boolean isCapture() {
      return false;
    }
  }

  private interface KeyProvider {
    String asString();
  }

  private static KeyProvider param(int idx) {
    return new StringKeyProvider(Integer.toString(idx));
  }

  private static class StringKeyProvider implements KeyProvider {
    private final String myValue;

    StringKeyProvider(String value) {
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

    FieldKeyProvider(String className, String fieldName) {
      myClassName = className;
      myFieldName = fieldName;
    }

    @Override
    public String asString() {
      return myClassName + AgentPoint.SEPARATOR + myFieldName;
    }
  }
}
