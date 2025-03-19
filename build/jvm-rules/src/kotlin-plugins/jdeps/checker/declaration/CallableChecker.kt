package io.bazel.kotlin.plugin.jdeps.k2.checker.declaration

import io.bazel.kotlin.plugin.jdeps.ClassUsageRecorder
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.isExtension
import org.jetbrains.kotlin.name.ClassId

internal class CallableChecker(
  private val classUsageRecorder: ClassUsageRecorder,
) : FirCallableDeclarationChecker(MppCheckerKind.Common) {
  /**
   * Tracks the return type & type parameters of a callable declaration. Function parameters are
   * tracked in [FunctionChecker].
   */
  override fun check(
    declaration: FirCallableDeclaration,
    context: CheckerContext,
    reporter: DiagnosticReporter,
  ) {
    val visited = HashSet<Pair<ClassId, Boolean>>()
    // return type
    classUsageRecorder.recordTypeRef(
      typeRef = declaration.returnTypeRef,
      context = context,
      visited = visited,
    )

    // type params
    for (typeParam in declaration.typeParameters) {
      for (typeParamBound in typeParam.symbol.resolvedBounds) {
        visited.clear()
        classUsageRecorder.recordTypeRef(
          typeRef = typeParamBound,
          context = context,
          visited = visited,
        )
      }
    }

    // receiver param for extensions
    if (declaration !is FirAnonymousFunction) {
      declaration.receiverParameter?.typeRef?.let {
        visited.clear()
        classUsageRecorder.recordTypeRef(
          typeRef = it,
          context = context,
          isExplicit = declaration.isExtension,
          visited = visited,
        )
      }
    }
  }
}
