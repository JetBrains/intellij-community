// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.internal.statistic.libraryUsage

import com.intellij.facet.frameworks.LibrariesDownloadConnectionService
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import java.io.IOException
import java.net.URL

@RequiresBackgroundThread
fun downloadLibraryDescriptors(): List<LibraryDescriptor>? {
  val url = createVersionsUrl() ?: return null
  return deserialize(url)?.ifEmpty {
    LOG.warn("the list of technologies is empty")
    null
  }
}

private const val FILE_NAME = "statistics/library-usage-statistics.xml"

@Suppress("SSBasedInspection")
private val LOG = Logger.getInstance("#com.intellij.internal.statistic.libraryUsage.LibraryUsageStatisticsDownloaderKt")

private fun createVersionsUrl(): URL? {
  val serviceUrl = LibrariesDownloadConnectionService.getInstance().serviceUrl?.takeIf(String::isNotEmpty) ?: return null
  return try {
    val url = "$serviceUrl/$FILE_NAME"
    HttpConfigurable.getInstance().prepareURL(url)
    return URL(url)
  }
  catch (e: IOException) {
    LOG.warn(e)
    null // no route to host, unknown host, malformed url, etc.
  }
}

private fun deserialize(url: URL): List<LibraryDescriptor>? = try {
  XmlSerializer.deserialize(url, TechnologyDescriptors::class.java)
    .descriptors
    .mapNotNull(fun(it: TechnologyDescriptor): LibraryDescriptor? {
      val name = it.name ?: run {
        LOG.warn("library without name, root: ${it.root}")
        return null
      }

      val root = it.root ?: run {
        LOG.warn("library without root, name: $name")
        return null
      }

      return LibraryDescriptor(name, root)
    })
}
catch (e: Exception) {
  if (e is ControlFlowException) throw e
  LOG.warn(e)
  null
}

@Tag("technologies")
private class TechnologyDescriptors {
  @get:Property(surroundWithTag = false)
  @get:XCollection(elementTypes = [TechnologyDescriptor::class], elementName = "technology")
  val descriptors: MutableList<TechnologyDescriptor> = mutableListOf()
}

@Tag("technology")
private class TechnologyDescriptor {
  @get:Attribute("name")
  var name: String? = null

  @get:Attribute("root")
  var root: String? = null
}