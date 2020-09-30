// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configuration

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.SettingsEditorFragment
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.ExtendableSettingsEditor
import com.intellij.openapi.options.ExtensionSettingsEditor
import com.intellij.openapi.options.SettingsEditorGroup
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.WriteExternalException
import com.intellij.util.SmartList
import gnu.trove.THashMap
import org.jdom.Element
import java.util.*
import kotlin.collections.ArrayList

private val RUN_EXTENSIONS = Key.create<List<Element>>("run.extension.elements")
private const val EXT_ID_ATTR = "ID"
private const val EXTENSION_ROOT_ATTR = "EXTENSION"

open class RunConfigurationExtensionsManager<U : RunConfigurationBase<*>, T : RunConfigurationExtensionBase<U>>(val extensionPoint: ExtensionPointName<T>) {
  protected open val idAttrName = EXT_ID_ATTR

  protected open val extensionRootAttr = EXTENSION_ROOT_ATTR

  fun readExternal(configuration: U, parentNode: Element) {
    val children = parentNode.getChildren(extensionRootAttr)
    if (children.isEmpty()) {
      return
    }

    val extensions = THashMap<String, T>()
    processApplicableExtensions(configuration) {
      extensions.put(it.serializationId, it)
    }

    // if some of extensions settings weren't found we should just keep it because some plugin with extension
    // may be turned off
    var hasUnknownExtension = false
    for (element in children) {
      val extension = extensions.remove(element.getAttributeValue(idAttrName))
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
      configuration.putCopyableUserData(RUN_EXTENSIONS, copy)
    }
  }

  fun writeExternal(configuration: U, parentNode: Element) {
    val map = TreeMap<String, Element>()
    val elements = configuration.getCopyableUserData(RUN_EXTENSIONS)
    if (elements != null) {
      for (element in elements) {
        map.put(element.getAttributeValue(idAttrName), element.clone())
      }
    }

    processApplicableExtensions(configuration) { extension ->
      val element = Element(extensionRootAttr)
      element.setAttribute(idAttrName, extension.serializationId)
      try {
        extension.writeExternal(configuration, element)
      }
      catch (ignored: WriteExternalException) {
        return@processApplicableExtensions
      }

      if (element.content.isNotEmpty() || element.attributes.size > 1) {
        map.put(extension.serializationId, element)
      }
    }

    for (values in map.values) {
      parentNode.addContent(values)
    }
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

  fun createFragments(configuration: U): List<SettingsEditorFragment<U, *>> {
    val list = ArrayList<SettingsEditorFragment<U, *>>()
    processApplicableExtensions(configuration) {
      val fragments = it.createFragments(configuration)
      if (fragments != null) {
        list.addAll(fragments)
      }
      else {
        val editor = it.createEditor(configuration)
        if (editor != null) {
          val wrapper = SettingsEditorFragment.createWrapper(it.serializationId, it.editorTitle, null, editor)
          wrapper.isSelected = it.isEnabledFor(configuration, null)
          list.add(wrapper)
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
    for (extension in extensionPoint.iterable) {
      if (extension.isApplicableFor(configuration)) {
        handler(extension)
      }
    }
  }

  protected inline fun processEnabledExtensions(configuration: U, runnerSettings: RunnerSettings?, handler: (T) -> Unit) {
    for (extension in extensionPoint.iterable) {
      if (extension.isApplicableFor(configuration) && extension.isEnabledFor(configuration, runnerSettings)) {
        handler(extension)
      }
    }
  }
}
