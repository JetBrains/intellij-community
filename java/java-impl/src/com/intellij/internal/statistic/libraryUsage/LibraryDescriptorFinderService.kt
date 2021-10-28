// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.components.Service
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.APP)
class LibraryDescriptorFinderService {
  private val lock = ReentrantReadWriteLock()
  private var libraryDescriptorFinder: LibraryDescriptorFinder? = null

  @RequiresBackgroundThread
  fun libraryDescriptorFinder(): LibraryDescriptorFinder? {
    cachedLibraryDescriptorFinder()?.let { return it }

    lock.write {
      if (libraryDescriptorFinder != null) return libraryDescriptorFinder

      val descriptors = downloadLibraryDescriptors() ?: return null

      libraryDescriptorFinder = LibraryLayer.create(descriptors)
      return libraryDescriptorFinder
    }
  }
  
  fun cachedLibraryDescriptorFinder(): LibraryDescriptorFinder? = lock.read { libraryDescriptorFinder }
}

