// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.search

import com.intellij.application.options.OptionsContainingConfigurable
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowSettingsUtilImpl.Companion.getConfigurables
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable
import com.intellij.ide.fileTemplates.impl.BundledFileTemplate
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jdom.IllegalDataException
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.system.exitProcess

private const val OPTIONS: @NonNls String = "options"
private const val CONFIGURABLE: @NonNls String = "configurable"
private const val ID: @NonNls String = "id"
private const val CONFIGURABLE_NAME: @NonNls String = "configurable_name"
private const val OPTION: @NonNls String = "option"
private const val NAME: @NonNls String = "name"
private const val PATH: @NonNls String = "path"
private const val HIT: @NonNls String = "hit"

private const val ROOT_ACTION_MODULE = "intellij.platform.ide"

/**
 * Used in installer's "build searchable options" step.
 *
 * To run locally, use "TraverseUi" run configuration (pass corresponding "idea.platform.prefix" property via VM options,
 * and choose correct main module).
 *
 * Pass `true` as the second parameter to have searchable options split by modules.
 */
private class TraverseUIStarter : ModernApplicationStarter() {
  override suspend fun start(args: List<String>) {
    try {
      buildSearchableOptions(
        outputPath = Path.of(args[1]),
        splitByResourcePath = args.size > 2 && args[2].toBoolean(),
        i18n = java.lang.Boolean.getBoolean("intellij.searchableOptions.i18n.enabled"),
      )
      println("Searchable options index builder completed")
      exitProcess(0)
    }
    catch (e: Throwable) {
      try {
        Logger.getInstance(javaClass).error("Searchable options index builder failed", e)
      }
      catch (ignored: Throwable) {
      }
      exitProcess(-1)
    }
  }
}

private fun addOptions(configurable: SearchableConfigurable,
                       options: Map<SearchableConfigurable, Set<OptionDescription>>,
                       roots: MutableMap<String, Element>,
                       splitByResourcePath: Boolean) {
  var configurable = configurable
  val configurableElement = createConfigurableElement(configurable)
  writeOptions(configurableElement, options.get(configurable)!!)

  if (configurable is ConfigurableWrapper) {
    val wrapped = configurable.configurable
    if (wrapped is SearchableConfigurable) {
      configurable = wrapped
    }
  }
  when (configurable) {
    is KeymapPanel -> {
      for ((key, value) in processKeymap(splitByResourcePath)) {
        val entryElement = createConfigurableElement(configurable)
        writeOptions(entryElement, value)
        addElement(roots, entryElement, key)
      }
    }
    is OptionsContainingConfigurable -> {
      processOptionsContainingConfigurable(configurable as OptionsContainingConfigurable, configurableElement)
    }
    is PluginManagerConfigurable -> {
      val optionDescriptions = TreeSet<OptionDescription>()
      wordsToOptionDescriptors(setOf(IdeBundle.message("plugin.manager.repositories")), null, optionDescriptions)
      for (description in optionDescriptions) {
        configurableElement.addContent(
          createOptionElement(path = null, hit = IdeBundle.message("plugin.manager.repositories"), word = description.option))
      }
    }
    is AllFileTemplatesConfigurable -> {
      for ((key, value) in processFileTemplates(splitByResourcePath)) {
        val entryElement = createConfigurableElement(configurable)
        writeOptions(entryElement, value)
        addElement(roots, entryElement, key)
      }
    }
  }

  val module = if (splitByResourcePath) getModuleByClass(configurable.originalClass) else ""
  addElement(roots, configurableElement, module)
}

@Throws(IOException::class)
private fun saveResults(outputPath: Path, roots: Map<String, Element>) {
  for ((module, value) in roots) {
    val output = if (module.isEmpty()) {
      outputPath.resolve(SearchableOptionsRegistrar.getSearchableOptionsXmlName())
    }
    else {
      val moduleDir = outputPath.resolve(module)
      Files.deleteIfExists(moduleDir.resolve("classpath.index"))
      moduleDir.resolve("search/$module.${SearchableOptionsRegistrar.getSearchableOptionsXmlName()}")
    }
    Files.createDirectories(output.parent)
    JDOMUtil.write(value, output)
    println("Output written to $output")
  }

  for (extension in TraverseUIHelper.helperExtensionPoint.extensionList) {
    extension.afterResultsAreSaved()
  }
}

