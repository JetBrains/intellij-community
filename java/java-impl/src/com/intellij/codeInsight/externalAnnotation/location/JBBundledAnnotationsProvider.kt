// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.externalAnnotation.location

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.intellij.ide.extensionResources.ExtensionsRootType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.util.text.VersionComparatorUtil
import java.nio.file.Files

private val LOG = logger<JBBundledAnnotationsProvider>()

class JBBundledAnnotationsProvider : AnnotationsLocationProvider {
  private val pluginId = PluginManagerCore.JAVA_PLUGIN_ID
  private val knownAnnotations: Map<String, Map<VersionRange, AnnotationsLocation>> by lazy { buildAnnotations() }

  override fun getLocations(project: Project,
                            library: Library,
                            artifactId: String?,
                            groupId: String?,
                            version: String?): Collection<AnnotationsLocation> {

    if (artifactId == null) return emptyList()
    if (groupId == null) return emptyList()
    if (version == null) return emptyList()

    val matchers = knownAnnotations["${groupId}:${artifactId}"] ?: return emptyList()

    return listOf(matchers.entries.find { it.key.matches(version) }?.value ?: return emptyList())
  }

  private fun buildAnnotations(): Map<String, Map<VersionRange, AnnotationsLocation>> {
    val extensionsRootType = ExtensionsRootType.getInstance()
    var file = extensionsRootType.findResource(pluginId, "predefinedExternalAnnotations.json")
    if (file == null || !Files.exists(file)) {
      extensionsRootType.extractBundledResources(pluginId, "")
      file = extensionsRootType.findResource(pluginId, "predefinedExternalAnnotations.json")
      if (file == null || !Files.exists(file)) {
        return emptyMap()
      }
    }

    val gsonBuilder = GsonBuilder()
    gsonBuilder.registerTypeAdapter(VersionRange::class.java, VersionRangeTypeAdapter())
    val raw: Array<RepositoryDescriptor> = try {
      gsonBuilder.create().fromJson(Files.readString(file), Array<RepositoryDescriptor>::class.java)
    }
    catch (e: JsonSyntaxException) {
      LOG.warn("Failed to load annotations repositories descriptors", e)
      emptyArray()
    }

    return raw.asSequence()
      .flatMap { rd ->
        rd.artifacts.asSequence()
          .map { "${it.groupId}:${it.artifactId}" to it.annotations }
          .map { entry ->
            entry.first to
              entry.second.mapValues { rdEntry ->
                AnnotationsLocation(rdEntry.value.groupId,
                                    rdEntry.value.artifactId,
                                    rdEntry.value.version,
                                    rd.repositoryUrl)
              }
          }
      }.toMap()
  }

  private data class RepositoryDescriptor(val repositoryUrl: String, val artifacts: Array<AnnotationMatcher>) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is RepositoryDescriptor) return false

      if (repositoryUrl != other.repositoryUrl) return false
      if (!artifacts.contentEquals(other.artifacts)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = repositoryUrl.hashCode()
      result = 31 * result + artifacts.contentHashCode()
      return result
    }
  }

  private data class AnnotationMatcher(val groupId: String, val artifactId: String, val annotations: Map<VersionRange, ArtifactDescriptor>)

  private class VersionRange(val lowerBound: String, val lowerInclusive: Boolean = false,
                             val upperBound: String, val upperInclusive: Boolean = false) {
    fun matches(version: String): Boolean {
      if (upperBound == lowerBound) {
        return upperBound == version
      }

      val lowerSatisfied = if (lowerInclusive) {
        VersionComparatorUtil.compare(lowerBound, version) <= 0
      }
      else {
        VersionComparatorUtil.compare(lowerBound, version) < 0
      }

      val upperSatisfied = if (upperInclusive) {
        VersionComparatorUtil.compare(version, upperBound) <= 0
      }
      else {
        VersionComparatorUtil.compare(version, upperBound) < 0
      }

      return lowerSatisfied && upperSatisfied
    }
  }

  private data class ArtifactDescriptor(val groupId: String, val artifactId: String, val version: String)

  private class VersionRangeTypeAdapter : TypeAdapter<VersionRange>() {
    override fun read(reader: JsonReader): VersionRange? {
      if (reader.peek() == JsonToken.NULL) {
        reader.nextNull()
        return null
      }
      val rangeString = reader.nextString()

      val beginInclusive = rangeString.startsWith('[')
      val endInclusive = rangeString.endsWith(']')

      val versions = rangeString.trim('[', ']', '(', ')').split(',').map { it.trim() }
      when {
        versions.size > 1 -> return VersionRange(versions[0], beginInclusive, versions[1], endInclusive)
        versions.size == 1 -> return VersionRange(lowerBound = versions[0], upperBound = versions[0])
        else -> throw IllegalArgumentException("Failed to parse string $rangeString as version range.")
      }
    }

    override fun write(writer: JsonWriter, range: VersionRange?) {
      if (range == null) {
        writer.nullValue()
        return
      }

      if (range.lowerBound == range.upperBound) {
        writer.value(range.lowerBound)
        return
      }

      val sb = StringBuilder().apply {
        if (range.lowerInclusive) {
          append("[")
        }
        else {
          append("(")
        }
        append(range.lowerBound)
        append(", ")
        append(range.upperBound)
        if (range.upperInclusive) {
          append(']')
        }
        else {
          append(')')
        }
      }


      writer.value(sb.toString())
    }
  }
}

