// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.search

import com.intellij.application.options.OptionsContainingConfigurable
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable
import com.intellij.ide.fileTemplates.impl.BundledFileTemplate
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.*
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.*
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jdom.IllegalDataException
import org.jdom.Namespace
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
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
      doBuildSearchableOptions(
        options = LinkedHashMap<SearchableConfigurable, Set<OptionDescription>>(),
        outputPath = Path.of(args[1]),
        splitByResourcePath = args.size > 2 && args[2].toBoolean(),
        i18n = java.lang.Boolean.getBoolean("intellij.searchableOptions.i18n.enabled"),
      )
      exitProcess(0)
    }
    catch (e: Throwable) {
      try {
        logger<TraverseUIStarter>().error("Searchable options index builder failed", e)
      }
      catch (ignored: Throwable) {
      }
      exitProcess(-1)
    }
  }
}

private fun addOptions(
  originalConfigurable: SearchableConfigurable,
  options: Map<SearchableConfigurable, Set<OptionDescription>>,
  roots: MutableMap<String, Element>,
  splitByResourcePath: Boolean,
) {
  val configurableElement = createConfigurableElement(originalConfigurable)
  writeOptions(configurableElement, options.get(originalConfigurable)!!)

  val configurable = if (originalConfigurable is ConfigurableWrapper) {
    originalConfigurable.configurable as? SearchableConfigurable ?: originalConfigurable
  }
  else {
    originalConfigurable
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
      processOptionsContainingConfigurable(configurable, configurableElement)
    }
    is PluginManagerConfigurable -> {
      val optionDescriptions = TreeSet<OptionDescription>()
      wordsToOptionDescriptors(optionPath = setOf(IdeBundle.message("plugin.manager.repositories")), path = null, result = optionDescriptions)
      for (description in optionDescriptions) {
        configurableElement.addContent(createOptionElement(path = null, hit = IdeBundle.message("plugin.manager.repositories"), word = description.option))
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
  addElement(roots = roots, element = configurableElement, module = module)
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
  val configurableElement = Element(true, CONFIGURABLE, Namespace.NO_NAMESPACE)
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
  wordsToOptionDescriptors(optionPath = optionsPath, path = null, result = result)
  val optionsWithPaths = configurable.processListOptionsWithPaths()
  for (path in optionsWithPaths.keys) {
    wordsToOptionDescriptors(optionsWithPaths.get(path)!!, path, result)
  }
  writeOptions(configurableElement, result)
}

private fun wordsToOptionDescriptors(optionPath: Set<String>, path: String?, result: MutableSet<OptionDescription>) {
  val registrar = SearchableOptionsRegistrar.getInstance()
  for (opt in optionPath) {
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
  val optionElement = Element(true, OPTION, Namespace.NO_NAMESPACE)
  optionElement.setAttribute(NAME, word)
  if (path != null) {
    optionElement.setAttribute(PATH, path)
  }
  optionElement.setAttribute(HIT, hit)
  return optionElement
}

@VisibleForTesting
suspend fun buildSearchableOptions(outputPath: Path, splitByResourcePath: Boolean, i18n: Boolean = false) {
  val options = LinkedHashMap<SearchableConfigurable, Set<OptionDescription>>()
  try {
    doBuildSearchableOptions(options = options, outputPath = outputPath, splitByResourcePath = splitByResourcePath, i18n = i18n)
  }
  finally {
    if (!options.isEmpty()) {
      withContext(Dispatchers.EDT) {
        blockingContext {
          for (configurable in options.keys) {
            configurable.disposeUIResources()
          }
        }
      }
    }
  }
}

@VisibleForTesting
suspend fun doBuildSearchableOptions(
  options: MutableMap<SearchableConfigurable, Set<OptionDescription>>,
  outputPath: Path,
  splitByResourcePath: Boolean,
  i18n: Boolean = false,
) {
  assert(AppMode.isHeadless())

  val roots = HashMap<String, Element>()

  val defaultProject = serviceAsync<ProjectManager>().defaultProject
  withContext(Dispatchers.EDT) {
    blockingContext {
      for (extension in TraverseUIHelper.helperExtensionPoint.extensionList) {
        extension.beforeStart()
      }
      processConfigurables(
        configurables = ShowSettingsUtilImpl.configurables(
          project = defaultProject,
          withIdeSettings = true,
          checkNonDefaultProject = false,
        ),
        options = options,
        i18n = i18n,
      )
    }
  }

  logger<TraverseUIStarter>().info("Found ${options.size} configurables")

  for (configurable in options.keys) {
    try {
      addOptions(originalConfigurable = configurable, options = options, roots = roots, splitByResourcePath = splitByResourcePath)
    }
    catch (e: IllegalDataException) {
      throw IllegalStateException(
        "Unable to process configurable '${configurable.id}', please check strings used in class: ${configurable.originalClass.name}", e
      )
    }
  }

  saveResults(outputPath, roots)
}

private fun processConfigurables(
  configurables: Sequence<Configurable>,
  options: MutableMap<SearchableConfigurable, Set<OptionDescription>>,
  i18n: Boolean,
) {
  for (configurable in configurables) {
    if (configurable !is SearchableConfigurable) {
      continue
    }

    val configurableOptions = TreeSet<OptionDescription>()
    options.put(configurable, configurableOptions)

    for (extension in TraverseUIHelper.helperExtensionPoint.extensionList) {
      extension.beforeConfigurable(configurable, configurableOptions)
    }

    if (configurable is MasterDetails) {
      configurable.initUi()
      SearchUtil.processComponent(configurable, configurableOptions, configurable.master, i18n)
      SearchUtil.processComponent(configurable, configurableOptions, configurable.details.component, i18n)
    }
    else {
      SearchUtil.processComponent(configurable, configurableOptions, configurable.createComponent(), i18n)
      val unwrapped = SearchUtil.unwrapConfigurable(configurable)
      if (unwrapped is CompositeConfigurable<*>) {
        unwrapped.disposeUIResources()
        val children = unwrapped.configurables
        for (child in children) {
          val childConfigurableOptions: Set<OptionDescription> = TreeSet()
          options.put(SearchableConfigurableAdapter(configurable, child), childConfigurableOptions)

          if (child is SearchableConfigurable) {
            SearchUtil.processUILabel(child.displayName, childConfigurableOptions, null, i18n)
          }
          val component = child.createComponent()
          if (component != null) {
            SearchUtil.processComponent(component, childConfigurableOptions, null, i18n)
          }

          configurableOptions.removeAll(childConfigurableOptions)
        }
      }
    }

    for (extension in TraverseUIHelper.helperExtensionPoint.extensionList) {
      extension.afterConfigurable(configurable, configurableOptions)
    }
  }
}

private class SearchableConfigurableAdapter(
  private val original: SearchableConfigurable,
  private val delegate: UnnamedConfigurable,
) : SearchableConfigurable {
  override fun getId(): String = original.id

  @Nls(capitalization = Nls.Capitalization.Title)
  override fun getDisplayName(): String = original.displayName

  override fun getOriginalClass(): Class<*> = if (delegate is SearchableConfigurable) delegate.originalClass else delegate.javaClass

  override fun createComponent() = null

  override fun isModified(): Boolean = false

  override fun apply() {
  }

  override fun toString(): String = displayName
}