private fun createConfigurableElement(configurable: SearchableConfigurable): Element {
  val configurableElement = Element(CONFIGURABLE)
  configurableElement.setAttribute(ID, configurable.id)
  configurableElement.setAttribute(CONFIGURABLE_NAME, configurable.displayName)
  return configurableElement
}

private fun addElement(roots: MutableMap<String, Element>, element: Element, module: String) {
  roots.computeIfAbsent(module) { Element(OPTIONS) }.addContent(element)
}

private fun processFileTemplates(splitByResourcePath: Boolean): Map<String, MutableSet<OptionDescription>> {
  val optionsRegistrar = SearchableOptionsRegistrar.getInstance()
  val options = HashMap<String, MutableSet<OptionDescription>>()
  val fileTemplateManager = FileTemplateManager.getDefaultInstance()
  processTemplates(optionsRegistrar, options, fileTemplateManager.allTemplates, splitByResourcePath)
  processTemplates(optionsRegistrar, options, fileTemplateManager.allPatterns, splitByResourcePath)
  processTemplates(optionsRegistrar, options, fileTemplateManager.allCodeTemplates, splitByResourcePath)
  processTemplates(optionsRegistrar, options, fileTemplateManager.allJ2eeTemplates, splitByResourcePath)
  return options
}

private fun processTemplates(registrar: SearchableOptionsRegistrar,
                             options: MutableMap<String, MutableSet<OptionDescription>>,
                             templates: Array<FileTemplate>,
                             splitByResourcePath: Boolean) {
  for (template in templates) {
    val module = if (splitByResourcePath && template is BundledFileTemplate) getModuleByTemplate(template) else ""
    collectOptions(registrar = registrar, options = options.computeIfAbsent(module) { TreeSet() }, text = template.name, path = null)
  }
}

private fun getModuleByTemplate(template: BundledFileTemplate): String {
  val url = template.toString()
  var path = checkNotNull(StringUtil.substringBefore(url, "fileTemplates")) { "Template URL doesn't contain 'fileTemplates' directory." }
  if (path.startsWith(URLUtil.JAR_PROTOCOL)) {
    path = path.removeSuffix(URLUtil.JAR_SEPARATOR)
  }
  return PathUtil.getFileName(path)
}

private fun collectOptions(registrar: SearchableOptionsRegistrar, options: MutableSet<OptionDescription>, text: String, path: String?) {
  for (word in registrar.getProcessedWordsWithoutStemming(text)) {
    options.add(OptionDescription(word, text, path))
  }
}

private fun processOptionsContainingConfigurable(configurable: OptionsContainingConfigurable, configurableElement: Element) {
  val optionsPath = configurable.processListOptions()
  val result = TreeSet<OptionDescription>()
  wordsToOptionDescriptors(optionsPath = optionsPath, path = null, result = result)
  val optionsWithPaths = configurable.processListOptionsWithPaths()
  for (path in optionsWithPaths.keys) {
    wordsToOptionDescriptors(optionsWithPaths.get(path)!!, path, result)
  }
  writeOptions(configurableElement, result)
}

private fun wordsToOptionDescriptors(optionsPath: Set<String>, path: String?, result: MutableSet<OptionDescription>) {
  val registrar = SearchableOptionsRegistrar.getInstance()
  for (opt in optionsPath) {
    for (word in registrar.getProcessedWordsWithoutStemming(opt)) {
      if (word != null) {
        result.add(OptionDescription(word, opt, path))
      }
    }
  }
}

