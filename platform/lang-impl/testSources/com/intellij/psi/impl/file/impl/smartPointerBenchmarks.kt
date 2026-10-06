// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.smartPointers.SmartPointerManagerEx
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/*
 * Common parts of the smart pointer revalidation benchmarks.
 *
 * [com.intellij.psi.impl.smartPointers.SmartPointerTracker.revalidate] runs on every pointer
 * resolution. It returns at once while the tracker is at the current generation. After a generation
 * bump it scans all pointers of the file one time. The benchmarks measure that scan from four sides:
 *
 * - [SmartPointerRevalidationPerformanceTest]: one file, one context, range pointers.
 * - [SmartPointerStubRevalidationPerformanceTest]: the same, with range-less stub-based infos.
 * - [SmartPointerBulkInvalidationPerformanceTest]: many files, and the real bulk invalidation.
 * - [SmartPointerMultiContextRevalidationPerformanceTest]: one file in many code insight contexts.
 *
 * The generation counter only moves in the multiverse mode, so every benchmark needs a multiverse
 * project. Each test class holds its own project fixture, because the fixture framework builds an
 * instance fixture per test method.
 *
 * ** Rules for a body **
 *
 * The body of a benchmark must be blocking, and it must not run inside `timeoutRunBlocking`.
 * `BenchmarkTestInfoImpl.start` reads the launch name from the stack of the current thread, and a
 * coroutine can resume on a thread that holds no test method frame. Each benchmark therefore does
 * the suspend preparation first, and then starts the measurement outside the coroutine with an
 * explicit method name.
 *
 * The runner calls `System.gc()` after every attempt. The tracker holds the pointers on weak
 * references, so a test keeps every pointer, PSI file and virtual file in a field.
 *
 * `setup` runs before every attempt, not one time. It may only bring the tracker to the current
 * generation. All of the preparation belongs in the suspend phase.
 */

internal const val BENCHMARK_ATTEMPTS: Int = 3

internal val BENCHMARK_PREPARE_TIMEOUT: Duration = 10.minutes

internal fun blankText(length: Int): String = " ".repeat(length)

/** Moves the generation counter without a walk of the view provider cache. Needs a write action. */
internal fun Project.bumpSmartPointerGeneration() {
  SmartPointerManagerEx.getInstanceEx(this).possiblyInvalidationModCounter.incModificationCount()
}

/** The real bulk invalidation. Moves the counter and marks every view provider as possibly invalid. */
internal fun Project.bulkInvalidatePsi() {
  runWriteActionAndWait {
    DebugUtil.performPsiModification<Throwable>("") {
      PsiManagerImpl.getInstanceEx(this).fileManagerEx.possiblyInvalidatePhysicalPsi()
    }
  }
}

/** The number of pointers the tracker holds for [psiFile]. Proves the garbage collector took none of them. */
internal fun Project.smartPointersNumber(psiFile: PsiFile): Int =
  (SmartPointerManager.getInstance(this) as SmartPointerManagerImpl).getPointersNumber(psiFile)
