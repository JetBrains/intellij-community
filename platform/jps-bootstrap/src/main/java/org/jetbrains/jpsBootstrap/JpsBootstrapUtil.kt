// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap

import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.info
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*

object JpsBootstrapUtil {
  const val TEAMCITY_BUILD_PROPERTIES_FILE_ENV = "TEAMCITY_BUILD_PROPERTIES_FILE"
  const val TEAMCITY_CONFIGURATION_PROPERTIES_SYSTEM_PROPERTY = "teamcity.configuration.properties.file"
  const val JPS_RESOLUTION_RETRY_ENABLED_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.enabled"
  const val JPS_RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.max.attempts"
  const val JPS_RESOLUTION_RETRY_DELAY_MS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.delay.ms"
  const val JPS_RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY = "org.jetbrains.jps.incremental.dependencies.resolution.retry.backoff.limit.ms"

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
   * Create properties to enable artifacts resolution retries in org.jetbrains.jps.incremental.dependencies.DependencyResolvingBuilder
   * if ones absent in `existingProperties`. Latest of `existingProperties` has the highest priority.
   *
   * @param existingProperties Existing properties to check whether required values already present.
   * @return Properties to enable artifacts resolution retries while build.
   */
  fun getJpsArtifactsResolutionRetryProperties(vararg existingProperties: Properties?): Properties {
    val properties = Properties()
    val existingPropertiesMerged = Properties()
    for (it in existingProperties) {
      existingPropertiesMerged.putAll(it!!)
    }
    val enabled = existingPropertiesMerged.getProperty(JPS_RESOLUTION_RETRY_ENABLED_PROPERTY, "true")
    properties[JPS_RESOLUTION_RETRY_ENABLED_PROPERTY] = enabled
    val maxAttempts = existingPropertiesMerged.getProperty(JPS_RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY, "3")
    properties[JPS_RESOLUTION_RETRY_MAX_ATTEMPTS_PROPERTY] = maxAttempts
    val initialDelayMs = existingPropertiesMerged.getProperty(JPS_RESOLUTION_RETRY_DELAY_MS_PROPERTY, "1000")
    properties[JPS_RESOLUTION_RETRY_DELAY_MS_PROPERTY] = initialDelayMs
    val backoffLimitMs = existingPropertiesMerged.getProperty(
      JPS_RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY,
      java.lang.Long.toString(TimeUnit.MINUTES.toMillis(5))
    )
    properties[JPS_RESOLUTION_RETRY_BACKOFF_LIMIT_MS_PROPERTY] = backoffLimitMs
    return properties
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
