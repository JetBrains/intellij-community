// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

/**
 * Define an information about Linux RPM package to be downloaded
 */
@CompileStatic
class LinuxLibraryDownloadInfo {

  public String libraryName
  public String version
  public Path downloadPath
  public String fileExtension
  public String architecture
  private Closure copyLibraryAction

  private static String LINUX_LIB_URL_BASE = "https://cache-redirector.jetbrains.com/archive.kernel.org/centos-vault"
  private static String LINUX_OS_VERSION = "7.0.1406"
  static String DEFAULT_ARCHITECTURE = "x86_64"
  static String DEFAULT_EXTENSION = "rpm"

  LinuxLibraryDownloadInfo(String name, String version) {
    this(name, version, DEFAULT_ARCHITECTURE)
  }

  LinuxLibraryDownloadInfo(String name, String version, String architecture) {
    this(name, version, architecture, null)
  }

  LinuxLibraryDownloadInfo(String name, String version, Closure closure) {
    this(name, version, DEFAULT_ARCHITECTURE, closure)
  }

  LinuxLibraryDownloadInfo(String name, String version, String architecture, Closure closure) {
    this.libraryName = name
    this.version = version
    this.architecture = architecture
    this.copyLibraryAction = closure
    this.fileExtension = DEFAULT_EXTENSION
  }

  URI getUri() {
    return new URI(
      "${LINUX_LIB_URL_BASE}/${LINUX_OS_VERSION}/os/x86_64/Packages/${libraryName}-${version}.el7.${architecture}.${fileExtension}")
  }

  /**
   * The action is used for preparing a directory structure after package is extracted and is copying into a destination folder.
   * @return a closure with action for filtering files inside a package
   */
  Closure getLibraryCopyAction() {
    if (copyLibraryAction != null) return copyLibraryAction

    return { Path source, Path target ->
      Path libsTargetPath = Files.createDirectories(target.resolve("libs"))
      source.eachFileRecurse { sourceFilePath ->
        String fileName = sourceFilePath.fileName
        if (fileName.contains("${this.libraryName}.so.") && Files.isSymbolicLink(sourceFilePath)) {
          Path relativeLibPath = Files.readSymbolicLink(sourceFilePath)
          Path sourceLibPath = sourceFilePath.parent.resolve(relativeLibPath)

          if (Files.exists(sourceLibPath)) {
            Path targetLibPath = libsTargetPath.resolve(fileName)
            LinuxDistributionBuilder.copyFile(sourceLibPath, targetLibPath)
          }
        }
      }
    }
  }
}
