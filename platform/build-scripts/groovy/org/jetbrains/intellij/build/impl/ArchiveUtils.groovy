// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.io.Compressor
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import org.apache.tools.tar.TarEntry
import org.apache.tools.tar.TarInputStream
import org.jetbrains.annotations.Nullable

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

@CompileStatic
final class ArchiveUtils {
  static boolean archiveContainsEntry(Path archivePath, String entryPath) {
    File archiveFile = archivePath.toFile()
    String fileName = archiveFile.name
    if (isZipFile(fileName)) {
      return new ZipFile(archiveFile).withCloseable {
        it.getEntry(entryPath) != null
      }
    }

    if (fileName.endsWith(".tar.gz")) {
      return Files.newInputStream(archivePath).withCloseable {
        TarInputStream inputStream = new TarInputStream(new GZIPInputStream(it, 32 * 1024))
        TarEntry entry
        String altEntryPath = "./$entryPath"
        while (null != (entry = inputStream.nextEntry)) {
          if (entry.name == entryPath || entry.name == altEntryPath) {
            return true
          }
        }
        return false
      }
    }
    return false
  }

  static @Nullable String loadEntry(Path archiveFile, String entryPath) {
    String fileName = archiveFile.fileName.toString()
    if (isZipFile(fileName)) {
      ZipFile zipFile = new ZipFile(archiveFile.toFile())
      try {
        def zipEntry = zipFile.getEntry(entryPath)
        if (zipEntry == null) return null
        InputStream inputStream = zipFile.getInputStream(zipEntry)
        return inputStream == null ? null : new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
      }
      finally {
        zipFile.close()
      }
    }
    else if (fileName.endsWith(".tar.gz")) {
      TarInputStream inputStream = new TarInputStream(new GZIPInputStream(Files.newInputStream(archiveFile)))
      try {
        TarEntry entry
        String altEntryPath = "./$entryPath"
        while (null != (entry = inputStream.getNextEntry())) {
          if (entry.name == entryPath || entry.name == altEntryPath) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
          }
        }
      }
      finally {
        inputStream.close()
      }
    }
    return null
  }

  private static boolean isZipFile(String fileName) {
    return fileName.endsWith(".zip") || fileName.endsWith(".jar")
  }

  private static final String TAR_DOCKER_IMAGE = "ubuntu:18.04"
  static boolean isGnuTarAvailable() {
    try {
      if (SystemInfoRt.isLinux) {
        callProcess(["tar", "--version"])
      }
      else {
        callProcess(["docker", "run", "--rm", TAR_DOCKER_IMAGE, "tar", "--version"])
      }
      return true
    }
    catch (Exception e) {
      e.printStackTrace(System.err)
      return false
    }
  }

  /**
   * For Linux hosts GNU Tar command is used to ensure executable flags preserved
   *
   * @param fallbackToPortableTar ignored for Linux hosts since production installers are built there
   */
  static void tar(Path archive, String rootDir, List<String> paths, long buildDateInSeconds, boolean fallbackToPortableTar = true) {
    if (rootDir.endsWith("/")) {
      def trailingSlash = rootDir.lastIndexOf("/")
      rootDir = rootDir.substring(0, trailingSlash)
    }
    if (!SystemInfoRt.isLinux) {
      try {
        gnuTarArchiveInDocker(archive, rootDir, paths, buildDateInSeconds)
      }
      catch (Exception e) {
        if (!fallbackToPortableTar) {
          throw e
        }
        e.printStackTrace(System.err)
        portableTarArchive(archive, rootDir, paths, buildDateInSeconds)
      }
    }
    else if (!isGnuTarAvailable()) {
      throw new Exception("GNU Tar is required")
    }
    else {
      gnuTarArchive(archive, rootDir, paths, buildDateInSeconds)
    }
  }

  /**
   * GNU Tar is assumed to be available locally
   */
  private static void gnuTarArchive(Path archive, String rootDir, List<String> paths, long buildDateInSeconds) {
    new GnuTarArchive(archive, rootDir, paths, buildDateInSeconds).create(false)
  }

  private static void gnuTarArchiveInDocker(Path archive, String rootDir, List<String> paths, long buildDateInSeconds) {
    new GnuTarArchive(archive, rootDir, paths, buildDateInSeconds).create(true)
  }

  static void portableTarArchive(Path archive, String rootDir, List<String> paths, long buildDateInSeconds) {
    new Compressor.Tar(archive.toFile(), Compressor.Tar.Compression.GZIP).withCloseable { compressor ->
      paths.each {
        def path = Paths.get(it)
        if (Files.isDirectory(path)) {
          compressor.addDirectory(rootDir, path, buildDateInSeconds)
        }
        else {
          compressor.addFile("$rootDir/${path.fileName}", path, buildDateInSeconds)
        }
      }
    }
  }

  static void unTar(Path archive, Path destination, @Nullable String rootDirToBeStripped = null) {
    if (SystemInfoRt.isWindows) {
      Decompressor.Tar decompressor = new Decompressor.Tar(archive)
      if (rootDirToBeStripped != null) {
        decompressor.removePrefixPath(rootDirToBeStripped)
      }
      decompressor.extract(destination)
    }
    else {
      // 'tar' command is used to ensure that executable flags will be preserved
      // both GNU Tar and BSD Tar will suffice
      Files.createDirectories(destination)
      List<String> args = new ArrayList<>()
      args.add("tar")
      args.add("--extract")
      args.add("--gzip")
      args.add("--file=${archive.fileName}".toString())
      if (rootDirToBeStripped != null) {
        args.add("--strip")
        args.add("1")
      }
      args.add("--directory")
      args.add(destination.toString())
      callProcess(args, archive.parent)
    }
  }

  private static class GnuTarArchive {
    final List<String> args
    final List<Path> workDirs
    final Map<String, String> env

    GnuTarArchive(Path archive, String rootDir, List<String> paths, long buildDateInSeconds) {
      // gzip stores a timestamp by default in its header, see https://wiki.debian.org/ReproducibleBuilds/TimestampsInGzipHeaders
      this.env = ["GZIP": "--no-name"]
      def args = [
        "tar", "--create", "--gzip",
        "--file=$archive".toString(),
        "--transform=s,^\\.,$rootDir,".toString(),
        "--mtime=@$buildDateInSeconds".toString(),
        "--owner=0", "--group=0", "--numeric-owner",
        "--sort=name", "--exclude=.DS_Store",
        // compatible with previous Ant tar implementation behaviour
        "--format=oldgnu"
      ]
      Path previousWorkDir = null
      this.workDirs = [archive.parent] + paths.collect { Paths.get(it) }.collect {
        Path dir
        Path workDir
        String toArchive
        if (Files.isDirectory(it)) {
          dir = it
          workDir = it
          toArchive = "."
        }
        else {
          dir = it.parent
          workDir = dir
          toArchive = "./" + it.fileName.toString()
        }
        if (previousWorkDir != null) {
          dir = previousWorkDir.relativize(workDir)
        }
        previousWorkDir = workDir
        args += ["--directory=$dir".toString(), toArchive]
        workDir
      }
      this.args = args
    }

    void create(boolean useDocker) {
      if (useDocker) {
        callProcess(
          [
            "docker", "run", "--rm",
            "--workdir", workDirs[0].toString()
          ] + env.entrySet().toList().<String, Map.Entry> collectMany {
            ["--env", "${it.key}=${it.value}".toString(),]
          } + workDirs.<String, Path> collectMany {
            ["--volume", "$it:$it".toString()]
          } + [TAR_DOCKER_IMAGE] + args, workDirs[0]
        )
      }
      else {
        callProcess(args, workDirs[0], env)
      }
    }
  }

  static callProcess(List<String> args,
                             Path workDir = Paths.get(System.getProperty("user.dir")),
                             Map<String, String> env = [:]) {
    ProcessBuilder builder = new ProcessBuilder(args)
      .tap { environment().putAll(env) }
      .directory(workDir.toFile())
      .inheritIO()
    Process process = builder.start()
    if (!process.waitFor(10, TimeUnit.MINUTES)) {
      process.destroyForcibly().waitFor()
      throw new IllegalStateException("Cannot execute $args: 10 min timeout")
    }
    int exitCode = process.exitValue()
    if (exitCode != 0) {
      throw new RuntimeException("Cannot execute $args (exitCode=$exitCode)")
    }
  }
}
