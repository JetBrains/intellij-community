package io.bazel.kotlin.plugin.jdeps

import io.bazel.kotlin.plugin.jdeps.k2.checker.declaration.BasicDeclarationChecker
import io.bazel.kotlin.plugin.jdeps.k2.checker.declaration.CallableChecker
import io.bazel.kotlin.plugin.jdeps.k2.checker.declaration.ClassLikeChecker
import io.bazel.kotlin.plugin.jdeps.k2.checker.declaration.FileChecker
import io.bazel.kotlin.plugin.jdeps.k2.checker.declaration.FunctionChecker
import io.bazel.kotlin.plugin.jdeps.k2.checker.expression.QualifiedAccessChecker
import io.bazel.kotlin.plugin.jdeps.k2.checker.expression.ResolvedQualifierChecker
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassLikeChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirQualifiedAccessExpressionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirResolvedQualifierChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

internal class JdepsFirExtensions(
  private val classUsageRecorder: ClassUsageRecorder,
) : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +::JdepsFirCheckersExtension
  }

  internal inner class JdepsFirCheckersExtension(
    session: FirSession,
  ) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers =
      object : DeclarationCheckers() {
        override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> = setOf(BasicDeclarationChecker(classUsageRecorder))

        override val fileCheckers: Set<FirFileChecker> = setOf(FileChecker(classUsageRecorder))

        override val classLikeCheckers: Set<FirClassLikeChecker> = setOf(ClassLikeChecker(classUsageRecorder))

        override val functionCheckers: Set<FirFunctionChecker> = setOf(FunctionChecker(classUsageRecorder))

        override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> = setOf(CallableChecker(classUsageRecorder))
      }

    override val expressionCheckers: ExpressionCheckers =
      object : ExpressionCheckers() {
        override val qualifiedAccessExpressionCheckers: Set<FirQualifiedAccessExpressionChecker> =
          setOf(QualifiedAccessChecker(classUsageRecorder))

        override val resolvedQualifierCheckers: Set<FirResolvedQualifierChecker> =
          setOf(ResolvedQualifierChecker(classUsageRecorder))
      }
  }
}
