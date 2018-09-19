// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.xmlb.annotations.Transient
import javax.swing.Icon

// RunConfiguration interface in java, so, we have to use base class (short kotlin property declaration doesn't support java)
abstract class RunConfigurationMinimalBase(name: String?, private val factory: ConfigurationFactory, private val project: Project) : RunConfiguration {
  private var name: String = name ?: ""
  
  @Transient
  override fun getName(): String {
    // a lot of clients not ready that name can be null and in most cases it is not convenient - just add more work to handle null value
    // in any case for run configuration empty name it is the same as null, we don't need to bother clients and use null
    return StringUtilRt.notNullize(name)
  }

  override fun setName(value: String) {
    name = value
  }

  override fun getFactory() = factory

  override fun getIcon(): Icon = factory.icon

  override fun getProject() = project
}