private fun processKeymap(splitByResourcePath: Boolean): Map<String, Set<OptionDescription>> {
  val map = LinkedHashMap<String, MutableSet<OptionDescription>>()
  val actionManager = ActionManager.getInstance() as ActionManagerImpl
  val actionToPluginId: Map<String, PluginId> = if (splitByResourcePath) getActionToPluginId() else emptyMap()
  val componentName = "ActionManager"
  val searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance()
  val iterator = actionManager.actions(false).iterator()
  while (iterator.hasNext()) {
    val action = iterator.next()
    val module = if (splitByResourcePath) getModuleByAction(action, actionToPluginId) else ""
    val options = map.computeIfAbsent(module) { TreeSet() }
    val text = action.templatePresentation.text
    if (text != null) {
      collectOptions(registrar = searchableOptionsRegistrar, options = options, text = text, path = componentName)
    }

    val description = action.templatePresentation.description
    if (description != null) {
      collectOptions(registrar = searchableOptionsRegistrar, options = options, text = description, path = componentName)
    }
  }
  return map
}

private fun getActionToPluginId(): Map<String, PluginId> {
  val actionManager = ActionManagerEx.getInstanceEx()
  val actionToPluginId = HashMap<String, PluginId>()
  for (id in PluginId.getRegisteredIds()) {
    for (action in actionManager.getPluginActions(id)) {
      actionToPluginId.put(action, id)
    }
  }
  return actionToPluginId
}

private fun getModuleByAction(rootAction: AnAction, actionToPluginId: Map<String, PluginId>): String {
  val actions = ArrayDeque<AnAction>()
  actions.add(rootAction)
  while (!actions.isEmpty()) {
    val action = actions.remove()
    val module = getModuleByClass(action.javaClass)
    if (ROOT_ACTION_MODULE != module) {
      return module
    }
    if (action is ActionGroup) {
      actions.addAll(action.getChildren(null))
    }
  }

  val actionManager = ActionManager.getInstance()
  val id = actionToPluginId[actionManager.getId(rootAction)]
  if (id != null) {
    val plugin = getPlugin(id)
    if (plugin != null && plugin.name != PluginManagerCore.SPECIAL_IDEA_PLUGIN_ID.idString) {
      return PathUtil.getFileName(plugin.pluginPath.toString())
    }
  }
  return ROOT_ACTION_MODULE
}

private fun getModuleByClass(aClass: Class<*>): String {
  return PathUtil.getFileName(PathUtil.getJarPathForClass(aClass))
}

private fun writeOptions(configurableElement: Element, options: Set<OptionDescription>) {
  for (opt in options) {
    configurableElement.addContent(createOptionElement(path = opt.path, hit = opt.hit, word = opt.option))
  }
}

private fun createOptionElement(path: String?, hit: String?, word: String): Element {
  val optionElement = Element(OPTION)
  optionElement.setAttribute(NAME, word)
  if (path != null) {
    optionElement.setAttribute(PATH, path)
  }
  optionElement.setAttribute(HIT, hit)
  return optionElement
}

suspend fun buildSearchableOptions(outputPath: Path, splitByResourcePath: Boolean, i18n: Boolean = false) {
  val options = LinkedHashMap<SearchableConfigurable, Set<OptionDescription>>()
  val roots = HashMap<String, Element>()
  try {
    withContext(Dispatchers.EDT) {
      for (extension in TraverseUIHelper.helperExtensionPoint.extensionList) {
        extension.beforeStart()
      }
      SearchUtil.processConfigurables(
        getConfigurables(project = serviceAsync<ProjectManager>().defaultProject, withIdeSettings = true, checkNonDefaultProject = false),
        options,
        i18n,
      )
    }

    println("Found ${options.size} configurables")

    for (configurable in options.keys) {
      try {
        addOptions(configurable = configurable, options = options, roots = roots, splitByResourcePath = splitByResourcePath)
      }
      catch (e: IllegalDataException) {
        throw IllegalStateException(
          "Unable to process configurable '${configurable.id}', please check strings used in class: ${configurable.originalClass.name}", e
        )
      }
    }
  }
  finally {
    withContext(Dispatchers.EDT) {
      for (configurable in options.keys) {
        configurable.disposeUIResources()
      }
    }
  }

  saveResults(outputPath, roots)
}
