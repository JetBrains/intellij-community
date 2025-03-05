package io.bazel.kotlin.plugin.jdeps.k2.checker.expression

import io.bazel.kotlin.plugin.jdeps.ClassUsageRecorder
import io.bazel.kotlin.plugin.jdeps.binaryClass
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirQualifiedAccessExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.toResolvedFunctionSymbol
import org.jetbrains.kotlin.fir.types.isExtensionFunctionType
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.resolvedType

internal class QualifiedAccessChecker(
  private val classUsageRecorder: ClassUsageRecorder,
) : FirQualifiedAccessExpressionChecker(MppCheckerKind.Common) {
  override fun check(
    expression: FirQualifiedAccessExpression,
    context: CheckerContext,
    reporter: DiagnosticReporter,
  ) {
    // track function's owning class
    val resolvedCallableSymbol = expression.toResolvedCallableSymbol()
    resolvedCallableSymbol?.containerSource?.binaryClass()?.let {
      classUsageRecorder.addClass(path = it, isExplicit = true)
    }

    // track return type
    resolvedCallableSymbol?.resolvedReturnTypeRef?.let {
      classUsageRecorder.recordTypeRef(
        typeRef = it,
        context = context,
        isExplicit = false,
        collectTypeArguments = false,
      )
    }

    // type arguments
    resolvedCallableSymbol?.typeParameterSymbols?.forEach { typeParam ->
      typeParam.resolvedBounds.forEach { classUsageRecorder.recordTypeRef(it, context) }
    }

    // track fun parameter types based on referenced function
    expression.calleeReference
      .toResolvedFunctionSymbol()
      ?.valueParameterSymbols
      ?.forEach { valueParam ->
        valueParam.resolvedReturnTypeRef.let {
          classUsageRecorder.recordTypeRef(typeRef = it, context = context, isExplicit = false)
        }
      }
    // track fun arguments actually passed
    (expression as? FirFunctionCall)?.arguments?.map { it.resolvedType }?.forEach {
      classUsageRecorder.recordConeType(
        coneKotlinType = it,
        context = context,
        isExplicit = !it.isExtensionFunctionType,
      )
    }

    // track dispatch receiver
    expression.dispatchReceiver?.resolvedType?.let {
      if (!it.isUnit) {
        classUsageRecorder.recordConeType(
          coneKotlinType = it,
          context = context,
          isExplicit = false,
        )
      }
    }
  }
}
