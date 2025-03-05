// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configuration

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ExecutionUiService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.ExtendableSettingsEditor
import com.intellij.openapi.options.ExtensionSettingsEditor
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.WriteExternalException
import com.intellij.util.SmartList
import org.jdom.Element
import org.jdom.output.XMLOutputter
import java.util.*

private const val EXTENSION_ID_ATTR = "ID"
private const val EXTENSION_ROOT_ATTR = "EXTENSION"

open class RunConfigurationExtensionsManager<U : RunConfigurationBase<*>, T : RunConfigurationExtensionBase<U>>(@PublishedApi internal val extensionPoint: ExtensionPointName<T>) {
  protected open val idAttrName: String = EXTENSION_ID_ATTR

  protected open val extensionRootAttr: String = EXTENSION_ROOT_ATTR

  private val unloadedExtensionsKey = Key.create<List<Element>>(this::class.java.canonicalName + ".run.extension.elements")

  fun readExternal(configuration: U, parentNode: Element) {
    val children = parentNode.getChildren(extensionRootAttr)
    if (children.isEmpty()) {
      return
    }

    val extensions = HashMap<String, T>()
    processApplicableExtensions(configuration) {
      extensions[it.serializationId] = it
    }

    // if some of extensions settings weren't found we should just keep it because some plugin with extension
    // may be turned off
    var hasUnknownExtension = false
    for (element in children) {
      val id = element.getExtensionId()
      val extension = extensions.remove(id)
      if (extension == null) {
        hasUnknownExtension = true
      }
      else {
        extension.readExternal(configuration, element)
      }
    }
    if (hasUnknownExtension) {
      val copy = SmartList<Element>()
      for (child in children) {
        copy.add(JDOMUtil.internElement(child))
      }
      configuration.putCopyableUserData(unloadedExtensionsKey, copy)
    }
  }

  fun writeExternal(configuration: U, parentNode: Element) {
    val map = TreeMap<String, Element>()
    val elements = configuration.getCopyableUserData(unloadedExtensionsKey)
    if (elements != null) {
      for (element in elements) {
        val id = element.getExtensionId()
        if (id != null) {
          map[id] = element.clone()
        }
      }
    }

    processApplicableExtensions(configuration) { extension ->
      val id = extension.serializationId
      val element = Element(extensionRootAttr)
      element.setExtensionId(id)
      try {
        extension.writeExternal(configuration, element)
      }
      catch (_: WriteExternalException) {
        return@processApplicableExtensions
      }

      if (element.content.isNotEmpty() || element.attributes.size > 1) {
        map[id] = element
      }
    }

    for (values in map.values) {
      parentNode.addContent(values)
    }
  }

  private fun Element.getExtensionId(): String? {
    val id = getAttributeValue(idAttrName)
    if (id == null) {
      val xml = XMLOutputter().outputString(this)
      logger<RunConfigurationExtensionsManager<*, *>>().error("Cannot find extension id in extension element: $xml")
    }
    return id
  }

  private fun Element.setExtensionId(id: String) {
    setAttribute(idAttrName, id)
  }

  fun <V : U> appendEditors(configuration: U, group: SettingsEditorGroup<V>) {
    appendEditors(configuration, group, null)
  }

  /**
   * Appends {@code SettingsEditor} to group or to {@param mainEditor} (if editor implements marker interface {@code ExtensionSettingsEditor}
   */
  fun <V : U> appendEditors(configuration: U, group: SettingsEditorGroup<V>, mainEditor: ExtendableSettingsEditor<V>?) {
    processApplicableExtensions(configuration) {
      @Suppress("UNCHECKED_CAST")
      val editor = it.createEditor(configuration as V) ?: return@processApplicableExtensions
      if (mainEditor != null && editor is ExtensionSettingsEditor) {
        mainEditor.addExtensionEditor(editor)
      } else {
        group.addEditor(it.editorTitle, editor)
      }
    }
  }

  fun <V : U> createFragments(configuration: V): List<SettingsEditor<V>> {
    val list = ArrayList<SettingsEditor<V>>()
    processApplicableExtensions(configuration) { t ->
      val fragments = t.createFragments(configuration)
      if (fragments != null) {
        list.addAll(fragments)
      }
      else {
        val editor = t.createEditor(configuration)
        if (editor != null) {
          val wrapper = ExecutionUiService.getInstance().createSettingsEditorFragmentWrapper(t.serializationId, t.editorTitle, null, editor)
          { t.isEnabledFor(configuration, null) }
          if (wrapper != null) {
            list.add(wrapper)
          }
        }
      }
    }
    return list
  }

  @Throws(Exception::class)
  fun validateConfiguration(configuration: U, isExecution: Boolean) {
    // only for enabled extensions
    processEnabledExtensions(configuration, null) {
      it.validateConfiguration(configuration, isExecution)
    }
  }

  fun extendCreatedConfiguration(configuration: U, location: Location<*>) {
    processApplicableExtensions(configuration) {
      it.extendCreatedConfiguration(configuration, location)
    }
  }

  fun extendTemplateConfiguration(configuration: U) {
    processApplicableExtensions(configuration) {
      it.extendTemplateConfiguration(configuration)
    }
  }

  @Throws(ExecutionException::class)
  open fun patchCommandLine(configuration: U,
                            runnerSettings: RunnerSettings?,
                            cmdLine: GeneralCommandLine,
                            runnerId: String,
                            executor: Executor) {
    // only for enabled extensions
    processEnabledExtensions(configuration, runnerSettings) {
      it.patchCommandLine(configuration, runnerSettings, cmdLine, runnerId, executor)
    }
  }

  @Throws(ExecutionException::class)
  open fun patchCommandLine(configuration: U, runnerSettings: RunnerSettings?, cmdLine: GeneralCommandLine, runnerId: String) {
    // only for enabled extensions
    processEnabledExtensions(configuration, runnerSettings) {
      it.patchCommandLine(configuration, runnerSettings, cmdLine, runnerId)
    }
  }

  open fun attachExtensionsToProcess(configuration: U, handler: ProcessHandler, runnerSettings: RunnerSettings?) {
    // only for enabled extensions
    processEnabledExtensions(configuration, runnerSettings) {
      it.attachToProcess(configuration, handler, runnerSettings)
    }
  }

  /**
   * Consider to use processApplicableExtensions.
   */
  protected fun getApplicableExtensions(configuration: U): MutableList<T> {
    val extensions = SmartList<T>()
    processApplicableExtensions(configuration) {
      extensions.add(it)
    }
    return extensions
  }

  protected inline fun processApplicableExtensions(configuration: U, handler: (T) -> Unit) {
    for (extension in extensionPoint.lazySequence()) {
      if (extension.isApplicableFor(configuration)) {
        try {
          handler(extension)
        }
        catch (_: IndexNotReadyException) {
        }
      }
    }
  }

  protected inline fun processEnabledExtensions(configuration: U, runnerSettings: RunnerSettings?, handler: (T) -> Unit) {
    for (extension in extensionPoint.lazySequence()) {
      if (extension.isApplicableFor(configuration) && extension.isEnabledFor(configuration, runnerSettings)) {
        try {
          handler(extension)
        }
        catch (_: IndexNotReadyException) {
        }
      }
    }
  }
}
