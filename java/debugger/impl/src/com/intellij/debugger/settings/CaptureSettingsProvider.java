// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class CaptureSettingsProvider {
  private static final Logger LOG = Logger.getInstance(CaptureSettingsProvider.class);

  private static final List<AgentCapturePoint> CAPTURE_POINTS = new ArrayList<>();
  private static final List<AgentInsertPoint> INSERT_POINTS = new ArrayList<>();

  private static final KeyProvider THIS_KEY = new StringKeyProvider("this");
  private static final KeyProvider FIRST_PARAM = param(0);
  private static final String ANY = "*";

  static {
    addCapture("java/awt/event/InvocationEvent", "<init>", THIS_KEY);
    addInsert("java/awt/event/InvocationEvent", "dispatch", THIS_KEY);

    addCapture("java/lang/Thread", "start", THIS_KEY);
    addInsert("java/lang/Thread", "run", THIS_KEY);

    addCapture("java/util/concurrent/FutureTask", "<init>", THIS_KEY);
    addInsert("java/util/concurrent/FutureTask", "run", THIS_KEY);
    addInsert("java/util/concurrent/FutureTask", "runAndReset", THIS_KEY);

    addCapture("java/util/concurrent/CompletableFuture$AsyncSupply", "<init>", THIS_KEY);
    addInsert("java/util/concurrent/CompletableFuture$AsyncSupply", "run", THIS_KEY);

    addCapture("java/util/concurrent/CompletableFuture$AsyncRun", "<init>", THIS_KEY);
    addInsert("java/util/concurrent/CompletableFuture$AsyncRun", "run", THIS_KEY);

    addCapture("java/util/concurrent/CompletableFuture$UniAccept", "<init>", THIS_KEY);
    addInsert("java/util/concurrent/CompletableFuture$UniAccept", "tryFire", THIS_KEY);

    addCapture("java/util/concurrent/CompletableFuture$UniRun", "<init>", THIS_KEY);
    addInsert("java/util/concurrent/CompletableFuture$UniRun", "tryFire", THIS_KEY);

    // netty
    addCapture("io/netty/util/concurrent/SingleThreadEventExecutor", "addTask", FIRST_PARAM);
    addInsert("io/netty/util/concurrent/AbstractEventExecutor", "safeExecute", FIRST_PARAM);

    // scala
    addCapture("scala/concurrent/impl/Future$PromiseCompletingRunnable", "<init>", THIS_KEY);
    addInsert("scala/concurrent/impl/Future$PromiseCompletingRunnable", "run", THIS_KEY);

    addCapture("scala/concurrent/impl/CallbackRunnable", "<init>", THIS_KEY);
    addInsert("scala/concurrent/impl/CallbackRunnable", "run", THIS_KEY);

    // akka-scala
    addCapture("akka/actor/ScalaActorRef", "$bang", FIRST_PARAM);
    addCapture("akka/actor/RepointableActorRef", "$bang", FIRST_PARAM);
    addCapture("akka/actor/LocalActorRef", "$bang", FIRST_PARAM);
    addInsert("akka/actor/Actor$class", "aroundReceive", param(2));

    // JavaFX
    addCapture("com/sun/glass/ui/InvokeLaterDispatcher", "invokeLater", FIRST_PARAM);
    addInsert("com/sun/glass/ui/InvokeLaterDispatcher$Future", "run",
              new FieldKeyProvider("com/sun/glass/ui/InvokeLaterDispatcher$Future", "runnable"));
  }

  public static List<AgentPoint> getPoints() {
    List<AgentPoint> res = ContainerUtil.concat(CAPTURE_POINTS, INSERT_POINTS);
    if (Registry.is("debugger.capture.points.agent.annotations")) {
      res = ContainerUtil.concat(res, getAnnotationPoints());
    }
    return res;
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

  private static void addCapture(String className, String methodName, KeyProvider key) {
    CAPTURE_POINTS.add(new AgentCapturePoint(className, methodName, ANY, key));
  }

  private static void addInsert(String className, String methodName, KeyProvider key) {
    INSERT_POINTS.add(new AgentInsertPoint(className, methodName, ANY, key));
  }
}
