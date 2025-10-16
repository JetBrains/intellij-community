/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package noria.plugin.k2

import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.psi.KtElement

object ComposeErrors : KtDiagnosticsContainer() {
    // error goes on the composable call in a non-composable function
    val COMPOSABLE_INVOCATION by error0<KtElement>()

    // error goes on the non-composable function with composable calls
    val COMPOSABLE_EXPECTED by error0<KtElement>(
        SourceElementPositioningStrategies.DECLARATION_NAME
    )

    val NONREADONLY_CALL_IN_READONLY_COMPOSABLE by error0<KtElement>()

    val CAPTURED_COMPOSABLE_INVOCATION by
    error2<KtElement, FirVariableSymbol<*>, FirCallableSymbol<*>>()

    val MISSING_DISALLOW_COMPOSABLE_CALLS_ANNOTATION by error3<
            KtElement,
            FirValueParameterSymbol, // unmarked
            FirValueParameterSymbol, // marked
            FirCallableSymbol<*>>()

    val DEPRECATED_OPEN_COMPOSABLE_DEFAULT_PARAMETER_VALUE by warning0<KtElement>()

    val COMPOSABLE_SUSPEND_FUN by error0<KtElement>(
        SourceElementPositioningStrategies.DECLARATION_NAME
    )

    val COMPOSABLE_FUN_MAIN by error0<KtElement>(
        SourceElementPositioningStrategies.DECLARATION_NAME
    )

    // TODO Implement a K2-compatible check that flags this before the backend phase for better DX, to avoid the
    // 'java.lang.AssertionError: Unexpected IR element found during code generation.' error thrown otherwise
    val COMPOSABLE_FUNCTION_REFERENCE by error0<KtElement>(
        ComposeSourceElementPositioningStrategies.DECLARATION_NAME_OR_DEFAULT
    )

    val COMPOSABLE_PROPERTY_REFERENCE by error0<KtElement>(
        ComposeSourceElementPositioningStrategies.DECLARATION_NAME_OR_DEFAULT
    )

    val COMPOSABLE_PROPERTY_BACKING_FIELD by error0<KtElement>(
        SourceElementPositioningStrategies.DECLARATION_NAME
    )

    val COMPOSABLE_VAR by error0<KtElement>(SourceElementPositioningStrategies.DECLARATION_NAME)

    val COMPOSE_INVALID_DELEGATE by error0<KtElement>(
        ComposeSourceElementPositioningStrategies.DECLARATION_NAME_OR_DEFAULT
    )

    val MISMATCHED_COMPOSABLE_IN_EXPECT_ACTUAL by error0<KtElement>(
        SourceElementPositioningStrategies.DECLARATION_NAME
    )

    val COMPOSE_APPLIER_CALL_MISMATCH by warning2<KtElement, String, String>(
        SourceElementPositioningStrategy(
            LightTreePositioningStrategies.REFERENCED_NAME_BY_QUALIFIED,
            PositioningStrategies.CALL_EXPRESSION
        )
    )

    val COMPOSE_APPLIER_PARAMETER_MISMATCH by warning2<KtElement, String, String>()

    val COMPOSE_APPLIER_DECLARATION_MISMATCH by warning0<KtElement>(
        ComposeSourceElementPositioningStrategies.DECLARATION_NAME_OR_DEFAULT
    )

    val COMPOSABLE_INAPPLICABLE_TYPE by error1<KtElement, ConeKotlinType>()

    val OPEN_COMPOSABLE_DEFAULT_PARAMETER_VALUE by error1<KtElement, LanguageVersion>()

    val ABSTRACT_COMPOSABLE_DEFAULT_PARAMETER_VALUE by error1<KtElement, LanguageVersion>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = ComposeErrorMessages
}

object ComposeSourceElementPositioningStrategies {
    val DECLARATION_NAME_OR_DEFAULT = SourceElementPositioningStrategies.DECLARATION_NAME
}
