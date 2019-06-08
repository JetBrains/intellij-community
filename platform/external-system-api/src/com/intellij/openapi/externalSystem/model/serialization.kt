// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.WriteConfiguration

// do not use SkipNullAndEmptySerializationFilter for now because can lead to issues
fun createCacheWriteConfiguration() = WriteConfiguration(allowAnySubTypes = true)

fun createCacheReadConfiguration(log: Logger): ReadConfiguration {
  val projectDataManager = ProjectDataManager.getInstance()
  val defaultClassLoader = DataNode::class.java.classLoader

  val allManagers = ExternalSystemApiUtil.getAllManagers()
  return createDataNodeReadConfiguration(fun(name: String, hostObject: Any): Class<*>? {
    if (hostObject !is DataNode<*>) {
      return defaultClassLoader.loadClass(name)
    }

    val services = projectDataManager.findService(hostObject.key)
    if (services != null) {
      for (dataService in services) {
        try {
          return dataService.javaClass.classLoader.loadClass(name)
        }
        catch (e: ClassNotFoundException) {
        }
      }
    }

    for (manager in allManagers) {
      try {
        return manager.javaClass.classLoader.loadClass(name)
      }
      catch (e: ClassNotFoundException) {
      }
    }

    log.warn("Cannot find class `$name`")
    return null
  })
}

fun createDataNodeReadConfiguration(loadClass: ((name: String, hostObject: Any) -> Class<*>?)): ReadConfiguration {
  return ReadConfiguration(allowAnySubTypes = true, loadClass = loadClass, beanConstructed = {
    if (it is ProjectSystemId) {
      it.intern()
    }
    else {
      it
    }
  })
}