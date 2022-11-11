// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import kotlin.jvm.functions.Function4
import org.junit.Test

import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class ArchiveUtilsTest {
  private final Long time = System.currentTimeSeconds()
  private final Path root = FileUtil.createTempDirectory("tar", "test").toPath().tap { FileUtil.delete(it) }
  private final String prefix = "root"
  private final List<String> shouldBeArchived
  private final List<String> expectedInArchive

  ArchiveUtilsTest() {
    def a = createFile("A/a.txt")
    def b = createFile("B/b/b.txt")
    def c = createFile("C/c.txt")
    shouldBeArchived = [a.parent, b, c].collect { it.toString() }
    expectedInArchive = [a, b, c].collect {"$prefix/${it.fileName}".toString()  }
    createFile("B/b/shouldNotBeArchived.txt")
  }

  private Path createFile(String path) {
    root.resolve(path).with {
      Files.createDirectories(it.parent)
      Files.createFile(it)
    }
  }

  @Test
  void tarTest() {
    testTarReproducibility { archive, rootDir, paths, buildDateInSeconds ->
      ArchiveUtils.INSTANCE.tar(archive, rootDir, paths, buildDateInSeconds)
    }
  }

  private void testTarReproducibility(Function4<Path, String, List<String>, Long, Void> tar) {
    def archives = (1..2).collect { i ->
      def archive = testTar(root.resolve("$i"), tar)
      if (i == 1) {
        // sleeping more than a second ensures different last modification time for the next iteration tar
        TimeUnit.SECONDS.sleep(2)
      }
      archive
    }
    assert checksum(archives[0]) == checksum(archives[1])
  }

  private Path testTar(Path iterationDir, Function4<Path, String, List<String>, Long, Void> tar) {
    def archiveName = "archive.tar.gz"
    Files.createDirectories(iterationDir)
    def archive = iterationDir.resolve(archiveName)
    tar.invoke(archive, prefix, shouldBeArchived, time)
    def extractionDir = iterationDir.resolve("result")
    ArchiveUtils.INSTANCE.unTar(archive, extractionDir, null)
    def extracted = Files.walk(extractionDir).withCloseable {
      it.filter { Files.isRegularFile(it) }
        .map { extractionDir.relativize(it).toString() }
        .sorted().collect(Collectors.toList())
    }
    assert extracted == expectedInArchive
    return archive
  }

  private static String checksum(Path path) {
    new BufferedInputStream(Files.newInputStream(path)).withCloseable { input ->
      def digest = MessageDigest.getInstance("SHA-256")
      new DigestInputStream(input, digest).withCloseable {
        def buffer = new byte[128]
        def bytesRead = 0
        while (bytesRead != -1) {
          bytesRead = it.read(buffer)
        }
      }
      Base64.getEncoder().encodeToString(digest.digest())
    }
  }
}
