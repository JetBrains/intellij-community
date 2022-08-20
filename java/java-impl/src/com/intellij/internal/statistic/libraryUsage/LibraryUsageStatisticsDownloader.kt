// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import java.net.URL

internal fun downloadLibraryDescriptors(): List<LibraryDescriptor> {
  val url = createVersionsUrl() ?: return emptyList()
  return deserialize(url) ?: emptyList()
}

private fun createVersionsUrl(): URL? {
  return LibraryDescriptor::class.java.getResource("/com/intellij/internal/statistic/libraryUsage/library-usage-statistics.xml")
}

private fun deserialize(url: URL): List<LibraryDescriptor>? {
  val logger = Logger.getInstance(LibraryDescriptorFinderService::class.java)

  return try {
    XmlSerializer.deserialize(url, TechnologyDescriptors::class.java)
      .descriptors
      .mapNotNull(fun(it: TechnologyDescriptor): LibraryDescriptor? {
        val name = it.name ?: run {
          logger.warn("library without name, root: ${it.root}")
          return null
        }

        val root = it.root ?: run {
          logger.warn("library without root, name: $name")
          return null
        }

        return LibraryDescriptor(name, root)
      })
  }
  catch (e: Exception) {
    if (e is ControlFlowException) throw e
    logger.warn(e)
    null
  }
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