package io.bazel.kotlin.plugin.jdeps.k2.checker.declaration

import io.bazel.kotlin.plugin.jdeps.ClassUsageRecorder
import io.bazel.kotlin.plugin.jdeps.binaryClass
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirResolvedImport
import org.jetbrains.kotlin.fir.declarations.fullyExpandedClass
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.getFunctions
import org.jetbrains.kotlin.fir.scopes.impl.declaredMemberScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirAnonymousObjectSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.name.ClassId

internal class FileChecker(
  private val classUsageRecorder: ClassUsageRecorder,
) : FirFileChecker(MppCheckerKind.Common) {
  override fun check(
    declaration: FirFile,
    context: CheckerContext,
    reporter: DiagnosticReporter,
  ) {
    val visited = HashSet<Pair<ClassId, Boolean>>()
    for (import in declaration.imports.filterIsInstance<FirResolvedImport>()) {
      // check for classlike import (class, interface, object, enum, annotation, etc)
      if (import.resolvesToClass(context)) {
        import.classId()?.resolveToClass(context)?.let {
          visited.clear()
          classUsageRecorder.recordClass(firClass = it, context = context, visited = visited)
        }
      } else {
        // check for function import
        val callableBinaryClass = import.resolveToFun(context)?.containerSource?.binaryClass()
        if (callableBinaryClass != null) {
          classUsageRecorder.addClass(path = callableBinaryClass, isExplicit = true)
        } else {
          // for other symbols, track the parent class
          import.resolvedParentClassId?.resolveToClass(context)?.let {
            visited.clear()
            classUsageRecorder.recordClass(firClass = it, context = context, visited = visited)
          }
        }
      }
    }
  }
}

@Suppress("ReturnCount")
private fun FirResolvedImport.resolveToFun(context: CheckerContext): FirCallableSymbol<*>? {
  val funName = this.importedName ?: return null
  val topLevelFun = context.session.symbolProvider
    .getTopLevelCallableSymbols(packageFqName, funName)
    .firstOrNull()
  if (topLevelFun != null) {
    return topLevelFun
  }

  val parentClassId = resolvedParentClassId ?: return null
  return context.session.getClassDeclaredMemberScope(parentClassId)
    ?.getFunctions(funName)?.firstOrNull()
}

private fun FirResolvedImport.classId(): ClassId? {
  val importedFqName = importedFqName ?: return null
  if (importedFqName.isRoot || importedFqName.shortName().asString().isEmpty()) return null
  return this.resolvedParentClassId?.createNestedClassId(importedFqName.shortName())
    ?: ClassId.topLevel(importedFqName)
}

@OptIn(SymbolInternals::class)
private fun FirSession.getClassDeclaredMemberScope(classId: ClassId): FirScope? {
  val classSymbol = symbolProvider.getClassLikeSymbolByClassId(classId) as? FirRegularClassSymbol ?: return null
  return declaredMemberScope(classSymbol.fir, memberRequiredPhase = null)
}

// Below is the original private code from the Kotlin compiler
// https://github.com/JetBrains/kotlin/blob/8b7ca9527a55de33e943f8885b34456030ce9b19/compiler/fir/checkers/src/org/jetbrains/kotlin/fir/analysis/checkers/declaration/FirImportsChecker.kt#L232-L259

@Suppress("UnsafeCallOnNullableType", "UnnecessarySafeCall", "ReturnCount")
private fun FirResolvedImport.resolvesToClass(context: CheckerContext): Boolean {
  if (resolvedParentClassId != null) {
    if (isAllUnder) return true
    val parentClass = resolvedParentClassId!!
    val relativeClassName = this.relativeParentClassName ?: return false
    val importedName = this.importedName ?: return false
    val innerClassId =
      ClassId(parentClass.packageFqName, relativeClassName.child(importedName), false)
    return innerClassId.resolveToClass(context) != null
  } else {
    val importedFqName = importedFqName ?: return false
    if (importedFqName.isRoot) return false
    val importedClassId = ClassId.topLevel(importedFqName)
    return importedClassId.resolveToClass(context) != null
  }
}

private fun ClassId.resolveToClass(context: CheckerContext): FirRegularClassSymbol? {
  val classSymbol = context.session.symbolProvider.getClassLikeSymbolByClassId(this) ?: return null
  return when (classSymbol) {
    is FirRegularClassSymbol -> classSymbol
    is FirTypeAliasSymbol -> classSymbol.fullyExpandedClass(context.session)
    is FirAnonymousObjectSymbol -> null
  }
}
