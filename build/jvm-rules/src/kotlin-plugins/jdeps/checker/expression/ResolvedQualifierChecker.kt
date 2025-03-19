package io.bazel.kotlin.plugin.jdeps.k2.checker.expression

import io.bazel.kotlin.plugin.jdeps.ClassUsageRecorder
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirResolvedQualifierChecker
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier

// Handles expressions such as enum constants and annotation usages
internal class ResolvedQualifierChecker(
  private val classUsageRecorder: ClassUsageRecorder,
) : FirResolvedQualifierChecker(MppCheckerKind.Common) {
  override fun check(
    expression: FirResolvedQualifier,
    context: CheckerContext,
    reporter: DiagnosticReporter,
  ) {
    expression.symbol?.let {
      classUsageRecorder.recordClass(firClass = it, context = context)
    }
  }
}
