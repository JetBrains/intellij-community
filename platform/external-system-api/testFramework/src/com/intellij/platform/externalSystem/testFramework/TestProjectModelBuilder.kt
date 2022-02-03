// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.roots.DependencyScope
import java.util.*

fun project(name: String = "project",
            projectPath: String,
            systemId: ProjectSystemId = TEST_EXTERNAL_SYSTEM_ID,
            init: Project.() -> Unit) = Project().also {
  it.name = name
  it.projectPath = projectPath
  it.systemId = systemId
  it.init()
}

interface Node {
  fun render(builder: StringBuilder, indent: String)
}

abstract class AbstractNode<DataType : Any?>(val type: String) : Node {
  lateinit var systemId: ProjectSystemId
  private var parent: AbstractNode<*>? = null
  val children = arrayListOf<AbstractNode<*>>()
  val props = hashMapOf<String, String>()

  protected fun find(predicate: (AbstractNode<*>) -> Boolean): AbstractNode<*>? {
    var rootNode: AbstractNode<*> = this
    while (rootNode.parent != null) {
      rootNode = rootNode.parent!!
    }
    val queue: Queue<AbstractNode<*>> = LinkedList()
    queue.add(rootNode)
    while (queue.isNotEmpty()) {
      val node = queue.remove()
      if (predicate.invoke(node)) return node
      queue.addAll(node.children)
    }
    return null
  }

  fun <T : Node> initChild(node: T, init: T.() -> Unit): T {
    (node as AbstractNode<*>).systemId = this.systemId
    (node as AbstractNode<*>).parent = this
    children.add(node)
    node.init()
    return node
  }

  fun <T : Any> ext(key: com.intellij.openapi.externalSystem.model.Key<T>, model: T, init: T.() -> Unit = {}) =
    initChild(OtherNode(key, model)) { init.invoke(model) }

  abstract fun createDataNode(parentData: Any? = null): DataNode<DataType>

  override fun render(builder: StringBuilder, indent: String) {
    builder.append("$indent<$type${renderProps()}")
    if (children.isNotEmpty()) {
      builder.appendLine(">")
      children.forEach { it.render(builder, "$indent  ") }
      builder.appendLine("$indent</$type>")
    }
    else {
      builder.appendLine("/>")
    }
  }

  private fun renderProps(): String {
    val builder = StringBuilder()
    for ((attr, value) in props) {
      builder.append(" $attr=\"$value\"")
    }
    return builder.toString()
  }

  override fun toString() = StringBuilder().also { render(it, "") }.toString()
}

abstract class NamedNode<T : Any>(type: String) : AbstractNode<T>(type) {
  var name: String
    get() = props["name"]!!
    set(value) {
      props["name"] = value
    }
}

class Project : NamedNode<ProjectData>("project") {
  var projectPath: String
    get() = props["projectPath"]!!
    set(value) {
      props["projectPath"] = value
    }

  fun module(name: String = "module",
             externalProjectPath: String = projectPath,
             moduleFilePath: String? = null,
             init: Module.() -> Unit = {}) =
    initChild(Module()) {
      this.name = name
      this.moduleFileDirectoryPath = moduleFilePath ?: projectPath
      this.externalProjectPath = externalProjectPath
      init.invoke(this)
    }

  override fun createDataNode(parentData: Any?): DataNode<ProjectData> {
    val projectData = ProjectData(systemId, name, projectPath, projectPath)
    return DataNode(ProjectKeys.PROJECT, projectData, null)
  }
}

class Module : NamedNode<ModuleData>("module") {
  var externalProjectPath: String
    get() = props["externalProjectPath"]!!
    set(value) {
      props["externalProjectPath"] = value
    }
  var moduleFileDirectoryPath: String
    get() = props["moduleFileDirectoryPath"]!!
    set(value) {
      props["moduleFileDirectoryPath"] = value
    }

  val moduleData by lazy { ModuleData(name, systemId, ModuleTypeId.JAVA_MODULE, name, moduleFileDirectoryPath, externalProjectPath) }

  fun module(name: String = "module",
             externalProjectPath: String,
             moduleFilePath: String? = null,
             init: Module.() -> Unit = {}) =
    initChild(Module()) {
      this.name = name
      this.moduleFileDirectoryPath = moduleFilePath ?: externalProjectPath
      this.externalProjectPath = externalProjectPath
      init.invoke(this)
    }

