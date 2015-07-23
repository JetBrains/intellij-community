/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.devkit.builder

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.PathUtil.getFileName
import com.intellij.util.containers.MultiMap
import org.jdom.Element
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.jetbrains.platform.loader.impl.repository.ProductionRepository
import org.jetbrains.platform.loader.repository.PlatformRepository
import org.jetbrains.platform.loader.repository.RuntimeModuleId
import java.io.File
import java.security.MessageDigest
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap

/**
 * @author nik
 */
public class PluginModulesListGenerator(private val project: JpsProject, private val ideDist: File) {
  public fun generateModulesList() {
    val jarInfos = IntellijJarInfo.collectJarsFromDist(ideDist)
    val plugins = MultiMap<String, IntellijJarInfo>()
    jarInfos.filter { it.getJarFile().name != "resources_en.jar" }.forEach { info -> plugins.putValue(info.getPluginName(), info) }
    plugins.remove(IntellijJarInfo.PLATFORM_PLUGIN)
    plugins.remove("Kotlin")
    plugins.remove("android")
    plugins.remove("ruby")

    val repo = ProductionRepository(ideDist)

    val moduleNameToPlugin = HashMap<String, String>()
    val singleModulePlugins = ArrayList<String>()
    var pluginsProcessed = 0
    for (pluginName in plugins.keySet()) {
      val jars = plugins.get(pluginName)
      val includedModules = jars.flatMap { jar -> jar.getIncludedModules().map {Pair(jar, it)} }
          .toMap({ findModule(it.second.getModuleId().getStringId()) }, { it.first})
      val includedModulesNames = includedModules.mapKeys { it.key.getName() }
      for (moduleName in includedModulesNames.keySet()) {
        if (moduleNameToPlugin.containsKey(moduleName) && moduleName != "ultimate-verifier") {
          println("Module '$moduleName' included in two plugins: $pluginName and ${moduleNameToPlugin.get(moduleName)}")
        }
        moduleNameToPlugin.put(moduleName, pluginName)
      }
      if (includedModules.size() == 1 && jars.size() == 1) {
        singleModulePlugins.add(pluginName)
        continue
      }

      val (pluginXml, mainModule) = findPluginXml(pluginName, includedModules.keySet())
      println("$pluginName ($pluginXml):)")
      val md5 = MessageDigest.getInstance("MD5")
      val buffer = ByteArray(16 * 1024)
      val libraryName = includedModulesNames.flatMap { collectDependencies(repo, RuntimeModuleId.module(it.key)) }
          .filter { it.getStringId().startsWith(RuntimeModuleId.LIB_NAME_PREFIX) }
          .flatMap { module ->
        repo.getModuleRootPaths(module).map { Pair(module, File(it))}
      }.toMap({
        RuntimeModuleDescriptorsGenerator.Bytes(RuntimeModuleDescriptorsGenerator.calcDigest(md5, buffer, it.second))
      }, { it.first })
      val includedLibraries = jars.toMap { it }.filterKeys { it.getIncludedModules().isEmpty() }.mapKeysTo(LinkedHashMap<String, IntellijJarInfo>()) {
        val bytes = RuntimeModuleDescriptorsGenerator.Bytes(RuntimeModuleDescriptorsGenerator.calcDigest(md5, buffer, it.key.getJarFile()))
        libraryName[bytes]?.getStringId()// ?: "Unknown library (${it.key.getJarFile()})"
      }.filterKeys { it != null }

      if (!includedModulesNames.isEmpty()) {
        println(" modules:\n  ${includedModulesNames.keySet().join("\n  ")}")
      }
      if (!includedLibraries.isEmpty()) {
        println(" libraries:\n  ${includedLibraries.keySet().join("\n  ")}")
      }
      val afterTags = listOf("name", "id", "description", "version", "vendor")
      val text = pluginXml.readText()
      val pluginXmlRoot = JDOMUtil.load(pluginXml)
      val modulesAndScopes = includedModules.mapTo(ArrayList<Pair<String, String?>>()) { Pair(it.key.getName(), getScope(it.value, it.key, pluginXmlRoot, mainModule)) }
      includedLibraries.mapTo(modulesAndScopes) { Pair(it.key, getScope(it.value, null, pluginXmlRoot, mainModule))}
      val modulesTag = "  <modules>\n${modulesAndScopes.map { "    <module${it.second?.let{" scope=\"$it\""}?:""}>${it.first}</module>" }.join("\n")}\n  </modules>\n"

      val insertStart: Int
      val insertEnd: Int
      if (text.contains("<modules>")) {
        insertStart = Math.min(text.indexOf("<modules>"), text.indexOf("  <modules>"))
        insertEnd = text.indexOf('\n', text.indexOf("</modules>")) + 1
      }
      else {
        val insertAfter = afterTags.map { text.indexOf("</$it>") }.filter { it != -1 && !text.substring(0, it).contains("<extensions") }.max()
            ?: throw RuntimeException("Cannot find place to insert modules in $pluginXml")
        insertStart = text.indexOf('\n', insertAfter) + 1
        insertEnd = insertStart
      }
      val modifiedText = text.replaceRange(insertStart, insertEnd, modulesTag)
      if (pluginName in setOf("GwtStudio", "sass")) {
        pluginXml.writeText(modifiedText)
      }

      pluginsProcessed++
    }
    println("=== ($pluginsProcessed plugins processed)")
    println("Single-module plugins (${singleModulePlugins.size()}): \n ${singleModulePlugins.join("\n ")}")
  }

