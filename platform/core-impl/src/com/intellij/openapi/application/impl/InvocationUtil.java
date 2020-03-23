// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InvocationEvent;
import java.lang.reflect.Field;

import static com.intellij.util.ReflectionUtil.findAssignableField;
import static com.intellij.util.ReflectionUtil.getFieldValue;

@ApiStatus.Internal
public final class InvocationUtil {

  public static final @NotNull Class<? extends Runnable> FLUSH_NOW_CLASS =
    FlushQueue.FlushNow.class;
  public static final @NotNull Class<? extends Runnable> REPAINT_PROCESSING_CLASS =
    findProcessingClass();
  private static final @NotNull Field INVOCATION_EVENT_RUNNABLE_FIELD =
    findRunnableField();

  private InvocationUtil() {}

  public static @Nullable Runnable extractRunnable(@NotNull AWTEvent event) {
    return event instanceof InvocationEvent ?
           getFieldValue(INVOCATION_EVENT_RUNNABLE_FIELD, event) :
           null;
  }

  private static @NotNull Class<? extends Runnable> findProcessingClass() {
    try {
      return Class.forName(
        "javax.swing.RepaintManager$ProcessingRunnable",
        false,
        InvocationUtil.class.getClassLoader()
      ).asSubclass(Runnable.class);
    }
    catch (ClassNotFoundException e) {
      throw new InternalAPIChangedException(RepaintManager.class, e);
    }
  }

  private static @NotNull Field findRunnableField() {
    try {
      return findAssignableField(
        InvocationEvent.class,
        Runnable.class,
        "runnable"
      );
    }
    catch (NoSuchFieldException e) {
      throw new InternalAPIChangedException(InvocationEvent.class, e);
    }
  }

  private static final class InternalAPIChangedException extends RuntimeException {

    InternalAPIChangedException(@NotNull Class<?> targetClass,
                                @Nullable ReflectiveOperationException cause) {
      super(targetClass + " class internal API has been changed", cause);
    }
  }
}
