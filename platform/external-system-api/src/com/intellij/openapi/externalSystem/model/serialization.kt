// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.serialization.NonDefaultConstructorInfo
import com.intellij.serialization.ReadConfiguration
import com.intellij.serialization.WriteConfiguration

// do not use SkipNullAndEmptySerializationFilter for now because can lead to issues
fun createCacheWriteConfiguration(): WriteConfiguration = WriteConfiguration(allowAnySubTypes = true)

private class DataClassResolver(private val log: Logger) {
  private val projectDataManager = ProjectDataManager.getInstance()

  private val managerClassLoaders = ExternalSystemManager.EP_NAME.lazySequence()
    .flatMap { it.extensionPointsForResolver.flatMap { it.lazySequence() } + it }
    .map { it.javaClass.classLoader }
    .toSet()

  private fun getClassLoadersToSearch(hostObject: DataNode<*>?): Set<ClassLoader> {
    if (null == hostObject) {
      return managerClassLoaders
    }
    projectDataManager!!

    val services = projectDataManager.findService(hostObject.key) + projectDataManager.findWorkspaceService(hostObject.key)
    if (services.isEmpty()) {
      return managerClassLoaders
    }

    val serviceClassLoaders = services.map { it.javaClass.classLoader }
    val set = LinkedHashSet<ClassLoader>(managerClassLoaders.size + serviceClassLoaders.size)
    set.addAll(managerClassLoaders)
    set.addAll(serviceClassLoaders)
    return set
  }

  fun resolve(name: String, hostObject: DataNode<*>?): Class<*>? {
    val classLoadersToSearch = getClassLoadersToSearch(hostObject)

    var pe: PluginException? = null
    for (classLoader in classLoadersToSearch) {
      try {
        return classLoader.loadClass(name)
      }
      catch (e: PluginException) {
        if (pe != null) {
          e.addSuppressed(pe)
        }
        pe = e
      }
      catch (_: ClassNotFoundException) {
      }
    }

    log.warn("ExternalProjectsDataStorage deserialization: Cannot find class `$name`", pe)
    if (pe != null) {
      throw pe
    }
    else {
      return null
    }
  }
}

@JvmOverloads
fun createCacheReadConfiguration(log: Logger, testOnlyClassLoader: ClassLoader? = null): ReadConfiguration {
  val dataNodeResolver = if (testOnlyClassLoader == null) DataClassResolver(log) else null
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
          dataNodeResolver?.resolve(name, null) ?: throw e
        }
      }
      dataNodeResolver == null -> testOnlyClassLoader!!.loadClass(name)
      else -> dataNodeResolver.resolve(name, hostObject)
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