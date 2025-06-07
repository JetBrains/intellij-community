package com.intellij.microservices.url.parameters

import com.intellij.openapi.project.Project
import com.intellij.pom.PomTarget
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiElement
import com.intellij.semantic.SemElement
import com.intellij.semantic.SemKey

internal object PathVariableSemElementSupport : RenameableSemElementSupport<PathVariableSem> {
  override val SEM_KEY: SemKey<PathVariableSem>
    get() = PATH_VARIABLE_SEM_KEY

  override fun findReferencingPsiElements(pomTarget: PomTarget): Iterable<PsiElement> {
    if (pomTarget !is PathVariablePomTarget) return emptyList()
    return pomTarget.findSemDefinitionPsiElement()
  }

  override fun supportsTarget(pomTarget: PomTarget): Boolean = pomTarget is PathVariablePomTarget

  override fun createPomTargetPsi(project: Project, sem: PathVariableSem): PomTargetPsiElement? = sem.pathVariablePsiElement
}

/**
 * A [SemElement] that should be registered for [PsiElement]s which are considered as usages of [pathVariablePsiElement],
 * in case when it couldn't be done by [com.intellij.psi.PsiReference]
 */
interface PathVariableSem : RenameableSemElement {
  override val name: String

  /**
   * The path variable that this [PathVariableSem] resolves to.
   * `null` means that this element is not a PathVariable usage
   */
  val pathVariablePsiElement: PathVariablePsiElement?

  /**
   * @return `true` if the [PsiElement], that current [PathVariableSem] is registered for, is an actual name holder,
   * and it's name is reference for the Path Variable and should follow the renaming of the Path Variable
   * `false` if this [PathVariableSem] doesn't hold the name  for instance if there is an explicit reference e.g. `@PathVariable("explicitName")`)
   * if this property is `false` it means that current [PathVariableSem] is useless and should not participate in reference operations (rename, find-usages)
   */
  val isActualNameHolder: Boolean
}

@JvmField
val PATH_VARIABLE_SEM_KEY: SemKey<PathVariableSem> = SemKey.createKey("PathVariable", RenameableSemElement.RENAMEABLE_SEM_KEY)