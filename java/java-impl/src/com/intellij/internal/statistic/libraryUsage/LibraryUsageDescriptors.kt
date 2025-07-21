// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

public object LibraryUsageDescriptors {
  private val descriptors: List<LibraryDescriptor> = downloadLibraryDescriptors()
  private val libraryDescriptorFinder: LibraryLayer = LibraryLayer.create(downloadLibraryDescriptors())

  private fun downloadLibraryDescriptors(): List<LibraryDescriptor> {
    if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return emptyList()

    val url = LibraryDescriptor::class.java.getResource("/com/intellij/internal/statistic/libraryUsage/library-usage-statistics.xml")!!

    return XmlSerializer.deserialize(url, TechnologyDescriptors::class.java)
      .descriptors
      .map { LibraryDescriptor(it.name!!, it.root!!) }
  }

  public fun findSuitableLibrary(packageQualifier: String): String? = libraryDescriptorFinder.findSuitableLibrary(packageQualifier)

  public val libraryNames: Set<String>
    get() = descriptors.mapTo(HashSet()) { it.libraryName }
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