// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency.annotations;

import com.intellij.openapi.application.ReadAction;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.ThreadingAssertions;

import java.lang.annotation.*;

/**
 * Methods and constructors annotated with {@code RequiresReadLock} must be called with read or write lock held.
 * Parameters annotated with {@code RequiresReadLock} must be callables and are guaranteed to be called with read or write lock held.
 * <p/>
 * Aside from a documentation purpose, the annotation is processed by the {@link org.jetbrains.jps.devkit.threadingModelHelper}.
 * The plugin instruments annotated elements with {@link ThreadingAssertions#assertReadAccess()} calls
 * to ensure annotation's contract is not violated at runtime. The instrumentation can be disabled
 * by setting {@link RequiresReadLock#generateAssertion()} to {@code false}.
 * <p/>
 * <b>Important:</b> the instrumentation has limitations. Please read the docs
 * of the {@link org.jetbrains.jps.devkit.threadingModelHelper} to learn about them.
 *
 * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html">General Threading Rules</a>
 * @see ThreadingAssertions#assertReadAccess()
 * @see ReadAction#run(ThrowableRunnable)
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
public @interface RequiresReadLock {
  /**
   * @return {@code false} if annotated element must not be instrumented with the assertion.
   */
  boolean generateAssertion() default true;
}
