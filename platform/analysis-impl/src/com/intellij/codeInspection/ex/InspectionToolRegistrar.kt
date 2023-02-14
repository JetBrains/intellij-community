// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.analysis.AnalysisBundle
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.*
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.SmartList
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<InspectionToolRegistrar>()
private val EP_NAME = ExtensionPointName<InspectionToolProvider>("com.intellij.inspectionToolProvider")

private typealias InspectionFactory = () -> InspectionToolWrapper<*, *>?

@Service
class InspectionToolRegistrar : InspectionToolsSupplier() {
  companion object {
    @JvmStatic
    fun getInstance() = service<InspectionToolRegistrar>()

    @ApiStatus.Internal
    @JvmStatic
    fun wrapTool(profileEntry: InspectionProfileEntry): InspectionToolWrapper<*, *> {
      return when (profileEntry) {
        is LocalInspectionTool -> LocalInspectionToolWrapper(profileEntry)
        is GlobalInspectionTool -> GlobalInspectionToolWrapper(profileEntry)
        else -> throw RuntimeException("unknown inspection class: " + profileEntry + "; " + profileEntry.javaClass)
      }
    }
  }

  private val toolFactories: Collection<List<InspectionFactory>>

  init {
    val app = ApplicationManager.getApplication()
    val result = CollectionFactory.createSmallMemoryFootprintMap<Any, MutableList<InspectionFactory>>()
    val shortNames = CollectionFactory.createSmallMemoryFootprintMap<String, InspectionEP>()
    registerToolProviders(result)
    registerInspections(result, app, shortNames, LocalInspectionEP.LOCAL_INSPECTION)
    registerInspections(result, app, shortNames, InspectionEP.GLOBAL_INSPECTION)
    toolFactories = result.values
  }

  private fun unregisterInspectionOrProvider(inspectionOrProvider: Any, factories: MutableMap<Any, MutableList<InspectionFactory>>) {
    for (removedTool in (factories.remove(inspectionOrProvider) ?: return)) {
      removedTool()?.shortName?.let { shortName -> HighlightDisplayKey.unregister(shortName) }
      fireToolRemoved(removedTool)
    }
  }

  private fun <T : InspectionEP> registerInspections(factories: MutableMap<Any, MutableList<InspectionFactory>>,
                                                     app: Application,
                                                     shortNames: MutableMap<String, InspectionEP>,
                                                     extensionPointName: ExtensionPointName<T>) {
    val isInternal = app.isInternal
    for (extension in extensionPointName.extensionList) {
      registerInspection(extension, shortNames, isInternal, factories)
    }

    extensionPointName.addExtensionPointListener(object : ExtensionPointListener<T> {
      override fun extensionAdded(inspection: T, pluginDescriptor: PluginDescriptor) {
        fireToolAdded(registerInspection(inspection, shortNames, isInternal, factories) ?: return)
      }

      override fun extensionRemoved(inspection: T, pluginDescriptor: PluginDescriptor) {
        unregisterInspectionOrProvider(inspection, factories)
        shortNames.remove(inspection.getShortName())
      }
    }, null)
  }

  private fun registerToolProviders(factories: MutableMap<Any, MutableList<InspectionFactory>>) {
    EP_NAME.processWithPluginDescriptor { provider, pluginDescriptor ->
      registerToolProvider(provider, pluginDescriptor, factories, null)
    }
    EP_NAME.addExtensionPointListener(object : ExtensionPointListener<InspectionToolProvider> {
      override fun extensionAdded(extension: InspectionToolProvider, pluginDescriptor: PluginDescriptor) {
        val added = mutableListOf<InspectionFactory>()
        registerToolProvider(extension, pluginDescriptor, factories, added)
        for (supplier in added) {
          fireToolAdded(supplier)
        }
      }

      override fun extensionRemoved(extension: InspectionToolProvider, pluginDescriptor: PluginDescriptor) {
        unregisterInspectionOrProvider(extension, factories)
      }
    }, null)
  }

  private fun fireToolAdded(factory: InspectionFactory) {
    val inspectionToolWrapper = factory() ?: return
    for (listener in listeners) {
      listener.toolAdded(inspectionToolWrapper)
    }
  }

  private fun fireToolRemoved(factory: InspectionFactory) {
    val inspectionToolWrapper = factory() ?: return
    for (listener in listeners) {
      listener.toolRemoved(inspectionToolWrapper)
    }
  }

  override fun createTools(): List<InspectionToolWrapper<*, *>> {
    val tools = ArrayList<InspectionToolWrapper<*, *>>()
    for (list in toolFactories) {
      tools.ensureCapacity(list.size)
      for (factory in list) {
        ProgressManager.checkCanceled()
        val toolWrapper = factory()
        if (toolWrapper != null && checkTool(toolWrapper) == null) {
          tools.add(toolWrapper)
        }
      }
    }
    return tools
  }
}

private fun <T : InspectionEP> registerInspection(inspection: T,
                                                  shortNames: MutableMap<String, InspectionEP>,
                                                  isInternal: Boolean,
                                                  factories: MutableMap<Any, MutableList<InspectionFactory>>): (InspectionFactory)? {
  checkForDuplicateShortName(inspection, shortNames)
  if (!isInternal && inspection.isInternal) {
    return null
  }

  val factory = {
    if (inspection is LocalInspectionEP) LocalInspectionToolWrapper(inspection) else GlobalInspectionToolWrapper(inspection)
  }
  factories.computeIfAbsent(inspection) { SmartList() }.add(factory)
  return factory
}

private fun registerToolProvider(provider: InspectionToolProvider,
                                 pluginDescriptor: PluginDescriptor,
                                 keyToFactories: MutableMap<Any, MutableList<InspectionFactory>>,
                                 added: MutableList<InspectionFactory>?) {
  val factories = keyToFactories.computeIfAbsent(provider) { ArrayList() }
  for (aClass in provider.inspectionClasses) {
    val supplier = {
      try {
        val constructor = aClass.getDeclaredConstructor()
        constructor.isAccessible = true
        InspectionToolRegistrar.wrapTool(constructor.newInstance())
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error(PluginException(e, pluginDescriptor.pluginId))
        null
      }
    }
    factories.add(supplier)
    added?.add(supplier)
  }
}

private fun checkForDuplicateShortName(ep: InspectionEP, shortNames: MutableMap<String, InspectionEP>) {
  val shortName = ep.getShortName()
  val duplicate = shortNames.put(shortName, ep) ?: return
  val descriptor = ep.pluginDescriptor
  LOG.error(PluginException(
    """
      Short name '$shortName' is not unique
      class '${ep.instantiateTool().javaClass.canonicalName}' in $descriptor
      and class '${duplicate.instantiateTool().javaClass.canonicalName}' in ${duplicate.pluginDescriptor}
      conflict
      """.trimIndent(),
    descriptor.pluginId))
}

private fun checkTool(toolWrapper: InspectionToolWrapper<*, *>): String? {
  if (toolWrapper !is LocalInspectionToolWrapper) {
    return null
  }

  var message: String? = null
  try {
    val id = toolWrapper.getID()
    if (!LocalInspectionTool.isValidID(id)) {
      message = AnalysisBundle.message("inspection.disabled.wrong.id", toolWrapper.getShortName(), id, LocalInspectionTool.VALID_ID_PATTERN)
    }
  }
  catch (t: Throwable) {
    message = AnalysisBundle.message("inspection.disabled.error", toolWrapper.getShortName(), t.message)
  }
  if (message != null) {
    LOG.error(message)
  }
  return message
}