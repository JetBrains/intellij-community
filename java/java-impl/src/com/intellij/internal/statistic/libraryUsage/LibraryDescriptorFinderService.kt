// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.components.Service
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

@Service(Service.Level.APP)
internal class LibraryDescriptorFinderService {
  private val libraryDescriptorFinder: LibraryLayer = LibraryLayer.create(downloadLibraryDescriptors())

  private fun downloadLibraryDescriptors(): List<LibraryDescriptor> {
    val url = LibraryDescriptor::class.java.getResource("/com/intellij/internal/statistic/libraryUsage/library-usage-statistics.xml")!!

    return XmlSerializer.deserialize(url, TechnologyDescriptors::class.java)
      .descriptors
      .map { LibraryDescriptor(it.name!!, it.root!!) }
  }

  fun findSuitableLibrary(packageQualifier: String): String? = libraryDescriptorFinder.findSuitableLibrary(packageQualifier)
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