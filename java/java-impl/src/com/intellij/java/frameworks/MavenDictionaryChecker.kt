package com.intellij.java.frameworks

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import com.intellij.spellchecker.dictionary.DictionaryChecker
import com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintSet

internal class MavenDictionaryChecker : DictionaryChecker {
  override fun isCorrect(project: Project, word: String): Boolean {
    return getWords(project).contains(word)
  }

  private val CACHE_KEY: Key<ParameterizedCachedValue<Set<String>, Project>> = Key.create("MAVEN_SPELLING_WORDS")
  private val PROVIDER: ParameterizedCachedValueProvider<Set<String>, Project> = ParameterizedCachedValueProvider {
    Result.create(collectWords(it), ProjectRootManager.getInstance(it))
  }

  private fun getWords(project: Project): Set<String> {
    return CachedValuesManager.getManager(project)
      .getParameterizedCachedValue(project, CACHE_KEY, PROVIDER, false, project)
  }

  private val splitWordsRegex: Regex = Regex("[._-]")

  private fun collectWords(project: Project): Set<String> {
    val allStrings = createSmallMemoryFootprintSet<String>(1000)
    val visited = mutableSetOf<Library>()

    OrderEnumerator.orderEntries(project)
      .recursively()
      .forEachLibrary {
        if (visited.contains(it)) return@forEachLibrary true

        val mavenCoordinates = JavaLibraryUtil.getMavenCoordinates(it)
        if (mavenCoordinates != null) {
          allStrings.add(mavenCoordinates.groupId)
          allStrings.add(mavenCoordinates.artifactId)
        }

        visited.add(it)
        true
      }

    val allWords = createSmallMemoryFootprintSet<String>(100)

    allStrings.asSequence()
      .flatMap { it.split(splitWordsRegex) }
      .filter { it.length > 3 }
      .forEach { allWords.add(it) }

    return allWords
  }
}