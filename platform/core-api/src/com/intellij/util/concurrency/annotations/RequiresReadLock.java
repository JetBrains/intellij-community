// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency.annotations;

import com.intellij.openapi.application.ReadAction;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.ThreadingAssertions;

import java.lang.annotation.*;

/**
 * Methods (including constructors) annotated with {@code @RequiresReadLock} must be called with read or write lock held.
 * <p>
 * Parameters annotated with {@code @RequiresReadLock} must be callables and are guaranteed to be called with read or write lock held.
 * <h2>Instrumentation</h2>
 * Aside from a documentation purpose, this annotation is processed by {@link org.jetbrains.jps.devkit.threadingModelHelper} from
 * the <a href="https://plugins.jetbrains.com/plugin/22851-plugin-devkit">DevKit plugin</a>.
 * The plugin instruments annotated elements with {@link ThreadingAssertions#assertReadAccess()} calls
 * to ensure annotation's contract is not violated at runtime. The instrumentation can be disabled
 * by setting {@link RequiresReadLock#generateAssertion()} to {@code false}.
 * <p>
 * <b>Important:</b> the instrumentation has limitations. Please read the docs
 * of the {@link org.jetbrains.jps.devkit.threadingModelHelper} to learn about them.
 *
 * <h2>Q&A</h2>
 * <p>
 * Q: When implementing or overriding a method annotated with {@code @RequiresReadLock},
 * should I also annotate the overriding method with {@code @RequiresReadLock}?
 * <p>
 * A: There is no definite answer.
 * The overriding class can be more specific, and its methods can have weaker (or stronger) contracts than the parent method.
 * In any case, it is strongly advised to document the threading contract of the overriding method.
 * <p>
 * The absence of a concurrency annotation also conveys information that a method does not require a specific thread,
 * and this may be more valuable than an overly pessimistic annotation.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/threading-model.html">Threading Model</a>
 * @see ThreadingAssertions#assertReadAccess()
 * @see ReadAction#run(ThrowableRunnable)
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
public @interface RequiresReadLock {
  /**
   * @return {@code false} if the annotated element must not be instrumented with the assertion.
   */
  boolean generateAssertion() default true;
}
