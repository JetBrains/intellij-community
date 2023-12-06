// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency.annotations;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.progress.Task;

import java.lang.annotation.*;

/**
 * Methods and constructors annotated with {@code RequiresBackgroundThread} must be called from some thread
 * that is not the Event Dispatch Thread.
 * Parameters annotated with {@code RequiresBackgroundThread} must be callables and are guaranteed to be called from some thread
 * that is not the Event Dispatch Thread.
 * <p/>
 * Aside from a documentation purpose, the annotation is processed by the {@link org.jetbrains.jps.devkit.threadingModelHelper}.
 * The plugin instruments annotated elements with {@link Application#assertIsNonDispatchThread()} calls
 * to ensure annotation's contract is not violated at runtime. The instrumentation can be disabled
 * by setting {@link RequiresBackgroundThread#generateAssertion()} to {@code false}.
 * <p/>
 * <b>Important:</b> the instrumentation has limitations. Please read the docs
 * of the {@link org.jetbrains.jps.devkit.threadingModelHelper} to learn about them.
 *
 * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html">General Threading Rules</a>
 * @see Application#assertIsNonDispatchThread()
 * @see Application#executeOnPooledThread(Runnable)
 * @see Task.Backgroundable
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
public @interface RequiresBackgroundThread {
  /**
   * @return {@code false} if annotated element must not be instrumented with the assertion.
   */
  boolean generateAssertion() default true;
}
