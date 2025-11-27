/*
 * Copyright 2025 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 license.
 */

package noria.plugin.k2

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirAnonymousObjectChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol

object ComposableInnerClassChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirRegularClass) {
        if (!context.hasComposableAncestor()) return

        reporter.reportOn(
            declaration.source,
            ComposeErrors.CLASS_OR_OBJECT_INSIDE_COMPOSABLE
        )
    }
}

object ComposableAnonymousObjectChecker : FirAnonymousObjectChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirAnonymousObject) {
        if (!context.hasComposableAncestor()) return

        reporter.reportOn(
            declaration.source,
            ComposeErrors.CLASS_OR_OBJECT_INSIDE_COMPOSABLE
        )
    }
}

private fun CheckerContext.hasComposableAncestor(): Boolean {
    for (decl in containingDeclarations.asReversed()) {
        when (decl) {
            is FirCallableSymbol -> {
                if (decl.isComposable(session)) return true
            }
        }
    }
    return false
}
