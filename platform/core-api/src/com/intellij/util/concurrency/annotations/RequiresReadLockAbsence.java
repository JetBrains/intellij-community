/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.concurrency.annotations;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ReadAction;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Methods and constructors annotated with {@code RequiresNoReadLock} must be called without read lock held.
 * Parameters annotated with {@code RequiresNoReadLock} must be callables and are guaranteed to be called without read lock held.
 *
 * <p/>Aside from a documentation purpose, the annotation is processed by the {@link org.jetbrains.jps.devkit.threadingModelHelper}.
 * The plugin instruments annotated elements with {@link Application#assertReadAccessNotAllowed()} calls
 * to ensure annotation's contract is not violated at runtime. The instrumentation can be disabled
 * by setting {@link RequiresReadLockAbsence#generateAssertion()} to {@code false}.
 *
 * <p/> <b>Important:</b> the instrumentation has limitations. Please read the docs
 * of the {@link org.jetbrains.jps.devkit.threadingModelHelper} to learn about them.
 *
 * @see <a href="http://www.jetbrains.org/intellij/sdk/docs/basics/architectural_overview/general_threading_rules.html">General Threading Rules</a>
 * @see Application#assertReadAccessNotAllowed()
 * @see ReadAction#run(ThrowableRunnable)
 */
@ApiStatus.Experimental
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
public @interface RequiresReadLockAbsence {
  /**
   * @return {@code false} if annotated element must not be instrumented with the assertion.
   */
  boolean generateAssertion() default true;
}
