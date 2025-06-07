package com.intellij.microservices.endpoints

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope.notScope
import com.intellij.psi.search.GlobalSearchScopesCore.projectTestScope
import com.intellij.psi.search.ProjectScope

/**
 * Filter for items of [EndpointsProvider].
 *
 * @see ModuleEndpointsFilter
 * @see ExternalEndpointsFilter
 */
interface EndpointsFilter

/**
 * Filter for items defined in a project scope.
 */
interface SearchScopeEndpointsFilter : EndpointsFilter {
  /**
   * Does not include any transitive modules, dependencies, libraries.
   * Should be used for specification-like declarations, e.g., OpenAPI, gRPC.
   */
  val contentSearchScope: GlobalSearchScope

  /**
   * Includes transitive dependencies, such as modules and libraries. Should be used for application-like frameworks that inherit all
   * endpoint handlers from dependency modules, e.g., Spring, Micronaut.
   */
  val transitiveSearchScope: GlobalSearchScope
}

/**
 * Filter for items defined in a project module.
 */
data class ModuleEndpointsFilter(
  val module: Module,
  val fromLibraries: Boolean,
  val fromTests: Boolean
) : SearchScopeEndpointsFilter {
  override val transitiveSearchScope: GlobalSearchScope
    get() {
      if (fromLibraries) {
        val contentScope = module.moduleContentScope // required for JS / Swagger files not in source roots
        // includeTests parameter affects library scope, we always include both compile and test libraries if fromLibraries is enabled
        val allModuleScope = module.getModuleWithDependenciesAndLibrariesScope(true).union(contentScope)
        if (fromTests) return allModuleScope

        val testScope = projectTestScope(module.project)
        return allModuleScope.intersectWith(notScope(testScope))
      }
      if (fromTests) return module.moduleContentWithDependenciesScope

      val testScope = projectTestScope(module.project)
      return module.moduleContentWithDependenciesScope
        .intersectWith(notScope(testScope))
    }

  override val contentSearchScope: GlobalSearchScope
    get() {
      if (fromTests) return module.moduleContentScope

      val testScope = projectTestScope(module.project)
      return module.moduleContentScope
        .intersectWith(notScope(testScope))
    }

  fun <T> filterByScope(items: Collection<T>, containingFileGetter: (T) -> PsiFile?): Iterable<T> {
    if (fromTests && fromLibraries) return items

    return filterByScope(items.asSequence(), containingFileGetter)
  }

  fun <T> filterByScope(sequence: Sequence<T>, containingFileGetter: (T) -> PsiFile?): Iterable<T> {
    if (fromTests && fromLibraries) return sequence.asIterable()

    val librariesScope = ProjectScope.getLibrariesScope(module.project)
    return sequence.filter {
      val file = containingFileGetter(it)?.virtualFile

      file == null ||
      ((fromLibraries || !librariesScope.contains(file))
       && (fromTests || !TestSourcesFilter.isTestSources(file, module.project)))
    }.asIterable()
  }
}

/**
 * Filter for items defined outside the project: e.g., external OpenAPI specifications.
 */
object ExternalEndpointsFilter : EndpointsFilter