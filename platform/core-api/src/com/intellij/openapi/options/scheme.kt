/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.options

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import org.jdom.Element

interface Scheme {
  val name: String
}

interface ExternalizableScheme : Scheme {
  override var name: String
}

/**
 * A scheme processor can implement this interface to provide a file extension different from default .xml.
 * @see SchemeProcessor
 */
interface SchemeExtensionProvider {
  /**
   * @return The scheme file extension **with e leading dot**, for example ".ext".
   */
  val schemeExtension: String

  /**
   * @return True if the upgrade from the old default .xml extension is needed.
   */
  val isUpgradeNeeded: Boolean
}


interface SchemeDataHolder {
  fun read(): Element
}

abstract class SchemesManagerFactory {
  companion object {
    @JvmStatic
    fun getInstance() = ServiceManager.getService(SchemesManagerFactory::class.java)

    @JvmStatic
    fun getInstance(project: Project) = ServiceManager.getService(project, SchemesManagerFactory::class.java)
  }

  /**
   * directoryName — like "keymaps".
   */
  @JvmOverloads
  fun <T : Scheme> create(directoryName: String, processor: SchemeProcessor<out T>, presentableName: String? = null): SchemeManager<T> = create(directoryName, processor, presentableName)

  protected abstract fun <T : Scheme> create(directoryName: String, processor: SchemeProcessor<T>, presentableName: String? = null, roamingType: RoamingType = RoamingType.DEFAULT): SchemeManager<T>
}