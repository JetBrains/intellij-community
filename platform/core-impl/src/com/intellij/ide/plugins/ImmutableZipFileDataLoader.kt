// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.lang.ZipFilePool.EntryResolver
import java.io.InputStream
import java.nio.file.Path

internal class ImmutableZipFileDataLoader(private val resolver: EntryResolver, private val zipPath: Path) : DataLoader {
  override fun load(path: String, pluginDescriptorSourceOnly: Boolean): InputStream? {
    // well, the path maybe specified as `/META-INF/*` in plugin descriptor, and
    // it is our responsibility to normalize the path for ImmutableZipFile API
    // do not use kotlin stdlib here
    return resolver.loadZipEntry(if (path[0] == '/') path.substring(1) else path)
  }

  // yes, by identity - ImmutableZipFileDataLoader is created for the same Path object from the plugin JARs list
  override fun isExcludedFromSubSearch(jarFile: Path): Boolean = jarFile === zipPath

  override fun toString(): String = resolver.toString()
}