// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.LocalVariable
import com.sun.jdi.Location
import com.sun.jdi.Method
import com.sun.jdi.ReferenceType
import org.jetbrains.annotations.TestOnly

/**
 * Maps a runtime lambda method back to the lambda ordinal used by source-level breakpoint variants.
 * This is important to know if more than one lambda body is available on the line.
 * Before javac 24, we could rely on the numeric suffix of generated lambda method names `lambda$...$N`,
 * as it followed source lambda ordinal closely enough. That is no longer safe:
 *
 *   ```java
 *   // before javac 24
 *   new Thread(() -> {             // compiled into lambda$main$2
 *     var r = Stream.of(1)
 *       .map(x -> x * x)           // compiled into lambda$main$0
 *       .map(y -> y + y);          // compiled into lambda$main$1
 *   });
 *   ```
 *
 *   ```java
 *   // javac 24+
 *   new Thread(() -> {             // compiled into lambda$main$0
 *     var r = Stream.of(1)
 *       .map(x -> x * x)           // compiled into lambda$main$1
 *       .map(y -> y + y);          // compiled into lambda$main$2
 *   });
 *   ```
 *
 * To distinguish several lambdas on one line, we use heuristics:
 * 1. Comparing line number ranges from the generated lambda methods
 * 2. Comparing argument names from the generated lambda methods and the source code
 * 3. Just rely on the generated lambda methods names ordinal `lambda$...$N` - works well before jdk 24 (or if lambdas are not nested)
 */
object LambdaOrdinalResolver {
  private val LOG = Logger.getInstance(LambdaOrdinalResolver::class.java)

  @JvmStatic
  fun findLambdaOrdinal(sourcePosition: SourcePosition, method: Method?): Int {
    if (method == null || !DebuggerUtilsEx.isLambda(method)) {
      return -1
    }

    val bytecodeLambdas = collectBytecodeLambdas(sourcePosition, method.declaringType())
    if (bytecodeLambdas.size <= 1 || !bytecodeLambdas.contains(method)) {
      return -1
    }

    val lambdaOrdinal = findLambdaOrdinalBySourceInfo(sourcePosition, method, bytecodeLambdas)
    if (lambdaOrdinal >= 0) {
      return lambdaOrdinal
    }
    return bytecodeLambdas.sortedWith(DebuggerUtilsEx.LAMBDA_ORDINAL_COMPARATOR).indexOf(method)
  }

  private fun collectBytecodeLambdas(sourcePosition: SourcePosition, type: ReferenceType): List<Method> {
    val line = sourcePosition.line + 1
    return type.methods().asSequence()
      .filter(DebuggerUtilsEx::isLambda) // call locationsOfLine only for lambdas
      .filter { DebuggerUtilsEx.locationsOfLine(it, line).isNotEmpty() }
      .toList()
  }

  private fun findLambdaOrdinalBySourceInfo(sourcePosition: SourcePosition, method: Method, bytecodeLambdas: List<Method>): Int {
    val sourceLambdas = collectSourceLambdaInfos(sourcePosition)
    if (sourceLambdas.size <= 1) {
      return -1
    }
    val lambdaOrdinal = findLambdaOrdinalByLineRangeNesting(method, bytecodeLambdas, sourceLambdas)
    if (lambdaOrdinal >= 0) {
      return lambdaOrdinal
    }
    return findLambdaOrdinalByArgumentNames(method, sourceLambdas)
  }

  // Collect all required information from PSI in one read action here
  private fun collectSourceLambdaInfos(sourcePosition: SourcePosition): List<SourceLambdaInfo> {
    return runReadActionBlocking {
      val lambdas = DebuggerUtilsEx.collectLambdas(sourcePosition, true)
      lambdas.mapIndexed { ordinal, lambda ->
        SourceLambdaInfo(
          ordinal,
          countPsiAncestors(lambda, lambdas),
          countPsiDescendants(lambda, lambdas),
          lambda.parameterList.parameters.map { it.name },
        )
      }
    }
  }

  @RequiresReadLock
  private fun countPsiAncestors(lambda: PsiLambdaExpression, lambdas: List<PsiLambdaExpression>): Int {
    return lambdas.count { other -> other !== lambda && PsiTreeUtil.isAncestor(other, lambda, false) }
  }

