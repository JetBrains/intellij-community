// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.core.CoreBundle
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.UnloadedModuleDescription
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.psi.search.impl.VirtualFileEnumerationAware
import com.intellij.util.ArrayUtil
import com.intellij.util.Processor
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.toArray
import org.jetbrains.annotations.NonNls

/** @see GlobalSearchScope.union */
@Suppress("EqualsOrHashCode")
internal class UnionScope private constructor(
  project: Project?,
  val myScopes: Array<GlobalSearchScope>
) : GlobalSearchScope(project), VirtualFileEnumerationAware, CodeInsightContextAwareSearchScope {

  init {
    require(myScopes.size >= 2) { "expected >= 2 scopes but got: ${myScopes.contentToString()}" }
  }

  override fun extractFileEnumeration(): VirtualFileEnumeration? {
    val fileEnumerations: MutableList<VirtualFileEnumeration?> = SmartList<VirtualFileEnumeration?>()
    for (scope in myScopes) {
      val fileEnumeration = VirtualFileEnumeration.extract(scope)
      if (fileEnumeration == null) {
        return null
      }
      fileEnumerations.add(fileEnumeration)
    }
    return UnionFileEnumeration(fileEnumerations)
  }

  override fun getDisplayName(): String {
    return CoreBundle.message("psi.search.scope.union", myScopes[0].displayName, myScopes[1].displayName)
  }

  override fun contains(file: VirtualFile): Boolean {
    return myScopes.any { scope ->
      ProgressManager.checkCanceled()
      scope.contains(file)
    }
  }

  override val codeInsightContextInfo: CodeInsightContextInfo
    get() = createCodeInsightContextInfoUnion(myScopes)

  override fun getUnloadedModulesBelongingToScope(): Collection<UnloadedModuleDescription> =
    myScopes.flatMapTo(mutableSetOf()) { it.unloadedModulesBelongingToScope }

  override fun compare(file1: VirtualFile, file2: VirtualFile): Int {
    var result = 0

    ContainerUtil.process(myScopes, Processor { scope ->
      // ignore irrelevant scopes - they don't know anything about the files
      if (!scope.contains(file1) || !scope.contains(file2)) return@Processor true
      val cmp = scope.compare(file1, file2)
      if (result == 0) {
        result = cmp
        return@Processor true
      }
      if (cmp == 0) {
        return@Processor true
      }
      if (result > 0 == cmp > 0) {
        return@Processor true
      }
      // scopes disagree about the order - abort the voting
      result = 0
      false
    })
    return result
  }

  override fun isSearchInModuleContent(module: Module): Boolean {
    return myScopes.any { scope ->
      scope.isSearchInModuleContent(module)
    }
  }

  override fun isSearchInModuleContent(module: Module, testSources: Boolean): Boolean {
    return myScopes.any { scope ->
      scope.isSearchInModuleContent(module, testSources)
    }
  }

  override fun isSearchInLibraries(): Boolean {
    return myScopes.any { obj -> obj.isSearchInLibraries() }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is UnionScope) return false

    return hashSetOf(*myScopes) == hashSetOf(*other.myScopes)
  }

  override fun calcHashCode(): Int {
    return myScopes.contentHashCode()
  }

  override fun toString(): @NonNls String {
    return myScopes.joinToString(prefix = "Union: (", separator = ",", postfix = ")")
  }

  override fun uniteWith(scope: GlobalSearchScope): GlobalSearchScope {
    if (scope is UnionScope) {
      val newScopes = ArrayUtil.mergeArrays(myScopes, scope.myScopes)
      return create(newScopes)
    }
    return super.uniteWith(scope)
  }

  companion object {
    @JvmStatic
    fun create(scopes: Array<GlobalSearchScope>): GlobalSearchScope {
      if (scopes.size == 2) {
        val unionScope = tryCreateUnionFor2Scopes(scopes)
        if (unionScope != null) {
          return unionScope
        }
      }

      val project = scopes.firstNotNullOfOrNull(GlobalSearchScope::getProject)

      val flattened = scopes.flatMapTo(destination = hashSetOf()) { scope ->
        when {
          scope is UnionScope -> listOf(*scope.myScopes)
          scope === EMPTY_SCOPE -> emptyList()
          else -> listOf(scope)
        }
      }

      if (flattened.isEmpty()) return EMPTY_SCOPE
      if (flattened.size == 1) return flattened.single()

      val united = tryUniting(flattened)
      if (united.size == 1) return united.single()

      return UnionScope(project, united.toArray(EMPTY_ARRAY))
    }

    private fun tryCreateUnionFor2Scopes(scopes: Array<GlobalSearchScope>): GlobalSearchScope? {
      assert(scopes.size == 2)

      val scope0 = scopes[0]
      val scope1 = scopes[1]

      if (scope0 === EMPTY_SCOPE) return scope1
      if (scope1 === EMPTY_SCOPE) return scope0

      if (scope0 is UnionScope && scope1 is UnionScope) return null

      val project = scope0.project ?: scope1.project

      fun unionWithUnionScope(unionScope: UnionScope, otherScope: GlobalSearchScope, project: Project?): GlobalSearchScope {
        val scopes = unionScope.myScopes
        return if (otherScope in scopes) {
          unionScope
        }
        else {
          val toUnite = buildSet { addAll(scopes); add(otherScope) }

          val united = tryUniting(toUnite)
          if (united.size == 1) return united.single()

          UnionScope(project, united.toArray(EMPTY_ARRAY))
        }
      }

      return when {
        scope0 is UnionScope -> unionWithUnionScope(scope0, scope1, project)
        scope1 is UnionScope -> unionWithUnionScope(scope1, scope0, project)
        else -> UnionScope(project, scopes)
      }
    }

    private fun tryUniting(scopes: Set<GlobalSearchScope>): Collection<GlobalSearchScope> {
      require(scopes.size >= 2)

      val unionCapable = scopes.asSequence().filterIsInstance<UnionCapableScope>()
      for (scope in unionCapable) {
        val others = scopes - (scope as GlobalSearchScope)

        val (united, leftScopes) = scope.uniteWith(others) ?: continue

        if (leftScopes.isEmpty()) {
          return listOf(united)
        }

        return listOf(united) + tryUniting(leftScopes)
      }

      return scopes
    }
  }
}
