// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package org.jetbrains.intellij.build.bazel

import com.intellij.openapi.util.JDOMUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jdom.Content
import org.jdom.Element
import org.jetbrains.intellij.build.io.unmapBuffer
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import java.io.StringWriter
import java.nio.ByteOrder
import java.nio.LongBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.ExperimentalPathApi

// remember, that constants are not considered as used (for example, com.intellij.openapi.actionSystem.ActionPlaces and ActionPlaces.UNKNOWN)
internal class AddDependencyToImlByUsage {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      val m2Repo = Path.of(System.getProperty("user.home"), ".m2/repository")
      //val projectDir = Path.of(PathManager.getHomePath())
      val projectDir = Path.of("/Users/develar/projects/idea")
      val project = JpsSerializationManager.getInstance().loadProject(projectDir.toString(), mapOf("MAVEN_REPOSITORY" to m2Repo.toString()), true)

      val items = listOf(
        "intellij.platform.editor",
        "intellij.platform.lang.tests",
        "intellij.platform.usageView.impl",
        "intellij.platform.structureView.impl",
        //"intellij.platform.analysis",
        //"intellij.platform.analysis.impl",
        //"intellij.platform.foldings",
        //"intellij.platform.usageView.impl",
        //"intellij.platform.extensions",
        //"intellij.platform.duplicates.analysis",
        //"intellij.platform.projectModel.impl",
        //"intellij.platform.projectModel",
        //"intellij.platform.util",
        //"intellij.platform.util.ui",
        //"intellij.platform.util.ex",
        //"intellij.platform.core.impl",
        //"intellij.platform.ide.core",
        //"intellij.platform.lang",
        //"intellij.platform.lang.core",
        //"intellij.platform.lang.impl",
        //"intellij.xml.psi",
        //"intellij.xml.psi.impl",
        //"intellij.platform.execution",
        //"intellij.platform.execution.impl",
        //"intellij.platform.refactoring",
        //"intellij.platform.ide.impl",
        "intellij.platform.ide.progress",
        //"intellij.platform.statistics",
        "intellij.platform.core",
        //"intellij.platform.core.ui",
        //"intellij.platform.remote.core",
        //"intellij.platform.codeStyle",
        //"intellij.platform.util.base",
        //"intellij.platform.util.diff",
        //"intellij.platform.indexing",
        //"intellij.platform.ide",
        "intellij.platform.jps.model",
        "intellij.platform.testFramework",
        //"intellij.platform.credentialStore",
        //"intellij.platform.macro",
        //"intellij.platform.lvcs",
        //"intellij.platform.lvcs.impl",
        //"intellij.c",
        //"intellij.cidr.core",
      )

      AddDependencyToImlImpl(
        modules = items,
        indexDir = Path.of("/Users/develar/projects/idea-push/out/usage-index"),
      ).addDependencyToImlByUsage(project)
    }
  }
}

private class ClassIndex(
  @JvmField val referenced: LongArray,
  @JvmField val declared: LongArray,
) {
  fun isReferenced(nameHash: Long): Boolean {
    return Arrays.binarySearch(referenced, nameHash) >= 0
  }
}

private val EMPTY_INDEX = ClassIndex(referenced = longArrayOf(), declared = longArrayOf())

private fun readIndex(filePath: Path): ClassIndex {
  val buffer = try {
    FileChannel.open(filePath, StandardOpenOption.READ).use { fileChannel ->
      fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileChannel.size()).order(ByteOrder.LITTLE_ENDIAN)
    }
  }
  catch (_: NoSuchFileException) {
    return EMPTY_INDEX
  }

  try {
    val referencedSize = buffer.getInt()
    val declaredSize = buffer.getInt()
    val asLongBuffer = buffer.asLongBuffer()
    val r = readAsSet(referencedSize, asLongBuffer)
    val d = readAsSet(declaredSize, asLongBuffer)
    require(!asLongBuffer.hasRemaining())
    return ClassIndex(r, d)
  }
  finally {
    unmapBuffer(buffer)
  }
}

private fun readAsSet(size: Int, longBuffer: LongBuffer): LongArray {
  val result = LongArray(size)
  longBuffer.get(result)
  return result
}