  fun contentRoot(path: String = externalProjectPath, init: ContentRoot.() -> Unit = {}) =
    initChild(ContentRoot()) {
      this.root = path
      init.invoke(this)
    }

  fun lib(name: String, level: LibraryLevel = LibraryLevel.PROJECT, unresolved: Boolean = false, init: Lib.() -> Unit = {}) =
    initChild(Lib()) {
      this.name = name
      this.level = level
      this.unresolved = unresolved
      init.invoke(this)
    }

  fun moduleDependency(targetModuleName: String, scope: DependencyScope = DependencyScope.COMPILE, init: ModuleDependency.() -> Unit = {}) =
    initChild(ModuleDependency()) {
      this.name = targetModuleName
      this.scope = scope
      init.invoke(this)
    }

  override fun createDataNode(parentData: Any?) = DataNode(ProjectKeys.MODULE, moduleData, null)
}

class ContentRoot : AbstractNode<ContentRootData>("contentRoot") {
  var root: String
    get() = props["root"]!!
    set(value) {
      props["root"] = value
    }
  val folders = LinkedHashMap<ExternalSystemSourceType, MutableList<ContentRootData.SourceRoot>>()

  fun folder(type: ExternalSystemSourceType, relativePath: String, packagePrefix: String? = null) {
    folders.computeIfAbsent(type) { ArrayList() }.add(ContentRootData.SourceRoot("$root/$relativePath", packagePrefix))
  }

  override fun createDataNode(parentData: Any?): DataNode<ContentRootData> {
    val contentRootData = ContentRootData(systemId, root)
    for ((type, list) in folders) {
      list.forEach { contentRootData.storePath(type, it.path, it.packagePrefix) }
    }
    return DataNode(ProjectKeys.CONTENT_ROOT, contentRootData, null)
  }
}

class Lib : NamedNode<LibraryDependencyData>("lib") {
  var level: LibraryLevel
    get() = LibraryLevel.valueOf(props["level"]!!)
    set(value) {
      props["level"] = value.name
    }
  var unresolved: Boolean
    get() = props["unresolved"]!!.toBoolean()
    set(value) {
      props["unresolved"] = value.toString()
    }
  val roots = LinkedHashMap<LibraryPathType, MutableList<String>>()

  fun roots(type: LibraryPathType, vararg roots: String) {
    this.roots.computeIfAbsent(type) { ArrayList() }.addAll(roots)
  }

  override fun createDataNode(parentData: Any?): DataNode<LibraryDependencyData> {
    check(parentData is ModuleData)
    val libraryData = LibraryData(systemId, name, unresolved)
    for ((type, list) in roots) {
      list.forEach { libraryData.addPath(type, it) }
    }
    val libraryDependencyData = LibraryDependencyData(parentData, libraryData, level)
    return DataNode(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData, null)
  }
}

class ModuleDependency : NamedNode<ModuleDependencyData>("moduleDependency") {
  var scope: DependencyScope
    get() = DependencyScope.valueOf(props["scope"]!!)
    set(value) {
      props["scope"] = value.name
    }

  override fun createDataNode(parentData: Any?): DataNode<ModuleDependencyData> {
    check(parentData is ModuleData)
    val targetModule = find { it is Module && it.name == name } as? Module ?:
                       throw IllegalStateException("Can not find module '$name'")
    val moduleDependencyData = ModuleDependencyData(parentData, targetModule.moduleData).also { it.scope = scope }
    return DataNode(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData, null)
  }
}

class OtherNode<T : Any>(private val key: com.intellij.openapi.externalSystem.model.Key<T>,
                         private val model: T) : AbstractNode<T>(key.dataType) {
  override fun createDataNode(parentData: Any?): DataNode<T> {
    return DataNode(key, model, null)
  }
}

fun <DataType : Any?> AbstractNode<DataType>.toDataNode(parentData: Any? = null): DataNode<DataType> {
  val dataNode = createDataNode(parentData)
  for (child in children) {
    dataNode.addChild(child.toDataNode(dataNode.data))
  }
  return dataNode
}
