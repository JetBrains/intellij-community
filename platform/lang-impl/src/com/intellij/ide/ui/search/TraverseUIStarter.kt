// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.ui.search

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.BundleBase.L10N_MARKER
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
import com.intellij.ide.ui.search.SearchableOptionsRegistrar.SEARCHABLE_OPTIONS_XML_NAME
import com.intellij.idea.AppMode
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.*
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.ReflectionUtil
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jdom.IllegalDataException
import org.jetbrains.annotations.Nls
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

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
      )
      ApplicationManagerEx.getApplicationEx().exit( /*force: */ false, /*confirm: */ true )
    }
    catch (e: Throwable) {
      try {
        println("Searchable options index builder failed")
        e.printStackTrace()
      }
      catch (_: Throwable) {
      }
      ApplicationManagerEx.getApplicationEx().exit( /*force: */ false, /*confirm: */ true, -1 )
    }
  }
}

private fun addOptions(
  originalConfigurable: SearchableConfigurable,
  options: Map<SearchableConfigurable, Set<SearchableOptionEntry>>,
  roots: MutableMap<OptionSetId, MutableList<ConfigurableEntry>>,
) {
  val configurableEntry = createConfigurableElement(originalConfigurable)
  configurableEntry.entries.addAll(elements = options.get(originalConfigurable)!!)

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
        entryElement.entries.addAll(value)
        addElement(roots = roots, entry = entryElement, module = key)
      }
    }
    is OptionsContainingConfigurable -> processOptionsContainingConfigurable(configurable, configurableEntry)
    is PluginManagerConfigurable -> {
      val optionDescriptions = TreeSet<SearchableOptionEntry>()
      wordsToOptionDescriptors(optionPaths = setOf(IdeBundle.message("plugin.manager.repositories")), path = null, result = optionDescriptions)
      configurableEntry.entries.add(SearchableOptionEntry(hit = IdeBundle.message("plugin.manager.repositories")))
    }
    is AllFileTemplatesConfigurable -> {
      for ((key, value) in processFileTemplates()) {
        val entryElement = createConfigurableElement(configurable)
        entryElement.entries.addAll(value)
        addElement(roots = roots, entry = entryElement, module = key)
      }
    }
  }

  addElement(roots = roots, entry = configurableEntry, module = getSetIdByClass(configurable.originalClass))
}

private data class SearchableOptionFile(@JvmField val module: OptionSetId, @JvmField val item: SearchableOptionSetIndexItem)

@Serializable
private data class SearchableOptionSetIndexItem(@JvmField val file: String, @JvmField val hash: Long, @JvmField val size: Long)

private fun getKeyByMessage(s: String): String {
  val matches = INDEX_ENTRY_REGEXP.findAll(s)
  if (matches.none()) {
    return s
  }

  // we don't need to build the correct message if multiple keys were used, we stem it in any case, so, use `;` as a separator
  //println("MULTIPLE ($s): " + list.joinToString { it.value })
  return matches.joinToString(separator = ";") { it.value }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun saveResults(outDir: Path, roots: Map<OptionSetId, List<ConfigurableEntry>>) {
  println("save to $outDir")
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

        require(LocalizationUtil.getLocaleOrNullForDefault() == null) {
          "Locale must be default"
        }
        val fileName = (if (module.moduleName == null) "p-${module.pluginId.idString}" else "m-${module.moduleName}") +
                       "-" + SEARCHABLE_OPTIONS_XML_NAME + ".json"
        val file = outDir.resolve(fileName)
        try {
          Files.newBufferedWriter(file).use { writer ->
            hash.putInt(value.size)
            for (entry in value) {
              val modifiedEntry = entry.copy(
                id = getKeyByMessage(entry.id),
                name = getKeyByMessage(entry.name),
                entries = entry.entries.mapTo(mutableListOf()) { optionEntry ->
                  optionEntry.copy(
                    hit = getKeyByMessage(optionEntry.hit),
                    path = optionEntry.path?.let { getKeyByMessage(it) },
                  )
                },
              )
              val encoded = Json.encodeToString(serializer, modifiedEntry)

              require(!encoded.contains(L10N_MARKER)) {
                "Searchable options index contains unexpected L10N marker: $encoded"
              }

              val encodeError = checkUtf8(encoded)
              require(encodeError == null) {
                "Cannot encode string: (error=$encodeError, entry=$entry)"
              }

              hash.putString(encoded)
              writer.write(encoded)
              writer.append('\n')
            }
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          throw RuntimeException("Cannot write $file", e)
        }

        SearchableOptionFile(
          module = module,
          item = SearchableOptionSetIndexItem(file = fileName, hash = hash.asLong, size = Files.size(file)),
        )
      }
    }
  }.map { it.getCompleted() }

  withContext(Dispatchers.IO) {
    Files.writeString(outDir.resolve("content.json"), Json.encodeToString(
      fileDescriptors
        .groupBy(keySelector = { it.module.moduleName ?: it.module.pluginId.idString })
        .mapValues { entry ->
          val item = entry.value.single().item
          listOf(SearchableOptionSetIndexItem(file = item.file, hash = item.hash, size = item.size))
        }
    ))
  }

  for (extension in TraverseUIHelper.helperExtensionPoint.extensionList) {
    extension.afterResultsAreSaved()
  }
}

