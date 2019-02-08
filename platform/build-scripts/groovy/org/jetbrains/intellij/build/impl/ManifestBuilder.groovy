// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.BuildContext

import java.nio.channels.FileChannel
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/**
 * Recursively computes hashes of files under given paths and writs them in sha*sum utility format.
 */
@CompileStatic
class ManifestBuilder {
  private final MessageDigest digest

  ManifestBuilder(BuildContext context) {
    digest = MessageDigest.getInstance(context.options.hashAlgorithm)
  }

  String buildManifest(List<String> paths, String basePath) {
    Map<String, byte[]> hashes = collectHashes(paths)
    String ext = digest.algorithm.toLowerCase(Locale.ENGLISH).replace("-", "")
    String path = "${basePath}.${ext}"
    writeHashes(hashes, path)
    return path
  }

  private SortedMap<String, byte[]> collectHashes(List<String> paths) {
    Map<String, byte[]> hashes = new TreeMap<String, byte[]>()

    paths.each {
      Path root = new File(it).toPath()
      Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          String relPath = (file == root ? file.fileName : root.relativize(file)).toString()
          byte[] hash = new RandomAccessFile(file.toFile(), "r").withCloseable {
            digest.update(it.channel.map(FileChannel.MapMode.READ_ONLY, 0, attrs.size()))
            digest.digest()
          }
          hashes[relPath] = hash
          return FileVisitResult.CONTINUE
        }
      })
    }

    return hashes
  }

  private static String writeHashes(SortedMap<String, byte[]> hashes, String path) {
    new File(path).parentFile.mkdirs()
    new PrintWriter(path, "UTF-8").withWriter {
      hashes.each { relPath, digest ->
        it.print(StringUtil.toHexString(digest))
        it.print(" *")
        it.print(relPath)
        it.println()
      }
    }
    return path
  }
}