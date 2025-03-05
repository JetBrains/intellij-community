package io.bazel.kotlin.plugin.jdeps

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.utils.sourceElement
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.forEachType
import org.jetbrains.kotlin.name.ClassId

//private const val JAR_FILE_SEPARATOR = "!/"
private const val ANONYMOUS = "<anonymous>"

internal class ClassUsageRecorder {
  @JvmField
  val explicitClassesCanonicalPaths: MutableSet<String> = HashSet()
  @JvmField
  val implicitClassesCanonicalPaths: MutableSet<String> = HashSet()

  internal fun recordTypeRef(
    typeRef: FirTypeRef,
    context: CheckerContext,
    isExplicit: Boolean = true,
    collectTypeArguments: Boolean = true,
    visited: MutableSet<Pair<ClassId, Boolean>> = HashSet(),
  ) {
    recordConeType(
      coneKotlinType = typeRef.coneType,
      context = context,
      isExplicit = isExplicit,
      collectTypeArguments = collectTypeArguments,
      visited = visited,
    )
  }

  internal fun recordConeType(
    coneKotlinType: ConeKotlinType,
    context: CheckerContext,
    isExplicit: Boolean = true,
    collectTypeArguments: Boolean = true,
    visited: MutableSet<Pair<ClassId, Boolean>> = HashSet(),
  ) {
    if (collectTypeArguments) {
      coneKotlinType.forEachType(
        action = { coneType ->
          val classId = coneType.classId ?: return@forEachType
          if (classId.toString().contains(ANONYMOUS)) {
            return@forEachType
          }

          context.session.symbolProvider
            .getClassLikeSymbolByClassId(classId)
            ?.let {
              recordClass(
                firClass = it,
                context = context,
                isExplicit = isExplicit,
                collectTypeArguments = collectTypeArguments,
                visited = visited
              )
            }
        },
      )
    }
    else {
      coneKotlinType.classId?.let { classId ->
        if (!classId.isLocal) {
          context.session.symbolProvider
            .getClassLikeSymbolByClassId(classId)
            ?.let {
              recordClass(
                firClass = it,
                context = context,
                isExplicit = isExplicit,
                collectTypeArguments = false,
                visited = visited,
              )
            }
        }
      }
    }
  }

  internal fun recordClass(
    firClass: FirClassLikeSymbol<*>,
    context: CheckerContext,
    isExplicit: Boolean = true,
    collectTypeArguments: Boolean = true,
    visited: MutableSet<Pair<ClassId, Boolean>> = HashSet(),
  ) {
    val classIdAndIsExplicit = firClass.classId to isExplicit
    if (!visited.add(classIdAndIsExplicit)) {
      return
    }

    firClass.sourceElement?.binaryClass()?.let { addClass(path = it, isExplicit = isExplicit) }

    if (firClass is FirClassSymbol<*>) {
      for (typeRef in firClass.resolvedSuperTypeRefs) {
        recordTypeRef(
          typeRef = typeRef,
          context = context,
          isExplicit = false,
          collectTypeArguments = collectTypeArguments,
          visited = visited,
        )
      }
      if (collectTypeArguments) {
        firClass.typeParameterSymbols
          .asSequence()
          .flatMap { it.resolvedBounds }
          .forEach {
            recordTypeRef(
              typeRef = it,
              context = context,
              isExplicit = isExplicit,
              collectTypeArguments = true,
              visited = visited,
            )
          }
      }
    }
  }

  internal fun addClass(
    path: String,
    isExplicit: Boolean,
  ) {
    if (isExplicit) {
      explicitClassesCanonicalPaths.add(path)
    }
    else {
      implicitClassesCanonicalPaths.add(path)
    }
  }
}
