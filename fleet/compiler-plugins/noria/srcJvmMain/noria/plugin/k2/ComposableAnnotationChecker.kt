// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package noria.plugin.k2

import noria.plugin.NoriaRuntime
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirResolvedTypeRefChecker
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.isNonPrimitiveArray

object ComposableAnnotationChecker : FirResolvedTypeRefChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(typeRef: FirResolvedTypeRef) {
        val composableAnnotation = typeRef.getAnnotationByClassId(NoriaRuntime.ComposableClassId, context.session)
        if (composableAnnotation == null) {
            return
        }

        // ignore if the type is coming from vararg
        if (
            typeRef.delegatedTypeRef?.source?.kind == KtFakeSourceElementKind.ArrayTypeFromVarargParameter &&
            typeRef.coneType.isNonPrimitiveArray
        ) {
            return
        }

        if (
            typeRef !is FirErrorTypeRef &&
            !typeRef.coneType.isComposableFunction(context.session)
        ) {
            reporter.reportOn(
                composableAnnotation.source,
                ComposeErrors.COMPOSABLE_INAPPLICABLE_TYPE,
                typeRef.coneType
            )
        }
    }
}