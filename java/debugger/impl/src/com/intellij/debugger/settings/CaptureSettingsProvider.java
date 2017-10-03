// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.debugger.jdi.DecompiledLocalVariable;
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

  static {
    CAPTURE_POINTS.add(new AgentCapturePoint("javax/swing/SwingUtilities", "invokeLater", new StringKeyProvider("0")));
    INSERT_POINTS.add(new AgentInsertPoint("java/awt/event/InvocationEvent", "dispatch",
                                           new FieldKeyProvider("java/awt/event/InvocationEvent", "runnable", "Ljava/lang/Runnable;")));

    CAPTURE_POINTS.add(new AgentCapturePoint("java/lang/Thread", "start", THIS_KEY));
    INSERT_POINTS.add(new AgentInsertPoint("java/lang/Thread", "run", THIS_KEY));

    CAPTURE_POINTS.add(new AgentCapturePoint("java/util/concurrent/ExecutorService", "submit", new StringKeyProvider("1")));
    INSERT_POINTS.add(new AgentInsertPoint("java/util/concurrent/Executors$RunnableAdapter", "call",
                                           new FieldKeyProvider("java/util/concurrent/Executors$RunnableAdapter",
                                                                "task",
                                                                "Ljava/lang/Runnable;")));

    CAPTURE_POINTS.add(new AgentCapturePoint("java/util/concurrent/ThreadPoolExecutor", "execute", new StringKeyProvider("1")));
    INSERT_POINTS.add(new AgentInsertPoint("java/util/concurrent/FutureTask", "run", THIS_KEY));

    CAPTURE_POINTS.add(new AgentCapturePoint("java/util/concurrent/CompletableFuture", "supplyAsync", new StringKeyProvider("0")));

    CapturePoint ideInsertPoint = new CapturePoint();
    ideInsertPoint.myInsertClassName = "java.util.concurrent.CompletableFuture$AsyncSupply";
    ideInsertPoint.myInsertMethodName = "run$$$capture";
    ideInsertPoint.myInsertKeyExpression = "f";
    INSERT_POINTS.add(new AgentInsertPoint("java/util/concurrent/CompletableFuture$AsyncSupply", "run",
                                           new FieldKeyProvider("java/util/concurrent/CompletableFuture$AsyncSupply",
                                                                "fn",
                                                                "Ljava/util/function/Supplier;"),
                                           ideInsertPoint));

    CAPTURE_POINTS.add(new AgentCapturePoint("java/util/concurrent/CompletableFuture", "runAsync", new StringKeyProvider("0")));
    ideInsertPoint = new CapturePoint();
    ideInsertPoint.myInsertClassName = "java.util.concurrent.CompletableFuture$AsyncRun";
    ideInsertPoint.myInsertMethodName = "run$$$capture";
    ideInsertPoint.myInsertKeyExpression = "f";
    INSERT_POINTS.add(new AgentInsertPoint("java/util/concurrent/CompletableFuture$AsyncRun",
                                           "run",
                                           new FieldKeyProvider("java/util/concurrent/CompletableFuture$AsyncRun",
                                                                "fn",
                                                                "Ljava/lang/Runnable;"),
                                           ideInsertPoint));

    CAPTURE_POINTS.add(new AgentCapturePoint("java/util/concurrent/CompletableFuture", "thenAcceptAsync", new StringKeyProvider("1")));
    INSERT_POINTS.add(new AgentInsertPoint("java/util/concurrent/CompletableFuture$UniAccept", "tryFire",
                                           new FieldKeyProvider("java/util/concurrent/CompletableFuture$UniAccept",
                                                                "fn",
                                                                "Ljava/util/function/Consumer;")));

    CAPTURE_POINTS.add(new AgentCapturePoint("java/util/concurrent/CompletableFuture", "thenRunAsync", new StringKeyProvider("1")));
    INSERT_POINTS.add(new AgentInsertPoint("java/util/concurrent/CompletableFuture$UniRun", "tryFire",
                                           new FieldKeyProvider("java/util/concurrent/CompletableFuture$UniRun",
                                                                "fn",
                                                                "Ljava/lang/Runnable;")));

    IDE_INSERT_POINTS = StreamEx.of(INSERT_POINTS).map(p -> p.myInsertPoint).nonNull().toList();
  }

  public static List<AgentPoint> getCapturePoints() {
    return Collections.unmodifiableList(CAPTURE_POINTS);
  }

  public static List<AgentPoint> getInsertPoints() {
    return Collections.unmodifiableList(INSERT_POINTS);
  }

  public static List<CapturePoint> getIdeInsertPoints() {
    return Collections.unmodifiableList(IDE_INSERT_POINTS);
  }

  public static class AgentPoint {
    public final String myClassName;
    public final String myMethodName;
    public final KeyProvider myKey;

    public static final String SEPARATOR = " ";

    public AgentPoint(String className, String methodName, KeyProvider key) {
      myClassName = className;
      myMethodName = methodName;
      myKey = key;
    }
  }

  public static class AgentCapturePoint extends AgentPoint {
    public AgentCapturePoint(String className, String methodName, KeyProvider key) {
      super(className, methodName, key);
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

    public AgentInsertPoint(String className, String methodName, KeyProvider key, CapturePoint point) {
      super(className, methodName, key);
      this.myInsertPoint = point;
    }
  }

  public interface KeyProvider {
    String asString();
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
}