@OptIn(ExperimentalPathApi::class)
private class AddDependencyToImlImpl(
  private val modules: List<String>,
  private val indexDir: Path,
) {
  private val moduleClassifierToIndex = ConcurrentHashMap<String, ClassIndex>()

  @Suppress("SameParameterValue")
  fun addDependencyToImlByUsage(project: JpsProject) {
    runBlocking(Dispatchers.Default) {
      val parallelism = 4
      val channel = Channel<JpsModule>(capacity = parallelism * 2)
      launch {
        for (module in project.modules) {
          channel.send(module)
        }
        channel.close()
      }

      repeat(parallelism) {
        launch {
          for (module in channel) {
            processModule(module)
          }
        }
      }
    }
  }

  private fun getModuleIndex(module: String, isTest: Boolean): ClassIndex {
    val classifier = "$module-${if (isTest) "t" else "p"}.bin"
    return moduleClassifierToIndex.computeIfAbsent(classifier) { module ->
      readIndex(indexDir.resolve(classifier))
    }
  }

  private fun processModule(module: JpsModule) {
    val moduleName = module.name
    val imlFile = JpsModelSerializationDataService.getBaseDirectory(module)!!.toPath().resolve("$moduleName.iml")

    val productionDependencies = ArrayList<String>()
    val testDependencies = ArrayList<String>()

    val dependencyModules = modules.filterTo(mutableListOf()) { moduleName != it }
    if (dependencyModules.isEmpty()) {
      return
    }

    val productionClassIndex = getModuleIndex(moduleName, isTest = false)
    val testClassIndex by lazy { getModuleIndex(moduleName, isTest = true) }

    val iterator = dependencyModules.iterator()
    while (iterator.hasNext()) {
      val dependencyModuleName = iterator.next()
      val depProdIndex = getModuleIndex(module = dependencyModuleName, isTest = false)
      if (checkIsUsed(dependencyModuleName, productionClassIndex, productionDependencies, depProdIndex) ||
          checkIsUsed(dependencyModuleName, testClassIndex, testDependencies, depProdIndex) ||
          checkIsUsed(dependencyModuleName, testClassIndex, testDependencies, getModuleIndex(module = dependencyModuleName, isTest = true))) {
        iterator.remove()
      }
    }

    val originalContent = Files.readString(imlFile)
    var updatedContent: CharSequence = originalContent
    for (dependency in productionDependencies) {
      val dom = JDOMUtil.load(updatedContent)
      addLineToXmlBeforeClosingTag(dom = dom, dependency = dependency, isTest = false)?.let {
        updatedContent = it
      }
    }
    for (dependency in testDependencies) {
      val dom = JDOMUtil.load(updatedContent)
      addLineToXmlBeforeClosingTag(dom = dom, dependency = dependency, isTest = true)?.let {
        updatedContent = it
      }
    }

    if (originalContent != updatedContent) {
      Files.writeString(imlFile, updatedContent)
    }
  }

  private fun checkIsUsed(
    dependencyModuleName: String,
    classIndex: ClassIndex,
    dependencies: ArrayList<String>,
    depClassIndex: ClassIndex,
  ): Boolean {
    for (classNameHash in depClassIndex.declared) {
      if (classIndex.isReferenced(classNameHash)) {
        dependencies.add(dependencyModuleName)
        return true
      }
    }
    return false
  }
}

private fun isModuleEntry(element: Content): Boolean {
  return element is Element && element.getAttributeValue("type") == "module"
}

private fun addLineToXmlBeforeClosingTag(dom: Element, dependency: String, isTest: Boolean): CharSequence? {
  val rootManager = dom.children.firstOrNull { it.name == "component" && it.getAttributeValue("name") == "NewModuleRootManager" } ?: return null
  val iterator = rootManager.children.iterator()
  // remove duplicates
  //val seen = ObjectOpenCustomHashSet<Element>(object : Hash.Strategy<Element> {
  //  override fun hashCode(o: Element?): Int = o?.name?.hashCode() ?: 0
  //
  //  override fun equals(a: Element?, b: Element?): Boolean = JDOMUtil.areElementsEqual(a, b)
  //})

  //@Suppress("VariableNeverRead")
  //var isChanged = false
  while (iterator.hasNext()) {
    val entry = iterator.next()
    //if (!seen.add(entry)) {
    //  iterator.remove()
    //  @Suppress("AssignedValueIsNeverRead")
    //  isChanged = isTest
    //}

    if (entry.name == "orderEntry" && entry.getAttributeValue("type") == "module" && entry.getAttributeValue("module-name") == dependency) {
      // already exists
      return null
    }
  }

  //seen.clear()

  //val contentList = rootManager.content.toMutableList()
  //rootManager.content.clear()

  val element = Element("orderEntry").setAttribute("type", "module").setAttribute("module-name", dependency)
  if (isTest) {
    element.setAttribute("scope", "TEST")
  }

  val insertionIndex = rootManager.content.indexOfFirst { current ->
    isModuleEntry(current) && (current as Element).getAttributeValue("module-name").let { name ->
      name != null && name > dependency
    }
  }
  if (insertionIndex == -1) {
    rootManager.addContent(rootManager.children.size - 1, element)
  }
  else {
    rootManager.addContent(insertionIndex, element)
  }
  //contentList.add(element)
  //rootManager.setContent(contentList)

  val writer = StringWriter()
  writer.write(prolog)
  writer.write('\n'.code)
  JDOMUtil.createOutputter("\n").output(dom, writer)
  return writer.buffer
}

//private fun sortList(contentList: MutableList<Content>) {
//  contentList.sortWith { a, b ->
//    val aIsModule = isModuleEntry(a)
//    val bIsModule = isModuleEntry(b)
//
//    if (!aIsModule && bIsModule) {
//      return@sortWith -1
//    }
//    if (aIsModule && !bIsModule) {
//      return@sortWith 1
//    }
//    if (!aIsModule) {
//      return@sortWith 0
//    }
//
//    // Both are module entries, compare their module-name attributes
//    val aN = (a as Element).getAttributeValue("module-name")
//    val bN = (b as Element).getAttributeValue("module-name")
//    aN.compareTo(bN)
//  }
//}

private const val prolog = """<?xml version="1.0" encoding="UTF-8"?>"""
