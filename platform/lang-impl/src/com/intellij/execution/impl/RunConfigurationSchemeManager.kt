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

import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.util.InvalidDataException
import com.intellij.util.attribute
import org.jdom.Element
import java.util.function.Function

internal class RunConfigurationSchemeManager(private val manager: RunManagerImpl, private val isShared: Boolean) : LazySchemeProcessor<RunnerAndConfigurationSettingsImpl, RunnerAndConfigurationSettingsImpl>() {
  override fun createScheme(dataHolder: SchemeDataHolder<RunnerAndConfigurationSettingsImpl>, name: String, attributeProvider: Function<String, String?>, isBundled: Boolean): RunnerAndConfigurationSettingsImpl {
    val settings = RunnerAndConfigurationSettingsImpl(manager)
    var element = dataHolder.read()
    if (isShared && element.name == "component") {
      element = element.getChild("configuration")
    }

    try {
      settings.readExternal(element, isShared)
    }
    catch (e: InvalidDataException) {
      RunManagerImpl.LOG.error(e)
    }

    manager.addConfiguration(element, settings)
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

  override fun isExternalizable(scheme: RunnerAndConfigurationSettingsImpl) = true

  override fun onSchemeDeleted(scheme: RunnerAndConfigurationSettingsImpl) {
    manager.removeConfiguration(scheme)
  }

  override fun writeScheme(scheme: RunnerAndConfigurationSettingsImpl): Element {
    val result = super.writeScheme(scheme)
    if (isShared) {
      return Element("component")
        .attribute("name", "ProjectRunConfigurationManager")
        .addContent(result)
    }
    return result
  }
}