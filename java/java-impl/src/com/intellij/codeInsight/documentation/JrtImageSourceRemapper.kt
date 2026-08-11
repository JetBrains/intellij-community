// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.URLUtil

/**
 * Resolves documentation image URLs that point inside a JDK source archive to the corresponding JRT filesystem path.
 *
 * When an image URL points inside a JDK source archive (e.g., `jar:///…/lib/src.zip!/java.desktop/java/awt/doc-files/img.png`),
 * the file may not actually exist in the archive — doc-files images are stored in the JDK's module files instead.
 * This resolver remaps such a URL to the JRT filesystem (`jrt://`) using the JDK home directory derived from the archive path.
 */
internal class JrtImageSourceRemapper : VfsUrlImageSourceRemapper {
  override fun remap(vfsUrl: String): VirtualFile? {
    val jarSeparatorIdx = vfsUrl.indexOf(URLUtil.JAR_SEPARATOR)
    if (jarSeparatorIdx < 0) return null

    val jarPath = vfsUrl
      .take(jarSeparatorIdx)
      .removePrefix(URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR)

    val pathInsideJar = vfsUrl.substring(jarSeparatorIdx + URLUtil.JAR_SEPARATOR.length)

    // Try the archive's parent as JDK home (archive directly under JDK home),
    // cover both `<jdk-home>/<archive>` and `<jdk-home>/lib/<archive>` layouts
    val parent = jarPath.substringBeforeLast('/')
    for (jdkHome in listOf(parent, parent.substringBeforeLast('/'))) {
      if (jdkHome.isEmpty()) continue
      val jrtUrl = URLUtil.JRT_PROTOCOL + URLUtil.SCHEME_SEPARATOR + jdkHome + URLUtil.JAR_SEPARATOR + pathInsideJar
      val file = VirtualFileManager.getInstance().findFileByUrl(jrtUrl)
      if (file != null) return file
    }
    return null
  }
}