  @RequiresReadLock
  private fun countPsiDescendants(lambda: PsiLambdaExpression, lambdas: List<PsiLambdaExpression>): Int {
    return lambdas.count { other -> other !== lambda && PsiTreeUtil.isAncestor(lambda, other, false) }
  }

  private fun findLambdaOrdinalByLineRangeNesting(
    method: Method,
    bytecodeLambdas: List<Method>,
    sourceLambdas: List<SourceLambdaInfo>,
  ): Int {
    val targetLineRange = getMethodLineRange(method) ?: return -1
    val methodsLineRanges = bytecodeLambdas.map { getMethodLineRange(it) ?: return -1 }
    return findLambdaOrdinalByLineRangeNesting(targetLineRange, methodsLineRanges, sourceLambdas)
  }

  /**
   * Tries to distinguish lambdas by mapped line ranges
   * ```java
   *    foo(() -> {
   *      boo(() -> zoo()); // this line is mapped in both lambdas, but we can check that outer lambda line range contains the inner lambda
   *    });
   * ```
   */
  private fun findLambdaOrdinalByLineRangeNesting(
    targetRange: LambdaLineRange,
    ranges: List<LambdaLineRange>,
    sourceLambdas: List<SourceLambdaInfo>,
  ): Int {
    val nestingProfile = NestingProfile.fromLineRanges(targetRange, ranges)
    if (!nestingProfile.hasNesting) {
      return -1
    }
    // Do not use line ranges if they produce the same evidence for several runtime methods
    if (ranges.count { range -> NestingProfile.fromLineRanges(range, ranges) == nestingProfile } > 1) {
      return -1
    }

    // Accept only a unique source match; zero or several matches fall back to argument-name matching
    val matchingLambdas = sourceLambdas.filter { nestingProfile.isCompatibleWith(it) }
    return matchingLambdas.singleOrNull()?.ordinal ?: -1
  }

  private fun getMethodLineRange(method: Method): LambdaLineRange? {
    val locations = try {
      method.allLineLocations(DebugProcess.JAVA_STRATUM, null)
    }
    catch (e: AbsentInformationException) {
      LOG.debug(e)
      return null
    }
    catch (e: InternalError) {
      LOG.debug(e)
      return null
    }
    catch (e: IllegalArgumentException) {
      LOG.debug(e)
      return null
    }

    return getLineRange(locations)
  }

  private fun getLineRange(locations: List<Location>): LambdaLineRange? {
    val lineNumbers = locations.asSequence()
      .map { it.lineNumber(DebugProcess.JAVA_STRATUM) }
      .filter { it >= 0 }
      .toList()
    if (lineNumbers.isEmpty()) return null
    return LambdaLineRange(lineNumbers.min(), lineNumbers.max())
  }

  /**
   * Tries to distinguish lambdas argument names captured/declared in the bytecode
   * ```java
   *    // inner lambda has argument b, outer - a, we can match this information with the bytecode debug info
   *    list.forEach(a -> { list.forEach(b -> use(a, b)); use(a); });
   * ```
   */
  private fun findLambdaOrdinalByArgumentNames(method: Method, sourceLambdas: List<SourceLambdaInfo>): Int {
    val arguments = try {
      method.arguments()
    }
    catch (e: AbsentInformationException) {
      LOG.debug(e)
      return -1
    }
    catch (e: InternalError) {
      LOG.debug(e)
      return -1
    }
    catch (e: IllegalArgumentException) {
      LOG.debug(e)
      return -1
    }

    var bestOrdinal = -1
    var bestScore = 0
    var ambiguous = false
    for (lambda in sourceLambdas) {
      val score = getLambdaArgumentsMatchScore(lambda, arguments)
      if (score > bestScore) {
        bestScore = score
        bestOrdinal = lambda.ordinal
        ambiguous = false
      }
      else if (score == bestScore && score > 0) {
        ambiguous = true
      }
    }
    return if (!ambiguous) bestOrdinal else -1
  }

  private fun getLambdaArgumentsMatchScore(lambda: SourceLambdaInfo, arguments: List<LocalVariable>): Int {
    val parameters = lambda.parameterNames
    if (parameters.isEmpty()) return if (arguments.isEmpty()) 1 else 0
    if (parameters.size > arguments.size) return 0

    var score = 0
    // Generated lambda methods receive captured values first, then declared lambda parameters.
    // Source PSI gives us only declared parameters, so the size difference is the captured-values prefix.
    val capturedArgumentCount = arguments.size - parameters.size
    for ((parameterIndex, name) in parameters.withIndex()) {
      if (!arguments.any { argument -> name == argument.name() }) {
        return 0
      }
      // Name presence is weak evidence; matching the declared-parameters suffix is stronger.
      score += 10
      if (name == arguments[capturedArgumentCount + parameterIndex].name()) {
        score += 100
      }
    }
    return score
  }