private fun checkUtf8(s: String): CharacterCodingException? {
  val encoder = Charsets.UTF_8.newEncoder()
  encoder.onMalformedInput(CodingErrorAction.REPORT)
  encoder.onUnmappableCharacter(CodingErrorAction.REPORT)
  try {
    encoder.encode(CharBuffer.wrap(s))
    return null
  }
  catch (e: CharacterCodingException) {
    return e
  }
}

private fun createConfigurableElement(configurable: SearchableConfigurable): ConfigurableEntry {
  return ConfigurableEntry(id = configurable.id, name = configurable.displayName, entries = mutableListOf())
}

private fun addElement(roots: MutableMap<OptionSetId, MutableList<ConfigurableEntry>>, entry: ConfigurableEntry, module: OptionSetId) {
  roots.computeIfAbsent(module) { ArrayList() }.add(entry)
}

private fun processFileTemplates(): Map<OptionSetId, MutableSet<SearchableOptionEntry>> {
  val options = LinkedHashMap<OptionSetId, MutableSet<SearchableOptionEntry>>()
  val fileTemplateManager = FileTemplateManager.getDefaultInstance()
  processTemplates(options = options, templates = fileTemplateManager.allTemplates)
  processTemplates(options = options, templates = fileTemplateManager.allPatterns)
  processTemplates(options = options, templates = fileTemplateManager.allCodeTemplates)
  processTemplates(options = options, templates = fileTemplateManager.allJ2eeTemplates)
  return options
}

private fun processTemplates(
  options: MutableMap<OptionSetId, MutableSet<SearchableOptionEntry>>,
  templates: Array<FileTemplate>,
) {
  for (template in templates) {
    val text = template.name
    if (text.isNotBlank()) {
      val module = if (template is BundledFileTemplate) getSetIdByPluginDescriptor(template.pluginDescriptor) else CORE_SET_ID
      options.computeIfAbsent(module) { TreeSet() }.add(SearchableOptionEntry(hit = text, path = null))
    }
  }
}

private fun processOptionsContainingConfigurable(configurable: OptionsContainingConfigurable, configurableElement: ConfigurableEntry) {
  val optionPaths = configurable.processListOptions()
  val result = TreeSet<SearchableOptionEntry>()
  wordsToOptionDescriptors(optionPaths = optionPaths, path = null, result = result)
  for ((path, optionPath) in configurable.processListOptionsWithPaths()) {
    wordsToOptionDescriptors(optionPaths = optionPath, path = path, result = result)
  }
  configurableElement.entries.addAll(result)
}

private fun wordsToOptionDescriptors(optionPaths: Set<String>, path: String?, result: MutableSet<SearchableOptionEntry>) {
  for (opt in optionPaths) {
    result.add(SearchableOptionEntry(hit = opt, path = path))
  }
}

