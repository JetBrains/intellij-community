// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.worker

import org.jetbrains.jps.builders.AdditionalRootsProviderService
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool
import org.jetbrains.jps.builders.java.ExcludedJavaSourceRootProvider
import org.jetbrains.jps.builders.java.JavaBuilderExtension
import org.jetbrains.jps.builders.java.JavaCompilingTool
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.model.*
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.service.JpsServiceManager
import org.jetbrains.jps.service.SharedThreadPool
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Suppress("RemoveRedundantQualifierName")
private fun <T> listOf(vararg elements: T): List<T> = java.util.List.of(*elements)
@Suppress("RemoveRedundantQualifierName")
private fun listOf(): List<Any> = java.util.List.of()

@Suppress("unused")
internal class BazelJpsServiceManager : JpsServiceManager() {
  private val services = ConcurrentHashMap<Class<*>, Any>()
  private val extensions = ConcurrentHashMap<Class<*>, List<*>>()

  init {
    extensions.put(JavaCompilingTool::class.java, listOf(JavacCompilerTool()))
    extensions.put(BuilderService::class.java, listOf(BazelJavaBuilderService))
    // org.jetbrains.kotlin.jps.build.KotlinResourcesRootProvider and KotlinSourceRootProvider are not needed
    extensions.put(AdditionalRootsProviderService::class.java, listOf())
    extensions.put(ExcludedJavaSourceRootProvider::class.java, listOf())
    // exclude CleanupTempDirectoryExtension
    extensions.put(JavaBuilderExtension::class.java, listOf(
      object : JavaBuilderExtension() {
        @Suppress("RemoveRedundantQualifierName")
        private val javaModuleTypes = java.util.Set.of(JpsJavaModuleType.INSTANCE)

        override fun shouldHonorFileEncodingForCompilation(file: File): Boolean = false

        override fun getCompilableModuleTypes() = javaModuleTypes
      }
    ))

    services.put(SharedThreadPool::class.java, BazelSharedThreadPool)
    services.put(JpsEncodingConfigurationService::class.java, DummyJpsEncodingConfigurationService)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> getService(serviceClass: Class<T>): T {
    services.get(serviceClass)?.let {
      return it as T
    }

    // confine costly service initialization to single thread for defined startup profile
    return synchronized(services) {
      services.computeIfAbsent(serviceClass) {
        doComputeService(it) as T
      } as T
    }
  }

  private fun doComputeService(serviceClass: Class<*>): Any {
    val iterator = ServiceLoader.load(serviceClass, serviceClass.getClassLoader()).iterator()
    if (!iterator.hasNext()) {
      throw ServiceConfigurationError("Implementation for $serviceClass not found")
    }

    val result = iterator.next()
    if (iterator.hasNext()) {
      throw ServiceConfigurationError(
        "More than one implementation for $serviceClass found: ${result::class.java} and ${iterator.next()::class.java}"
      )
    }
    //System.err.println("Extensions for $serviceClass not registered: $result")
    return result
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getExtensions(extensionClass: Class<T>): Iterable<T> {
    extensions.get(extensionClass)?.let {
      return it as List<T>
    }

    // confine costly service initialization to single thread for defined startup profile
    synchronized(extensions) {
      val result = extensions.computeIfAbsent(extensionClass) {
        ServiceLoader.load(it, extensionClass.getClassLoader()).toList()
      } as List<T>

      //System.err.println("Extensions for $extensionClass not registered: $result")
      return result
    }
  }
}

private object DummyJpsEncodingConfigurationService : JpsEncodingConfigurationService() {
  override fun getGlobalEncoding(global: JpsGlobal) = "UTF-8"

  override fun setGlobalEncoding(global: JpsGlobal, encoding: String?) {
    throw UnsupportedOperationException()
  }

  override fun getProjectEncoding(model: JpsModel) = "UTF-8"

  override fun getEncodingConfiguration(project: JpsProject): JpsEncodingProjectConfiguration? = null

  override fun setEncodingConfiguration(
    project: JpsProject,
    projectEncoding: String?,
    urlToEncoding: Map<String, String>
  ): JpsEncodingProjectConfiguration {
    throw UnsupportedOperationException()
  }
}

// remove ResourcesBuilder
private object BazelJavaBuilderService : BuilderService() {
  // remove ResourcesTargetType.ALL_TYPES and ProjectDependenciesResolver.ProjectDependenciesResolvingTargetType.INSTANCE
  override fun getTargetTypes() = listOf(JavaModuleBuildTargetType.PRODUCTION)

  // remove RmiStubsGenerator and DependencyResolvingBuilder
  override fun createModuleLevelBuilders(): List<ModuleLevelBuilder> {
    throw IllegalStateException("must not be called")
  }
}