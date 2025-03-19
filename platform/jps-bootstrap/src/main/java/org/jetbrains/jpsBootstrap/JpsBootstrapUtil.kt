// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap

import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.info
import org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*

object JpsBootstrapUtil {
  const val TEAMCITY_BUILD_PROPERTIES_FILE_ENV = "TEAMCITY_BUILD_PROPERTIES_FILE"
  const val TEAMCITY_CONFIGURATION_PROPERTIES_SYSTEM_PROPERTY = "teamcity.configuration.properties.file"

  fun String.toBooleanChecked(): Boolean {
    return when (this) {
      "true" -> true
      "false" -> false
      else -> error("Could not convert '$this' to boolean. Only 'true' or 'false' values are accepted")
    }
  }

  @get:Throws(IOException::class)
  val teamCitySystemProperties: Properties
    get() {
      check(JpsBootstrapMain.underTeamCity) { "Not under TeamCity" }
      val buildPropertiesFile = System.getenv(TEAMCITY_BUILD_PROPERTIES_FILE_ENV)
      check(!(buildPropertiesFile == null || buildPropertiesFile.length == 0)) { "'TEAMCITY_BUILD_PROPERTIES_FILE_ENV' env. variable is missing or empty under TeamCity build" }
      val properties = Properties()
      Files.newBufferedReader(Path.of(buildPropertiesFile)).use { reader -> properties.load(reader) }
      return properties
    }

  val teamCityConfigProperties: Properties
    get() {
      val systemProperties = teamCitySystemProperties
      val configPropertiesFile = systemProperties.getProperty(TEAMCITY_CONFIGURATION_PROPERTIES_SYSTEM_PROPERTY)
      check(!(configPropertiesFile == null || configPropertiesFile.length == 0)) { "TeamCity system property '$TEAMCITY_CONFIGURATION_PROPERTIES_SYSTEM_PROPERTY' is missing under TeamCity build" }
      val properties = Properties()
      Files.newBufferedReader(Path.of(configPropertiesFile)).use { reader -> properties.load(reader) }
      return properties
    }

  @Throws(IOException::class)
  fun getTeamCityConfigPropertyOrThrow(configProperty: String): String {
    val properties = teamCityConfigProperties
    return properties.getProperty(configProperty)
      ?: throw IllegalStateException("TeamCity config property $configProperty was not found")
  }

  /**
   * Load JPS-consumed system properties from [existingProperties] and set them using [System.setProperty] if not set yet.
   */
  fun loadJpsSystemProperties(existingProperties: Properties) {

    val propertiesToCopy = existingProperties.stringPropertyNames()
      .filter { name -> name.startsWith("org.jetbrains.jps.incremental.dependencies.resolution.") }
      .toMutableList()

    propertiesToCopy += BuildDependenciesConstants.JPS_AUTH_SPACE_PASSWORD
    propertiesToCopy += BuildDependenciesConstants.JPS_AUTH_SPACE_USERNAME

    propertiesToCopy.forEach { name ->
      if (System.getProperty(name) == null) {
        val p = existingProperties.getProperty(name)
        if (p != null) {
          System.setProperty(name, p)
        }
      }
    }
  }

  fun getDefaultSystemPropertiesIfMissing(vararg existingProperties: Properties): Properties {
    val merged = Properties()
    existingProperties.forEach(merged::putAll)

    val defaults = Properties().apply {
      setProperty(DependencyResolvingBuilder.RESOLUTION_RETRY_ENABLED_PROPERTY, "true")
      setProperty(DependencyResolvingBuilder.RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY, "10")
      setProperty(DependencyResolvingBuilder.RESOLUTION_PARALLELISM_PROPERTY, "4")
      setProperty(DependencyResolvingBuilder.RESOLUTION_RETRY_DOWNLOAD_CORRUPTED_ZIP_PROPERTY, "true")
    }

    val result = Properties()
    defaults.stringPropertyNames().forEach { name ->
      if (merged.getProperty(name) == null) {
        result.setProperty(name, defaults.getProperty(name))
      }
    }

    return result
  }

  fun <T> executeTasksInParallel(tasks: List<Callable<T>>): List<T> {
    val executorService = Executors.newFixedThreadPool(5)
    val start = System.currentTimeMillis()
    return try {
      info("Executing " + tasks.size + " in parallel")
      val futures = executorService.invokeAll(tasks)
      val errors: MutableList<Throwable?> = ArrayList()
      val results: MutableList<T> = ArrayList()
      for (future in futures) {
        try {
          val r = future[10, TimeUnit.MINUTES]
          results.add(r)
        }
        catch (e: ExecutionException) {
          errors.add(e.cause)
          if (errors.size > 4) {
            executorService.shutdownNow()
            break
          }
        }
        catch (e: TimeoutException) {
          throw IllegalStateException("Timeout waiting for results, exiting")
        }
      }
      if (errors.size > 0) {
        val t = RuntimeException("Unable to execute all targets, " + errors.size + " error(s)")
        for (err in errors) {
          t.addSuppressed(err)
        }
        throw t
      }
      check(results.size == tasks.size) { "received results size != tasks size (" + results.size + " != " + tasks.size + ")" }
      results
    }
    finally {
      info("Finished all tasks in " + (System.currentTimeMillis() - start) + " ms")
      if (!executorService.isShutdown) {
        executorService.shutdownNow()
      }
    }
  }
}
