package com.intellij.java.frameworks

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.libraries.Library
import com.intellij.spellchecker.dictionary.DictionaryChecker
import com.intellij.spellchecker.dictionary.DictionaryCheckerProvider
import com.intellij.util.containers.CollectionFactory.createSmallMemoryFootprintSet

internal class MavenDictionaryCheckerProvider : DictionaryCheckerProvider {
  override fun getChecker(project: Project): DictionaryChecker {
    return project.service<AllMavenDependenciesDictionary>()
  }
}

@Service(Service.Level.PROJECT)
internal class AllMavenDependenciesDictionary(private val project: Project) : DictionaryChecker, Disposable {
  init {
    val messageBusConnection = project.messageBus.connect(this)
    messageBusConnection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun exitDumbMode() = reset()
    })
    messageBusConnection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) = reset()
    })
    messageBusConnection.subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun modulesAdded(project: Project, modules: List<Module>) = reset()
      override fun moduleRemoved(project: Project, module: Module) = reset()
    })
  }

  @Volatile
  private var allMavenWords: Set<String>? = null

  override fun isCorrect(word: String): Boolean {
    var current = allMavenWords
    if (current == null) {
      current = collectWords(project)
      allMavenWords = current
    }
    return current.contains(word)
  }

  fun reset() {
    allMavenWords = null
  }

  override fun dispose() {
    // do nothing
  }
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
  for (allString in allStrings) {
    val parts = allString.split(splitWordsRegex)
    allWords.addAll(parts)
  }

  return allWords
}