private fun processKeymap(): Map<OptionSetId, Set<SearchableOptionEntry>> {
  val map = LinkedHashMap<OptionSetId, MutableSet<SearchableOptionEntry>>()
  val actionManager = ActionManager.getInstance() as ActionManagerImpl
  val actionToPluginId = getActionToPluginId(actionManager)
  val componentName = "ActionManager"
  for (action in actionManager.actions(canReturnStub = false)) {
    val module = getModuleByAction(action, actionToPluginId)
    val options = map.computeIfAbsent(module) { TreeSet() }
    action.templatePresentation.text?.takeIf { it.isNotBlank() }?.let {
      options.add(SearchableOptionEntry(hit = it, path = componentName))
    }

    action.templatePresentation.description?.takeIf { it.isNotBlank() }?.let {
      options.add(SearchableOptionEntry(hit = it, path = componentName))
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
  return if (classLoader is PluginAwareClassLoader) getSetIdByPluginDescriptor(classLoader.pluginDescriptor) else CORE_SET_ID
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

private suspend fun doBuildSearchableOptions(
  options: MutableMap<SearchableConfigurable, Set<SearchableOptionEntry>>,
  outputPath: Path,
) {
  assert(AppMode.isHeadless())

  val roots = LinkedHashMap<OptionSetId, MutableList<ConfigurableEntry>>()

  val defaultProject = serviceAsync<ProjectManager>().defaultProject
  withContext(Dispatchers.EDT) {
    writeIntentReadAction {
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
      )
    }
  }

  println("Found ${options.size} configurables")

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
  options: MutableMap<SearchableConfigurable, Set<SearchableOptionEntry>>,
) {
  for (configurable in configurables) {
    if (configurable !is SearchableConfigurable) {
      continue
    }

    val configurableOptions = TreeSet<SearchableOptionEntry>()
    options.put(configurable, configurableOptions)

    for (extension in TraverseUIHelper.helperExtensionPoint.extensionList) {
      extension.beforeConfigurable(configurable, configurableOptions)
    }

    if (configurable is MasterDetails) {
      configurable.initUi()
      collectSearchItemsForComponentWithLabel(
        configurable = configurable,
        configurableOptions = configurableOptions,
        component = configurable.master,
      )
      collectSearchItemsForComponentWithLabel(
        configurable = configurable,
        configurableOptions = configurableOptions,
        component = configurable.details.component,
      )
    }
    else {
      configurable.createComponent()?.let { component ->
        processUiLabel(
          title = configurable.displayName,
          configurableOptions = null,
          path = null,
          i18n = false,
          rawList = configurableOptions,
        )
        collectSearchItemsForComponent(
          component = component,
          configurableOptions = null,
          path = null,
          i18n = false,
          rawList = configurableOptions,
        )
      }

      val unwrapped = unwrapConfigurable(configurable)
      if (unwrapped is CompositeConfigurable<*>) {
        unwrapped.disposeUIResources()
        val children = unwrapped.configurables
        for (child in children) {
          val childConfigurableOptions = TreeSet<SearchableOptionEntry>()
          options.put(SearchableConfigurableAdapter(configurable, child), childConfigurableOptions)

          if (child is SearchableConfigurable) {
            processUiLabel(
              title = child.displayName,
              configurableOptions = null,
              path = null,
              i18n = false,
              rawList = childConfigurableOptions,
            )
          }
          child.createComponent()?.let { component ->
            collectSearchItemsForComponent(
              component = component,
              configurableOptions = null,
              path = null,
              i18n = false,
              rawList = childConfigurableOptions,
            )
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

private const val DEBUGGER_CONFIGURABLE_CLASS = "com.intellij.xdebugger.impl.settings.DebuggerConfigurable"

private fun unwrapConfigurable(configurable: Configurable): Configurable {
  @Suppress("NAME_SHADOWING")
  var configurable = configurable
  if (configurable is ConfigurableWrapper) {
    val wrapped = configurable.configurable
    if (wrapped is SearchableConfigurable) {
      configurable = wrapped
    }
  }
  if (DEBUGGER_CONFIGURABLE_CLASS == configurable.javaClass.name) {
    val clazz = ReflectionUtil.forName(DEBUGGER_CONFIGURABLE_CLASS)
    val rootConfigurable = ReflectionUtil.getField(clazz, configurable, Configurable::class.java, "myRootConfigurable")
    if (rootConfigurable != null) {
      return rootConfigurable
    }
  }
  return configurable
}