  @JvmStatic
  @TestOnly
  fun unambiguouslyContainsLineRangeForTest(parentStartLine: Int, parentEndLine: Int, childStartLine: Int, childEndLine: Int): Boolean {
    val parent = LambdaLineRange(parentStartLine, parentEndLine)
    val child = LambdaLineRange(childStartLine, childEndLine)
    return child.isNestedIn(parent) == ThreeState.YES
  }

  @JvmStatic
  @TestOnly
  fun lineRangeNestingOrdinalForTest(targetRange: IntRange, ranges: List<IntRange>, sourceProfiles: List<Pair<Int, Int>>): Int {
    return findLambdaOrdinalByLineRangeNesting(
      LambdaLineRange(targetRange.first, targetRange.last),
      ranges.map { LambdaLineRange(it.first, it.last) },
      sourceProfiles.mapIndexed { ordinal, profile -> SourceLambdaInfo(ordinal, profile.first, profile.second, emptyList()) },
    )
  }

  private data class NestingProfile(
    val certainAncestors: Int,
    val possibleAncestors: Int,
    val certainDescendants: Int,
    val possibleDescendants: Int,
  ) {
    val hasNesting: Boolean
      get() = possibleAncestors > 0 || possibleDescendants > 0 || certainAncestors > 0 || certainDescendants > 0

    fun isCompatibleWith(lambda: SourceLambdaInfo): Boolean {
      // Source PSI is exact. Runtime min/max line ranges are only evidence: boundary one-line ranges can be nested or sibling
      // lambdas, so they define an allowed range instead of a second matching mode.
      return lambda.ancestorCount in certainAncestors..possibleAncestors + certainAncestors &&
             lambda.descendantCount in certainDescendants..possibleDescendants + certainDescendants
    }

    companion object {
      fun fromLineRanges(targetRange: LambdaLineRange, ranges: List<LambdaLineRange>): NestingProfile {
        var requiredAncestors = 0
        var possibleAncestors = 0
        var requiredDescendants = 0
        var possibleDescendants = 0

        // Drop one range equal to the target. Equal ranges from other methods still remain and produce UNSURE evidence.
        val otherRanges = ranges.toMutableList().also { it.remove(targetRange) }

        for (range in otherRanges) {
          when (range.isNestedIn(targetRange)) {
            ThreeState.YES -> requiredDescendants++
            ThreeState.UNSURE -> possibleDescendants++
            else -> {}
          }
          when (targetRange.isNestedIn(range)) {
            ThreeState.YES -> requiredAncestors++
            ThreeState.UNSURE -> possibleAncestors++
            else -> {}
          }
        }
        return NestingProfile(requiredAncestors, possibleAncestors, requiredDescendants, possibleDescendants)
      }
    }
  }

  private data class SourceLambdaInfo(val ordinal: Int, val ancestorCount: Int, val descendantCount: Int, val parameterNames: List<String>)

  private data class LambdaLineRange(val startLine: Int, val endLine: Int) {
    fun isNestedIn(other: LambdaLineRange): ThreeState {
      /* Both lambdas have the same line range, and we do not know which one is nested
       * () -> {()->{
       * }}
       */
      if (this == other) return ThreeState.UNSURE

      val isInside = other.startLine <= startLine && endLine <= other.endLine
      if (!isInside) {
        return ThreeState.NO
      }

      /*
       * These cases have the same line range, but nested in the first case and siblings in another:
       *
       * () -> {       // line range (1,2)
       *    ()->{}};   // line range (2,2)
       *
       * () -> {       // line range (1,2)
       *    }; ()->{}; // line range (2,2)
       */
      if (!isSingleLineOnBoundaryOf(other)) return ThreeState.YES
      return ThreeState.UNSURE
    }

    private fun isSingleLineOnBoundaryOf(other: LambdaLineRange): Boolean {
      return startLine == endLine && (startLine == other.startLine || endLine == other.endLine)
    }
  }
}
