// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext

import java.nio.channels.FileChannel
import java.nio.file.FileVisitOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.function.Function

/**
 * Recursively computes hashes of files under given paths and writes them in sha*sum utility format.
 */
@CompileStatic
final class ManifestBuilder {
  private static final String NAME = "product-info"
  private static final String CHARSET = "UTF-8"
  private static final String SEPARATOR = " *"

  public final String manifestName

  private final MessageDigest digest

  ManifestBuilder(BuildContext context) {
    digest = MessageDigest.getInstance(context.options.hashAlgorithm)
    String ext = digest.algorithm.toLowerCase(Locale.ENGLISH).replace("-", "")
    manifestName = "${NAME}.${ext}"
  }

  void buildManifest(@NotNull List<String> paths, @NotNull String targetDirectory, @Nullable Function<String, String> mapper = null) {
    Map<String, byte[]> hashes = new TreeMap<String, byte[]>()
    updateHashes(hashes, paths, mapper)
    writeHashes(hashes, new File(targetDirectory, manifestName))
  }

  void updateManifest(@NotNull List<String> paths, @NotNull String targetDirectory) {
    Map<String, byte[]> hashes = new TreeMap<String, byte[]>()
    File file = new File(targetDirectory, manifestName)
    loadHashes(file, hashes)
    updateHashes(hashes, paths, null)
    writeHashes(hashes, file)
  }

  @CompileDynamic
  private void updateHashes(Map<String, byte[]> hashes, List<String> paths, @Nullable Function<String, String> mapper) {
    for (path in paths) {
      Path root = new File(path).toPath()
      Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          String relPath = (file == root ? file.fileName : root.relativize(file)).toString()
          String key = mapper != null ? mapper.apply(relPath) : relPath
          byte[] hash = new RandomAccessFile(file.toFile(), "r").withCloseable {
            digest.update(it.channel.map(FileChannel.MapMode.READ_ONLY, 0, attrs.size()))
            digest.digest()
          }
          hashes[key] = hash
          return FileVisitResult.CONTINUE
        }
      })
    }
  }

  private static void loadHashes(File file, Map<String, byte[]> hashes) {
    new BufferedReader(new InputStreamReader(new FileInputStream(file), CHARSET)).eachLine { line ->
      int p = line.indexOf(SEPARATOR)
      assert p > 0 : "Invalid input: '$line'"
      String relPath = line.substring(p + SEPARATOR.length())
      byte[] hash = StringUtil.parseHexString(line.substring(0, p))
      hashes[relPath] = hash
    }
  }

  private static void writeHashes(Map<String, byte[]> hashes, File file) {
    file.parentFile.mkdirs()
    new PrintWriter(file, CHARSET).withWriter {
      hashes.each { relPath, hash ->
        it.print(StringUtil.toHexString(hash))
        it.print(SEPARATOR)
        it.print(relPath)
        it.println()
      }
    }
  }
}