package com.intellij.microservices.url.parameters

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.pom.PomTarget
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import com.intellij.semantic.SemElement
import com.intellij.semantic.SemKey
import com.intellij.semantic.SemService
import com.intellij.util.Plow

interface RenameableSemElement : SemElement {
  val name: String

  val nameVariants: Plow<LookupElement>
    get() = Plow.empty()

  companion object {
    @JvmField
    val RENAMEABLE_SEM_KEY: SemKey<RenameableSemElement> = SemKey.createKey("RenameableSemElement")
  }
}

/**
 * Adds the "usage" semantics to the [SEM_KEY], meaning that all [PsiElement]s for which [SEM_KEY] is defined will be considered
 * as "usage" of the [PomTargetPsiElement] returned from [createPomTargetPsi] ( or any [PomTargetPsiElement] which is equal)
 */
interface RenameableSemElementSupport<T : RenameableSemElement> {

  /**
   * A [SemKey] for which "usage" semantic is added
   */
  @Suppress("PropertyName")
  val SEM_KEY: SemKey<T>

  /**
   * @return all [SEM_KEY]-"references" for the [pomTarget]
   * All returned [PsiElement]s should have the [SEM_KEY] defined for them via [SemService].
   * For instance, for the PathVariable PomTarget it will return PsiParameters that uses it
   *
   * @param pomTarget implementation-specific POM target.
   * Ok, different PomTargets will be passed there, but the implementation should cast and process its own
   */
  fun findReferencingPsiElements(pomTarget: PomTarget): Iterable<PsiElement>

  /**
   * @return `true` if the given [pomTarget] could be handled by current implementation
   */
  fun supportsTarget(pomTarget: PomTarget): Boolean

  /**
   * @return [PomTargetPsiElement] that will be used for reference search.
   * @param sem data, that should be used to create [PomTargetPsiElement].
   * It implies that [T] should contain enough data to create a distinguishable [PomTargetPsiElement],
   * though it doesn't mean that [sem] is the only way to create such [PomTargetPsiElement]s
   */
  fun createPomTargetPsi(project: Project, sem: T): PomTargetPsiElement?
}

fun <T : RenameableSemElement> RenameableSemElementSupport<T>.getSemElement(element: PsiElement): T? =
  SemService.getSemService(element.project).getSemElement(SEM_KEY, element)