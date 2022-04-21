// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.fileTemplates.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.ResourceUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.URLUtil
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.function.Supplier

internal class FileTemplateLoadResult constructor(
  @JvmField val result: MultiMap<String, DefaultTemplate>,
) {
  companion object {
    @JvmStatic
    fun createSupplier(root: URL, path: String): Supplier<String> {
      // no need to cache the result - it is client responsibility, see DefaultTemplate.getDescriptionText for example
      return object : Supplier<String> {
        override fun get(): String {
          // root url should be used as context to use provided handler to load data to avoid using a generic one
          return try {
            ResourceUtil.loadText(URL(root, "${FileTemplatesLoader.TEMPLATES_DIR}/${path.trimEnd('/')}").openStream())
          }
          catch (e: IOException) {
            thisLogger().error(e)
            ""
          }
        }

        override fun toString() = "(root=$root, path=$path)"
      }
    }

    @JvmStatic
    fun processDirectory(root: URL, result: FileTemplateLoadResult, prefixes: List<String>) {
      val descriptionPaths = HashSet<String>()
      val templateFiles = mutableListOf<String>()
      val rootFile = Path.of(URLUtil.unescapePercentSequences(root.path))

      Files.find(rootFile, Int.MAX_VALUE, BiPredicate { _, a -> a.isRegularFile }).use {
        it.forEach(Consumer { file ->
          val path = rootFile.relativize(file).toString().replace(File.separatorChar, '/')
          if (path.endsWith("/default.html")) {
            result.defaultTemplateDescription = Supplier { Files.readString(file) }
          }
          else if (path.endsWith("/includes/default.html")) {
            result.defaultIncludeDescription = Supplier { Files.readString(file) }
          }
          else if (path.endsWith(".html")) {
            descriptionPaths.add(path)
          }
          else if (path.endsWith(FTManager.TEMPLATE_EXTENSION_SUFFIX)) {
            templateFiles.add(path)
          }
        })
      }

      for (path in templateFiles) {
        for (prefix in prefixes) {
          if (!FileTemplatesLoader.matchesPrefix(path, prefix)) {
            continue
          }

          val filename = path.substring(if (prefix.isEmpty()) 0 else prefix.length + 1,
                                        path.length - FTManager.TEMPLATE_EXTENSION_SUFFIX.length)
          val extension = FileUtilRt.getExtension(filename)
          val templateName = filename.substring(0, filename.length - extension.length - 1)
          val descriptionPath = FileTemplatesLoader.getDescriptionPath(prefix, templateName, extension, descriptionPaths)
          result.result.putValue(
            prefix,
            DefaultTemplate(
              templateName,
              extension,
              Supplier { Files.readString(rootFile.resolve(path)) },
              if (descriptionPath == null) null else Supplier { Files.readString(rootFile.resolve(descriptionPath)) },
              descriptionPath
            )
          )
          break
        }
      }
    }
  }

  @JvmField
  var defaultTemplateDescription: Supplier<String>? = null

  @JvmField
  var defaultIncludeDescription: Supplier<String>? = null
}