/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.configurations

import com.intellij.util.ArrayUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

import javax.swing.*

/**
 * @author yole
 */
abstract class ConfigurationTypeBase protected constructor(@param:NonNls private val myId: String, @param:Nls private val myDisplayName: String, @param:Nls private val myDescription: String,
                                                           private val myIcon: Icon) : ConfigurationType {
  private var myFactories: Array<ConfigurationFactory>? = null

  init {
    myFactories = EMPTY_FACTORIES
  }

  protected fun addFactory(factory: ConfigurationFactory) {
    myFactories = ArrayUtil.append(myFactories!!, factory)
  }

  @Nls
  override fun getDisplayName(): String {
    return myDisplayName
  }

  @Nls
  override fun getConfigurationTypeDescription(): String {
    return myDescription
  }

  override fun getIcon(): Icon {
    return myIcon
  }

  @NonNls
  override fun getId(): String {
    return myId
  }

  override fun getConfigurationFactories(): Array<ConfigurationFactory>? {
    return myFactories
  }

  companion object {
    private val EMPTY_FACTORIES = arrayOfNulls<ConfigurationFactory>(0)
  }
}
