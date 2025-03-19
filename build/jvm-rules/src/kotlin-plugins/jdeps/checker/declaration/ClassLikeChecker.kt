package io.bazel.kotlin.plugin.jdeps.k2.checker.declaration

import io.bazel.kotlin.plugin.jdeps.ClassUsageRecorder
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassLikeChecker
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.resolve.getSuperTypes
import org.jetbrains.kotlin.name.ClassId

internal class ClassLikeChecker(
  private val classUsageRecorder: ClassUsageRecorder,
) : FirClassLikeChecker(MppCheckerKind.Common) {
  override fun check(
    declaration: FirClassLikeDeclaration,
    context: CheckerContext,
    reporter: DiagnosticReporter,
  ) {
    val visited = HashSet<Pair<ClassId, Boolean>>()
    classUsageRecorder.recordClass(
      firClass = declaration.symbol,
      context = context,
      visited = visited,
    )
    // [recordClass] also handles supertypes, but this marks direct supertypes as explicit
    for (path in declaration.symbol.getSuperTypes(useSiteSession = context.session, recursive = false)) {
      visited.clear()
      classUsageRecorder.recordConeType(
        coneKotlinType = path,
        context = context,
        visited = visited,
      )
    }
  }
}
