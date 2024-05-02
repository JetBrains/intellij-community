// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.search

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.application.options.OptionsContainingConfigurable
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.ide.fileTemplates.FileTemplate
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable
import com.intellij.ide.fileTemplates.impl.BundledFileTemplate
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.*
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jdom.IllegalDataException
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.system.exitProcess

private val LOG = logger<TraverseUIStarter>()

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
        options = LinkedHashMap(),
        outputPath = Path.of(args[1]).toAbsolutePath().normalize(),
        i18n = java.lang.Boolean.getBoolean("intellij.searchableOptions.i18n.enabled"),
      )
      exitProcess(0)
    }
    catch (e: Throwable) {
      try {
        LOG.error("Searchable options index builder failed", e)
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
  roots: MutableMap<OptionSetId, MutableList<ConfigurableEntry>>,
) {
  val configurableEntry = createConfigurableElement(originalConfigurable)
  writeOptions(configurableEntry, options.get(originalConfigurable)!!)

  val configurable = if (originalConfigurable is ConfigurableWrapper) {
    originalConfigurable.configurable as? SearchableConfigurable ?: originalConfigurable
  }
  else {
    originalConfigurable
  }

  when (configurable) {
    is KeymapPanel -> {
      for ((key, value) in processKeymap()) {
        val entryElement = createConfigurableElement(configurable)
        writeOptions(entryElement, value)
        addElement(roots, entryElement, key)
      }
    }
    is OptionsContainingConfigurable -> {
      processOptionsContainingConfigurable(configurable, configurableEntry)
    }
    is PluginManagerConfigurable -> {
      val optionDescriptions = TreeSet<OptionDescription>()
      wordsToOptionDescriptors(optionPath = setOf(IdeBundle.message("plugin.manager.repositories")), path = null, result = optionDescriptions)
      configurableEntry.entries.add(SearchableOptionEntry(words = optionDescriptions.map { it.option.trim() }, hit = IdeBundle.message("plugin.manager.repositories")))
    }
    is AllFileTemplatesConfigurable -> {
      for ((key, value) in processFileTemplates()) {
        val entryElement = createConfigurableElement(configurable)
        writeOptions(entryElement, value)
        addElement(roots, entryElement, key)
      }
    }
  }

  addElement(roots = roots, entry = configurableEntry, module = getSetIdByClass(configurable.originalClass))
}

private data class SearchableOptionFile(@JvmField val module: OptionSetId, @JvmField val item: SearchableOptionSetIndexItem)

@Serializable
private data class SearchableOptionSetIndexItem(@JvmField val file: String, @JvmField val hash: Long, @JvmField val size: Long)

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
private suspend fun saveResults(outDir: Path, roots: Map<OptionSetId, List<ConfigurableEntry>>) {
  LOG.info("save to $outDir")
  val fileDescriptors = withContext(Dispatchers.IO.limitedParallelism(4)) {
    val createdDirs = HashSet<Path>()
    if (java.lang.Boolean.getBoolean("intellij.searchableOptions.clean.out") && Files.isDirectory(outDir) && outDir.endsWith("searchable-options")) {
      Files.newDirectoryStream(outDir)
        .use { it.toList() }
        .forEach { NioFiles.deleteRecursively(it) }
    }
    Files.createDirectories(outDir)
    createdDirs.add(outDir)

    val serializer = ConfigurableEntry.serializer()
    roots.map { (module, value) ->
      async {
        val hash = Hashing.komihash5_0().hashStream()
        hash.putString(module.pluginId.idString)
        hash.putString(module.moduleName ?: "")

        val fileName = (if (module.moduleName == null) "p-${module.pluginId.idString}" else "m-${module.moduleName}") +
                       "-" + SearchableOptionsRegistrar.getSearchableOptionsName() + ".json"
        val file = outDir.resolve(fileName)
        Files.newBufferedWriter(file).use { writer ->
          hash.putInt(value.size)
          for (entry in value) {
            val encoded = Json.encodeToString(serializer, entry)
            hash.putString(encoded)
            writer.write(encoded)
            writer.append('\n')
          }
        }

        SearchableOptionFile(
          module = module,
          item = SearchableOptionSetIndexItem(file = fileName, hash = hash.asLong, size = Files.size(file)),
        )
      }
    }
  }.map { it.getCompleted() }

  withContext(Dispatchers.IO) {
    val contentFile = outDir.resolve("content.json")
    val existing: Map<String, List<SearchableOptionSetIndexItem>> = if (Files.exists(contentFile)) {
      Files.newInputStream(contentFile).use {
        Json.decodeFromStream(it)
      }
    }
    else {
      emptyMap()
    }

    Files.writeString(contentFile, Json.encodeToString(
      fileDescriptors
        .groupBy(keySelector = { it.module.moduleName ?: it.module.pluginId.idString })
        .mapValues { entry ->
          val item = entry.value.single().item
          listOf(SearchableOptionSetIndexItem(file = item.file, hash = item.hash, size = item.size)) + (existing.get(entry.key) ?: emptyList())
        }
    ))
  }

  for (extension in TraverseUIHelper.helperExtensionPoint.extensionList) {
    extension.afterResultsAreSaved()
  }
}

private fun createConfigurableElement(configurable: SearchableConfigurable): ConfigurableEntry {
  return ConfigurableEntry(id = configurable.id, name = configurable.displayName, entries = mutableListOf())
}

private fun addElement(roots: MutableMap<OptionSetId, MutableList<ConfigurableEntry>>, entry: ConfigurableEntry, module: OptionSetId) {
  roots.computeIfAbsent(module) { ArrayList() }.add(entry)
}

private fun processFileTemplates(): Map<OptionSetId, MutableSet<OptionDescription>> {
  val optionsRegistrar = SearchableOptionsRegistrar.getInstance()
  val options = LinkedHashMap<OptionSetId, MutableSet<OptionDescription>>()
  val fileTemplateManager = FileTemplateManager.getDefaultInstance()
  processTemplates(optionsRegistrar, options, fileTemplateManager.allTemplates)
  processTemplates(optionsRegistrar, options, fileTemplateManager.allPatterns)
  processTemplates(optionsRegistrar, options, fileTemplateManager.allCodeTemplates)
  processTemplates(optionsRegistrar, options, fileTemplateManager.allJ2eeTemplates)
  return options
}

private fun processTemplates(
  registrar: SearchableOptionsRegistrar,
  options: MutableMap<OptionSetId, MutableSet<OptionDescription>>,
  templates: Array<FileTemplate>,
) {
  for (template in templates) {
    val module = if (template is BundledFileTemplate) getSetIdByPluginDescriptor(template.pluginDescriptor) else CORE_SET_ID
    collectOptions(registrar = registrar, options = options.computeIfAbsent(module) { TreeSet() }, text = template.name, path = null)
  }
}

private fun collectOptions(registrar: SearchableOptionsRegistrar, options: MutableSet<OptionDescription>, text: String, path: String?) {
  for (word in registrar.getProcessedWordsWithoutStemming(text)) {
    options.add(OptionDescription(word, text, path))
  }
}

private fun processOptionsContainingConfigurable(configurable: OptionsContainingConfigurable, configurableElement: ConfigurableEntry) {
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

private fun processKeymap(): Map<OptionSetId, Set<OptionDescription>> {
  val map = LinkedHashMap<OptionSetId, MutableSet<OptionDescription>>()
  val actionManager = ActionManager.getInstance() as ActionManagerImpl
  val actionToPluginId = getActionToPluginId(actionManager)
  val componentName = "ActionManager"
  val searchableOptionsRegistrar = SearchableOptionsRegistrar.getInstance()
  val iterator = actionManager.actions(false).iterator()
  while (iterator.hasNext()) {
    val action = iterator.next()
    val module = getModuleByAction(action, actionToPluginId)
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

private fun getActionToPluginId(actionManager: ActionManagerImpl): Map<String, PluginId> {
  val actionToPluginId = HashMap<String, PluginId>()
  for (id in PluginId.getRegisteredIds()) {
    for (action in actionManager.getPluginActions(id)) {
      actionToPluginId.put(action, id)
    }
  }
  return actionToPluginId
}

private fun getModuleByAction(rootAction: AnAction, actionToPluginId: Map<String, PluginId>): OptionSetId {
  val actions = ArrayDeque<AnAction>()
  actions.add(rootAction)
  while (!actions.isEmpty()) {
    val action = actions.remove()
    val module = getSetIdByClass(action.javaClass)
    if (module != CORE_SET_ID) {
      return module
    }
    if (action is ActionGroup) {
      actions.addAll(action.getChildren(null))
    }
  }

  val pluginDescriptor = actionToPluginId.get(ActionManager.getInstance().getId(rootAction))?.let { PluginManagerCore.getPlugin(it) }
                         ?: return CORE_SET_ID
  return getSetIdByPluginDescriptor(pluginDescriptor)
}

private val CORE_SET_ID = OptionSetId(pluginId = PluginManagerCore.CORE_ID, moduleName = null)

private fun getSetIdByClass(aClass: Class<*>): OptionSetId {
  val classLoader = aClass.classLoader
  if (classLoader is PluginAwareClassLoader) {
    return getSetIdByPluginDescriptor(classLoader.pluginDescriptor)
  }
  else {
    return CORE_SET_ID
  }
}
private fun getSetIdByPluginDescriptor(pluginDescriptor: PluginDescriptor): OptionSetId {
  if (pluginDescriptor.pluginId == PluginManagerCore.CORE_ID) {
    return CORE_SET_ID
  }
  else {
    return OptionSetId(
      pluginId = pluginDescriptor.pluginId,
      moduleName = (pluginDescriptor as IdeaPluginDescriptorImpl).moduleName?.takeIf { !it.contains('/') },
    )
  }
}

private fun writeOptions(configurableElement: ConfigurableEntry, options: Set<OptionDescription>) {
  for ((key, item) in options.groupBy { it.hit?.trim() to it.path?.trim() }) {
    configurableElement.entries.add(SearchableOptionEntry(words = item.mapNotNull { it.option?.trim() }, hit = key.first, path = key.second))
  }
}

@VisibleForTesting
suspend fun buildSearchableOptions(outputPath: Path, i18n: Boolean = false) {
  val options = LinkedHashMap<SearchableConfigurable, Set<OptionDescription>>()
  try {
    doBuildSearchableOptions(options = options, outputPath = outputPath, i18n = i18n)
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
  i18n: Boolean = false,
) {
  assert(AppMode.isHeadless())

  val roots = LinkedHashMap<OptionSetId, MutableList<ConfigurableEntry>>()

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

  LOG.info("Found ${options.size} configurables")

  for (configurable in options.keys) {
    try {
      addOptions(originalConfigurable = configurable, options = options, roots = roots)
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
          val childConfigurableOptions = TreeSet<OptionDescription>()
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

private data class OptionSetId(@JvmField val pluginId: PluginId, @JvmField val moduleName: String? = null)

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