  private fun findModule(name: String) =
      project.getModules().firstOrNull { it.getName() == name } ?: throw RuntimeException("Module '$name' not found")
}

private fun getScope(jar: IntellijJarInfo, module: JpsModule?, pluginXmlRoot: Element, mainModule: JpsModule): String? {
  if (module == mainModule) return null;

  if (jar.getIncludedModules().any {it.getModuleId().getStringId() == mainModule.getName()}) {
    return "embedded"
  }
  val dir = jar.getJarFile().getParentFile()
  val includeInIde = dir.getName() == "lib" && dir.getParentFile().getName() == jar.getPluginName()
  val externalBuildJars = pluginXmlRoot.getChildren().flatMap { it.getChildren("compileServer.plugin") }.flatMap { it.getAttributeValue("classpath").split(',') }
  val includeInExternalBuild = externalBuildJars.map(PathUtil::getFileName).contains(jar.getJarFile().name)
  if (includeInExternalBuild) {
    return if (includeInIde) "IDE,build" else "build"
  }
  if (!includeInIde) {
    return "runtime"
  }
  return null
}

private val ignoredModules = setOf("community-resources", "idea")

private fun collectDependencies(repo: PlatformRepository, module: RuntimeModuleId, processed: MutableSet<RuntimeModuleId> = HashSet<RuntimeModuleId>()): Collection<RuntimeModuleId> {
  if (module.getStringId() !in ignoredModules && processed.add(module)) {
    repo.getRequiredModule(module).getDependencies().forEach { collectDependencies(repo, it, processed) }
  }
  return processed
}

private fun findPluginXml(pluginName: String, modules: Set<JpsModule>): Pair<File, JpsModule> {
  for (module in modules) {
    for (root in module.getSourceRoots()) {
      if (JavaModuleSourceRootTypes.PRODUCTION.contains(root.getRootType())) {
        val prefix = if (root.getRootType() == JavaSourceRootType.SOURCE)
          root.asTyped(JavaSourceRootType.SOURCE)!!.getProperties().getPackagePrefix().replace('.', '/')
        else
          root.asTyped(JavaResourceRootType.RESOURCE)!!.getProperties().getRelativeOutputPath()
        val pluginXml = File(root.getFile(), StringUtil.trimStart(StringUtil.trimStart("META-INF/plugin.xml", prefix), "/"))
        if (pluginXml.isFile()) {
          return Pair(pluginXml, module)
        }
      }
    }
  }
  throw RuntimeException("plugin.xml not found in '$pluginName' modules: ${modules.map {it.getName()}}")
}


public fun main(args: Array<String>) {
  val model = JpsSerializationManager.getInstance().loadModel(args[0], null)
  val dist = File(args[1])
  try {
    PluginModulesListGenerator(model.getProject(), dist).generateModulesList()
  }
  catch(e: Throwable) {
    e.printStackTrace()
  }
  finally {
    System.exit(0)
  }
}

