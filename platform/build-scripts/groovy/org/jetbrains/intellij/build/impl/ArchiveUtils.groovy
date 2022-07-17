// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.io.Compressor
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

@CompileStatic
final class ArchiveUtils {
  static boolean isGnuTarAvailable() {
    try {
      if (SystemInfoRt.isLinux) {
        callProcess(["tar", "--version"])
        return true
      }
    }
    catch (Exception e) {
      e.printStackTrace(System.err)
    }
    return false
  }

  /**
   * For Linux hosts GNU Tar command is used for performance reasons.
   */
  static void tar(Path archive, String rootDir, List<String> paths, long buildDateInSeconds) {
    if (rootDir.endsWith("/")) {
      def trailingSlash = rootDir.lastIndexOf("/")
      rootDir = rootDir.substring(0, trailingSlash)
    }
    if (!SystemInfoRt.isLinux) {
      portableTarArchive(archive, rootDir, paths, buildDateInSeconds)
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
  static void gnuTarArchive(Path archive, String rootDir, List<String> paths, long buildDateInSeconds) {
    new GnuTarArchive(archive, rootDir, paths, buildDateInSeconds).create()
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
      // 'tar' command is used for performance reasons
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

    GnuTarArchive(Path archive, String rootDir, List<String> paths, long buildDateInSeconds) {
      // gzip stores a timestamp by default in its header, see https://wiki.debian.org/ReproducibleBuilds/TimestampsInGzipHeaders
      def args = [
        "tar", "--create",
        "--use-compress-program=gzip --no-name",
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

    void create() {
      callProcess(args, workDirs[0])
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
