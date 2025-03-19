package io.bazel.kotlin.plugin.jdeps.k2.checker.declaration

import io.bazel.kotlin.plugin.jdeps.ClassUsageRecorder
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.name.ClassId

internal class BasicDeclarationChecker(
  private val classUsageRecorder: ClassUsageRecorder,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
  override fun check(
    declaration: FirDeclaration,
    context: CheckerContext,
    reporter: DiagnosticReporter,
  ) {
    var visited: HashSet<Pair<ClassId, Boolean>>? = null
    for (annotation in declaration.annotations) {
      val symbol = annotation.toAnnotationClassLikeSymbol(context.session) ?: continue
      if (visited == null) {
        visited = HashSet()
      }
      else {
        visited.clear()
      }
      classUsageRecorder.recordClass(firClass = symbol, context = context, visited = visited)
    }
  }
}
