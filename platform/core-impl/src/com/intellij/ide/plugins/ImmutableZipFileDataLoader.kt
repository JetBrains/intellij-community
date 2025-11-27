// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.lang.ZipEntryResolverPool
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
class ImmutableZipFileDataLoader(private val resolver: ZipEntryResolverPool.EntryResolver, private val zipPath: Path) : DataLoader {
  override fun load(path: String, pluginDescriptorSourceOnly: Boolean): ByteArray? {
    // well, the path maybe specified as `/META-INF/*` in plugin descriptor, and
    // it is our responsibility to normalize the path for ImmutableZipFile API
    // do not use kotlin stdlib here
    return resolver.loadZipEntry(if (path[0] == '/') path.substring(1) else path)
  }

  // yes, by identity - ImmutableZipFileDataLoader is created for the same Path object from the plugin JARs list
  override fun isExcludedFromSubSearch(jarFile: Path): Boolean = jarFile === zipPath

  override fun toString(): String = resolver.toString()
}