// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.serialization.NonDefaultConstructorInfo
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.WriteConfiguration

// do not use SkipNullAndEmptySerializationFilter for now because can lead to issues
fun createCacheWriteConfiguration() = WriteConfiguration(allowAnySubTypes = true)

private fun createDataClassResolver(log: Logger): (name: String, hostObject: DataNode<*>?) -> Class<*>? {
  val projectDataManager = ProjectDataManager.getInstance()
  val managerClassLoaders = ExternalSystemManager.EP_NAME.iterable.asSequence()
    .map { it.javaClass.classLoader }
    .toSet()
  return fun(name: String, hostObject: DataNode<*>?): Class<*>? {
    var classLoadersToSearch = managerClassLoaders
    val services = if (hostObject == null) emptyList() else projectDataManager!!.findService(hostObject.key)
    if (!services.isNullOrEmpty()) {
      val set = LinkedHashSet<ClassLoader>(managerClassLoaders.size + services.size)
      set.addAll(managerClassLoaders)
      services.mapTo(set) { it.javaClass.classLoader }
      classLoadersToSearch = set
    }

    for (classLoader in classLoadersToSearch) {
      try {
        return classLoader.loadClass(name)
      }
      catch (e: ClassNotFoundException) {
      }
    }

    log.warn("Cannot find class `$name`")
    return null
  }
}

@JvmOverloads
fun createCacheReadConfiguration(log: Logger, testOnlyClassLoader: ClassLoader? = null): ReadConfiguration {
  val dataNodeResolver = if (testOnlyClassLoader == null) createDataClassResolver(log) else null
  return createDataNodeReadConfiguration(fun(name: String, hostObject: Any): Class<*>? {
    return when {
      hostObject !is DataNode<*> -> {
        val hostObjectClass = hostObject.javaClass
        try {
          hostObjectClass.classLoader.loadClass(name)
        }
        catch (e: ClassNotFoundException) {
          log.debug("cannot find class $name using class loader of class ${hostObjectClass.name} (classLoader=${hostObjectClass.classLoader})", e)
          // let's try system manager class loaders
          dataNodeResolver?.invoke(name, null) ?: throw e
        }
      }
      dataNodeResolver == null -> testOnlyClassLoader!!.loadClass(name)
      else -> dataNodeResolver(name, hostObject)
    }
  })
}

fun createDataNodeReadConfiguration(loadClass: ((name: String, hostObject: Any) -> Class<*>?)): ReadConfiguration {
  return ReadConfiguration(allowAnySubTypes = true, resolvePropertyMapping = { beanClass ->
    when (beanClass.name) {
      "org.jetbrains.kotlin.idea.configuration.KotlinTargetData" -> NonDefaultConstructorInfo(listOf("externalName"), beanClass.getDeclaredConstructor(String::class.java))
      "org.jetbrains.kotlin.idea.configuration.KotlinAndroidSourceSetData" -> NonDefaultConstructorInfo(listOf("sourceSetInfos"), beanClass.constructors.first())
      else -> null
    }
  }, loadClass = loadClass, beanConstructed = {
    if (it is ProjectSystemId) {
      it.intern()
    }
    else {
      it
    }
  })
}