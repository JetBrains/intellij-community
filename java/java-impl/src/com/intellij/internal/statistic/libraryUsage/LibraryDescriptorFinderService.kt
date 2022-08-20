// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
internal class LibraryDescriptorFinderService {
  private val finder: LibraryDescriptorFinder = LibraryLayer.create(downloadLibraryDescriptors())

  val libraryDescriptorFinder: LibraryDescriptorFinder
    get() = finder
}