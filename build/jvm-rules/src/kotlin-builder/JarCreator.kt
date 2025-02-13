/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.bazel.jvm.kotlin

import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipArchiveOutputStream
import org.jetbrains.intellij.build.io.file
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest

private val MANIFEST_NAME_BYTES = JarFile.MANIFEST_NAME.toByteArray()

/**
 * A class for creating Jar files. Allows normalization of Jar entries by setting their timestamp to
 * the DOS epoch. All Jar entries are sorted alphabetically.
 */
class JarCreator(
  private val targetLabel: String,
  private val injectingRuleKind: String,
  private val out: ZipArchiveOutputStream,
  private val packageIndexBuilder: PackageIndexBuilder,
) : AutoCloseable {
  private var mainClass: String? = null

  private var manifestFile: Path? = null

  /**
   * Adds the contents of a directory to the Jar file. All files below this directory will be added
   * to the Jar file using the name relative to the directory as the name for the Jar entry.
   *
   * @param startDir the directory to add to the jar
   */
  fun addDirectory(startDir: Path) {
    val localPrefixLength = startDir.toString().length + 1
    val dirCandidates = ArrayDeque<Path>()
    dirCandidates.add(startDir)
    val tempList = ArrayList<Path>()
    while (true) {
      val dir = dirCandidates.pollFirst() ?: break
      tempList.clear()
      val dirStream = try {
        Files.newDirectoryStream(dir)
      }
      catch (_: NoSuchFileException) {
        continue
      }

      dirStream.use {
        tempList.addAll(it)
      }

      tempList.sort()
      for (file in tempList) {
        val attributes = Files.readAttributes(file, BasicFileAttributes::class.java)
        val key = file.toString().substring(localPrefixLength).replace(File.separatorChar, '/')
        if (attributes.isDirectory) {
          dirCandidates.add(file)
        }
        else if (key == JarFile.MANIFEST_NAME) {
          manifestFile = file
        }
        else {
          packageIndexBuilder.addFile(key)
          out.file(key, file)
        }
      }
    }
  }

  private fun manifestContentImpl(existingFile: Path?): ByteArray {
    val manifest = if (existingFile == null) {
      Manifest()
    }
    else {
      Files.newInputStream(existingFile).use { Manifest(it) }
    }

    val m = manifest
    m.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
    m.mainAttributes.putIfAbsent(Attributes.Name("Created-By"), "io.bazel.rules.kotlin")

    val attributes = m.mainAttributes

    if (mainClass != null) {
      attributes[Attributes.Name.MAIN_CLASS] = mainClass
    }
    attributes[JarOwner.TARGET_LABEL] = targetLabel
    attributes[JarOwner.INJECTING_RULE_KIND] = injectingRuleKind

    val out = ByteArrayOutputStream()
    manifest.write(out)
    return out.toByteArray()
  }

  private fun writeManifest() {
    packageIndexBuilder.addFile(JarFile.MANIFEST_NAME)

    val content = manifestContentImpl(existingFile = manifestFile)

    out.writeDataRawEntryWithoutCrc(name = MANIFEST_NAME_BYTES, data = content)
  }

  override fun close() {
    writeManifest()

    packageIndexBuilder.writePackageIndex(stream = out, addDirEntriesMode = AddDirEntriesMode.RESOURCE_ONLY)
  }
}
