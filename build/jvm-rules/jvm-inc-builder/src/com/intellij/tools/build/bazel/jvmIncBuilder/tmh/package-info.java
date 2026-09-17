// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * <h2>Threading Model Helper</h2>
 * <p>
 * Instruments methods (including constructors) annotated with threading annotations by inserting a call to a corresponding assertion method.
 * This call becomes the first instruction in the method's body.
 * <ul>
 * <li>{@code @RequiresEdt} &rarr; {@code ThreadingAssertions.assertEventDispatchThread()}
 * <li>{@code @RequiresBackgroundThread} &rarr; {@code ThreadingAssertions.assertBackgroundThread()}
 * <li>{@code @RequiresReadLock} &rarr; {@code ThreadingAssertions.assertReadAccess()}
 * <li>{@code @RequiresWriteLock} &rarr; {@code ThreadingAssertions.assertWriteAccess()}
 * <li>{@code @RequiresReadLockAbsence} &rarr; {@code Application.assertNoReadAccess()}
 * </ul>
 * <p>
 * To disable the instrumentation, use the {@code tmh.generate.assertions.for.annotations} key in the Registry.
 * <h3>Limitations</h3>
 * <ul>
 * <li>Kotlin instrumentation produces soft asserts.</li>
 * <li>Does not instrument parameters. Consider calling the corresponding assertion method manually.</li>
 * <li>Does not instrument overriding methods. If the overriding method has the same threading contract as the supermethod,
 * annotate it again.</li>
 * <li>Does not instrument abstract methods, as there is no method body.</li>
 * <li>Does not instrument suspend functions.</li>
 * <li>Does not add assertions to calls inlined before instrumentation.</li>
 * <li>May omit assertions when targeting older platform versions.</li>
 * </ul>
 *
 */
package com.intellij.tools.build.bazel.jvmIncBuilder.tmh;
