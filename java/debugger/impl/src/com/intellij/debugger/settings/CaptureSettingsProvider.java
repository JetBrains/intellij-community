/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.settings;

import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.jdi.DecompiledLocalVariable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author egor
 */
public class CaptureSettingsProvider {
  private static final List<AgentCapturePoint> CAPTURE_POINTS = new ArrayList<>();
  private static final List<AgentInsertPoint> INSERT_POINTS = new ArrayList<>();
  private static final List<CapturePoint> IDE_INSERT_POINTS;

  private static final KeyProvider THIS_KEY = new StringKeyProvider("this");
  private static final KeyProvider FIRST_PARAM = param(0);

  static {
    addCapture("javax/swing/SwingUtilities", "invokeLater", FIRST_PARAM);
    addInsert("java/awt/event/InvocationEvent",
              "dispatch",
              new FieldKeyProvider("java/awt/event/InvocationEvent", "runnable", "Ljava/lang/Runnable;"));

    addCapture("java/lang/Thread", "start", THIS_KEY);
    addInsert("java/lang/Thread", "run", THIS_KEY);

    addCapture("java/util/concurrent/ExecutorService", "submit", FIRST_PARAM);
    addInsert("java/util/concurrent/Executors$RunnableAdapter",
              "call",
              new FieldKeyProvider("java/util/concurrent/Executors$RunnableAdapter",
                                   "task",
                                   "Ljava/lang/Runnable;"));

    addCapture("java/util/concurrent/ThreadPoolExecutor", "execute", FIRST_PARAM);
    addInsert("java/util/concurrent/FutureTask", "run", THIS_KEY);

    addCapture("java/util/concurrent/CompletableFuture", "supplyAsync", FIRST_PARAM);
    AgentInsertPoint point = new AgentInsertPoint("java/util/concurrent/CompletableFuture$AsyncSupply",
                                                  "run",
                                                  new FieldKeyProvider("java/util/concurrent/CompletableFuture$AsyncSupply",
                                                                       "fn",
                                                                       "Ljava/util/function/Supplier;"));
    point.myInsertPoint.myInsertMethodName = "run$$$capture";
    point.myInsertPoint.myInsertKeyExpression = "f";
    INSERT_POINTS.add(point);

    addCapture("java/util/concurrent/CompletableFuture", "runAsync", FIRST_PARAM);
    point = new AgentInsertPoint("java/util/concurrent/CompletableFuture$AsyncRun",
                                 "run",
                                 new FieldKeyProvider("java/util/concurrent/CompletableFuture$AsyncRun",
                                                      "fn",
                                                      "Ljava/lang/Runnable;"));
    point.myInsertPoint.myInsertMethodName = "run$$$capture";
    point.myInsertPoint.myInsertKeyExpression = "f";
    INSERT_POINTS.add(point);

    addCapture("java/util/concurrent/CompletableFuture", "thenAcceptAsync", FIRST_PARAM);
    addInsert("java/util/concurrent/CompletableFuture$UniAccept",
              "tryFire",
              new FieldKeyProvider("java/util/concurrent/CompletableFuture$UniAccept",
                                   "fn",
                                   "Ljava/util/function/Consumer;"));

    addCapture("java/util/concurrent/CompletableFuture", "thenRunAsync", FIRST_PARAM);
    addInsert("java/util/concurrent/CompletableFuture$UniRun",
              "tryFire",
              new FieldKeyProvider("java/util/concurrent/CompletableFuture$UniRun",
                                   "fn",
                                   "Ljava/lang/Runnable;"));

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

    IDE_INSERT_POINTS = StreamEx.of(INSERT_POINTS).map(p -> p.myInsertPoint).nonNull().toList();
  }

  public static List<AgentPoint> getPoints() {
    List<AgentPoint> res = ContainerUtil.concat(CAPTURE_POINTS, INSERT_POINTS);
    if (Registry.is("debugger.capture.points.agent.annotations")) {
      res = ContainerUtil.concat(res, getAnnotationPoints());
    }
    return res;
  }

  public static List<CapturePoint> getIdeInsertPoints() {
    List<CapturePoint> res = Collections.unmodifiableList(IDE_INSERT_POINTS);
    if (Registry.is("debugger.capture.points.agent.annotations")) {
      res = ContainerUtil.concat(
        res, StreamEx.of(getAnnotationPoints()).select(AgentInsertPoint.class).map(p -> p.myInsertPoint).nonNull().toList());
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
        PsiModifierList modifierList = e.getModifierList();
        if (modifierList != null) {
          PsiAnnotation annotation = modifierList.findAnnotation(CaptureConfigurable.getAnnotationName(capture));
          if (annotation != null) {
            PsiAnnotationMemberValue keyExpressionValue = annotation.findAttributeValue("keyExpression");
            if (keyExpressionValue != null && !"\"\"".equals(keyExpressionValue.getText())) {
              return; //skip for now
            }
          }
        }
        String className = JVMNameUtil.getNonAnonymousClassName(method.getContainingClass()).replaceAll("\\.", "/");
        String methodName = JVMNameUtil.getJVMMethodName(method);
        AgentPoint point =
          capture ? new AgentCapturePoint(className, methodName, keyProvider) : new AgentInsertPoint(className, methodName, keyProvider);
        annotationPoints.add(point);
      });
      return annotationPoints;
    });
  }

  public static abstract class AgentPoint {
    public final String myClassName;
    public final String myMethodName;
    public final KeyProvider myKey;

    public static final String SEPARATOR = " ";

    public AgentPoint(String className, String methodName, KeyProvider key) {
      assert !className.contains(".") : "Classname should not contain . here";
      myClassName = className;
      myMethodName = methodName;
      myKey = key;
    }

    public abstract boolean isCapture();
  }

  public static class AgentCapturePoint extends AgentPoint {
    public AgentCapturePoint(String className, String methodName, KeyProvider key) {
      super(className, methodName, key);
    }

    @Override
    public boolean isCapture() {
      return true;
    }
  }

  public static class AgentInsertPoint extends AgentPoint {
    public final CapturePoint myInsertPoint; // for IDE

    public AgentInsertPoint(String className, String methodName, KeyProvider key) {
      super(className, methodName, key);
      this.myInsertPoint = new CapturePoint();
      myInsertPoint.myInsertClassName = className.replaceAll("/", ".");
      myInsertPoint.myInsertMethodName = methodName;
      if (myKey instanceof FieldKeyProvider) {
        myInsertPoint.myInsertKeyExpression = ((FieldKeyProvider)myKey).myFieldName;
      }
      else {
        String keyStr = key.asString();
        try {
          myInsertPoint.myInsertKeyExpression = DecompiledLocalVariable.PARAM_PREFIX + Integer.parseInt(keyStr);
        }
        catch (NumberFormatException ignored) {
          myInsertPoint.myInsertKeyExpression = keyStr;
        }
      }
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
    private final String myFieldDesc;

    public FieldKeyProvider(String className, String fieldName, String fieldDesc) {
      myClassName = className;
      myFieldName = fieldName;
      myFieldDesc = fieldDesc;
    }

    @Override
    public String asString() {
      return myClassName + AgentPoint.SEPARATOR + myFieldName + AgentPoint.SEPARATOR + myFieldDesc;
    }
  }

  private static void addCapture(String className, String methodName, KeyProvider key) {
    CAPTURE_POINTS.add(new AgentCapturePoint(className, methodName, key));
  }

  private static void addInsert(String className, String methodName, KeyProvider key) {
    INSERT_POINTS.add(new AgentInsertPoint(className, methodName, key));
  }
}
