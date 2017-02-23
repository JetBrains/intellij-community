/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.impl

import com.intellij.configurationStore.*
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.UnknownRunConfiguration
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.options.SchemeState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import org.jdom.Element
import java.util.*
import java.util.function.Function

internal class WorkspaceRunManager(project: Project, propertiesComponent: PropertiesComponent) : RunManagerImpl(project, propertiesComponent) {
  private val schemeManagerProvider = SchemeManagerIprProvider("configuration")

  private val schemeManager = SchemeManagerFactory.getInstance(project).create("workspace",
    object : LazySchemeProcessor<RunConfigurationScheme, RunConfigurationScheme>() {
      override fun createScheme(dataHolder: SchemeDataHolder<RunConfigurationScheme>, name: String, attributeProvider: Function<String, String?>, isBundled: Boolean): RunConfigurationScheme {
        val settings = RunnerAndConfigurationSettingsImpl(this@WorkspaceRunManager)
        val element = dataHolder.read()
        try {
          settings.readExternal(element)
        }
        catch (e: InvalidDataException) {
          LOG.error(e)
        }

        val factory = settings.factory ?: return UnknownRunConfigurationScheme(name)
        doLoadConfiguration(element, false, settings, factory)
        return settings
      }

      override fun getName(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String): String {
        var name = attributeProvider.apply("name")
        if (name == "<template>" || name == null) {
          attributeProvider.apply("type")?.let {
            if (name == null) {
              name = "<template>"
            }
            name += " of type ${it}"
          }
        }
        return name ?: throw IllegalStateException("name is missed in the scheme data")
      }
    }, streamProvider = schemeManagerProvider, autoSave = false)

  override fun loadState(parentNode: Element) {
    clear(false)

    schemeManagerProvider.load(parentNode) {
      var name = it.getAttributeValue("name")
      if (name == "<template>" || name == null) {
        // scheme name must be unique
        it.getAttributeValue("type")?.let {
          if (name == null) {
            name = "<template>"
          }
          name += " of type ${it}"
        }
      }
      name
    }
    schemeManager.reload()

    super.loadState(parentNode)
  }

  override fun getState(): Element {
    val element = Element("state")

    schemeManager.save()

    // template rc in the end
    schemeManagerProvider.writeState(element, Comparator { n1, n2 ->
      val w1 = if (n1.startsWith("<template> of ")) 1 else 0
      val w2 = if (n2.startsWith("<template> of ")) 1 else 0
      if (w1 != w2) {
        w1 - w2
      }
      else {
        n1.compareTo(n2)
      }
    })

    super.getState(element)

    return element
  }

  override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
    if (settings.isTemporary) {
      schemeManager.addScheme(settings as RunConfigurationScheme)
    }
    super.runConfigurationAdded(settings)
  }

  override fun getConfigurationTemplate(factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    val key = "${factory.type.id}.${factory.name}"
    var template = myTemplateConfigurationsMap.get(key)
    if (template == null) {
      template = RunnerAndConfigurationSettingsImpl(this, factory.createTemplateConfiguration(myProject, this), true)
      template.isSingleton = factory.isConfigurationSingletonByDefault
      (template.configuration as? UnknownRunConfiguration)?.let {
        it.isDoNotStore = true
      }

      schemeManager.addScheme(template)

      myTemplateConfigurationsMap.put(key, template)
    }
    return template
  }
}

interface RunConfigurationScheme : Scheme

private class UnknownRunConfigurationScheme(private val name: String) : RunConfigurationScheme, SerializableScheme {
  override fun getSchemeState() = SchemeState.UNCHANGED

  override fun writeScheme() = throw AssertionError("Must be not called")

  override fun getName() = name
}