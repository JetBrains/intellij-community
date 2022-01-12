// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.dependencies

import groovy.transform.CompileStatic
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nullable
import org.w3c.dom.Element
import org.w3c.dom.Node

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions

@CompileStatic
@ApiStatus.Internal
final class BuildDependenciesUtil {
  static boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

  @SuppressWarnings('HttpUrlsUsage')
  static DocumentBuilder createDocumentBuilder() {
    // from https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance()
    try {
      String FEATURE

      // This is the PRIMARY defense. If DTDs (doctype) are disallowed, almost all
      // XML entity attacks are prevented
      // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
      FEATURE = "http://apache.org/xml/features/disallow-doctype-decl"
      dbf.setFeature(FEATURE, true)

      // If you can't completely disable DTDs, then at least do the following:
      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
      // JDK7+ - http://xml.org/sax/features/external-general-entities
      //This feature has to be used together with the following one, otherwise it will not protect you from XXE for sure
      FEATURE = "http://xml.org/sax/features/external-general-entities"
      dbf.setFeature(FEATURE, false)

      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
      // JDK7+ - http://xml.org/sax/features/external-parameter-entities
      //This feature has to be used together with the previous one, otherwise it will not protect you from XXE for sure
      FEATURE = "http://xml.org/sax/features/external-parameter-entities"
      dbf.setFeature(FEATURE, false)

      // Disable external DTDs as well
      FEATURE = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
      dbf.setFeature(FEATURE, false)

      // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
      dbf.setXIncludeAware(false)
      dbf.setExpandEntityReferences(false)

      // And, per Timothy Morgan: "If for some reason support for inline DOCTYPE are a requirement, then
      // ensure the entity settings are disabled (as shown above) and beware that SSRF attacks
      // (http://cwe.mitre.org/data/definitions/918.html) and denial
      // of service attacks (such as billion laughs or decompression bombs via "jar:") are a risk."
    }
    catch (Throwable throwable) {
      throw new IllegalStateException("Unable to create DOM parser", throwable)
    }

    return dbf.newDocumentBuilder()
  }

  static Element getSingleChildElement(Element parent, String tagName) {
    def childNodes = parent.childNodes

    def result = new ArrayList<Element>()
    for (int i = 0; i < childNodes.length; i++) {
      Node node = childNodes.item(i)
      if (node instanceof Element) {
        Element element = (Element)node
        if (element != null && element.tagName == tagName) {
          result.add(element)
        }
      }
    }

    if (result.size() != 1) {
      throw new IllegalStateException("Expected one and only one element by tag '$tagName'")
    }
    return result.first()
  }

  static String getLibraryMavenId(Path libraryXml) {
    try {
      def documentBuilder = createDocumentBuilder()
      def document = documentBuilder.parse(libraryXml.toFile())

      def libraryElement = getSingleChildElement(document.documentElement, "library")
      def propertiesElement = getSingleChildElement(libraryElement, "properties")
      def mavenId = propertiesElement.getAttribute("maven-id")
      if (mavenId == null || mavenId.isBlank()) {
        throw new IllegalStateException("Invalid maven-id")
      }

      return mavenId
    }
    catch (Throwable t) {
      throw new IllegalStateException("Unable to load maven-id from ${libraryXml}: ${t.message}", t)
    }
  }


  static void deleteRecursively(Path path) {
    if (!Files.exists(path)) {
      throw new IOException("Path does not exist: $path")
    }

    if (!Files.isDirectory(path)) {
      Files.delete(path)
      return
    }

    Files.walkFileTree(
      path,
      new SimpleFileVisitor<Path>() {
        @Override
        FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
          Files.delete(dir)
          return FileVisitResult.CONTINUE
        }

        @Override
        FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Files.delete(file)
          return FileVisitResult.CONTINUE
        }
      })

    if (Files.exists(path)) {
      throw new IllegalStateException("Directory is still exists after deletion: $path")
    }
  }

  static String normalizeEntryName(String name) {
    String withForwardSlashes = name.replace(backwardSlash, forwardSlash)
    String trimmed = trim(withForwardSlashes, forwardSlash)
    assertValidEntryName(trimmed)
    return trimmed
  }

  static void extractZip(Path archiveFile, Path target, boolean stripRoot) {
    new ZipFile(archiveFile.toFile(), "UTF-8").withCloseable { zipFile ->
      EntryNameConverter converter = new EntryNameConverter(archiveFile, target, stripRoot)

      zipFile.entries.each { ZipArchiveEntry entry ->
        def entryPath = converter.getOutputPath(entry.name, entry.isDirectory())
        if (entryPath == null) return

        if (entry.isDirectory()) {
          Files.createDirectories(entryPath)
        }
        else {
          Files.createDirectories(entryPath.parent)

          zipFile.getInputStream(entry).withCloseable { entryInputStream ->
            Files.copy(entryInputStream, entryPath)
          }

          if (isPosix && (entry.unixMode & 0111) != 0) {
            //noinspection SpellCheckingInspection
            Files.setPosixFilePermissions(entryPath, PosixFilePermissions.fromString("rwxr-xr-x"))
          }
        }
      }
    }
  }

  static void extractTarGz(Path archiveFile, Path target, boolean stripRoot) {
    new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(archiveFile)))).withCloseable { archive ->
      EntryNameConverter converter = new EntryNameConverter(archiveFile, target, stripRoot)

      while (true) {
        TarArchiveEntry entry = (TarArchiveEntry) archive.getNextEntry()
        if (Objects.isNull(entry)) break

        def entryPath = converter.getOutputPath(entry.name, entry.isDirectory())
        if (entryPath == null) continue

        if (entry.isDirectory()) {
          Files.createDirectories(entryPath)
        }
        else {
          Files.createDirectories(entryPath.parent)

          Files.copy(archive, entryPath)

          if (isPosix && (entry.mode & 0111) != 0) {
            //noinspection SpellCheckingInspection
            Files.setPosixFilePermissions(entryPath, PosixFilePermissions.fromString("rwxr-xr-x"))
          }
        }
      }
    }
  }

  private static class EntryNameConverter {
    private final Path archiveFile
    private final Path target
    private final boolean stripRoot

    private String leadingComponentPrefix = null

    EntryNameConverter(Path archiveFile, Path target, boolean stripRoot) {
      this.stripRoot = stripRoot
      this.target = target
      this.archiveFile = archiveFile
    }

    @Nullable
    Path getOutputPath(String entryName, boolean isDirectory) {
      String normalizedName = normalizeEntryName(entryName)
      if (!stripRoot) {
        return target.resolve(normalizedName)
      }

      if (leadingComponentPrefix == null) {
        String[] split = normalizedName.split(forwardSlash.toString(), 2)
        leadingComponentPrefix = split[0] + forwardSlash

        if (split.length < 2) {
          if (!isDirectory) {
            throw new IllegalStateException("$archiveFile: first top-level entry must be a directory if strip root is enabled")
          }

          return null
        }
        else {
          return target.resolve(split[1])
        }
      }

      if (!normalizedName.startsWith(leadingComponentPrefix)) {
        throw new IllegalStateException(
          "$archiveFile: entry name '" + normalizedName + "' should start with previously found prefix '" + leadingComponentPrefix + "'")
      }

      return target.resolve(normalizedName.substring(leadingComponentPrefix.size()))
    }
  }

  private static char backwardSlash = '\\'
  private static String backwardSlashString = backwardSlash.toString()
  private static char forwardSlash = '/'
  private static String forwardSlashString = forwardSlash.toString()
  private static String doubleForwardSlashString = forwardSlashString + forwardSlashString

  private static void assertValidEntryName(String normalizedEntryName) throws IOException {
    if (normalizedEntryName.isBlank()) {
      throw new IllegalStateException("Entry names should not be blank: '" + normalizedEntryName + "'")
    }
    if (normalizedEntryName.indexOf(backwardSlashString) >= 0) {
      throw new IllegalStateException("Normalized entry names should not contain '" + backwardSlashString + "'")
    }
    if (normalizedEntryName.startsWith(forwardSlashString)) {
      throw new IllegalStateException("Normalized entry names should not start with forward slash: " + normalizedEntryName)
    }
    if (normalizedEntryName.endsWith(forwardSlashString)) {
      throw new IllegalStateException("Normalized entry names should not end with forward slash: " + normalizedEntryName)
    }
    if (normalizedEntryName.contains(doubleForwardSlashString)) {
      throw new IllegalStateException("Normalized entry name should not contain '" + doubleForwardSlashString + "': " + normalizedEntryName)
    }
    if (normalizedEntryName.contains("..") && normalizedEntryName.split(forwardSlashString).contains("..")) {
      throw new IOException("Invalid entry name: " + normalizedEntryName)
    }
  }

  static String trim(String s, char charToTrim) {
    int len = s.length()
    int start = 0
    while (start < len && s.charAt(start) == charToTrim) start++
    int end = len
    while (end > 0 && start < end && s.charAt(end - 1) == charToTrim) end--
    return s.substring(start, end)
  }

  static void cleanDirectory(Path directory) {
    Files.createDirectories(directory)
    Files.list(directory).withCloseable { pathStream ->
      pathStream.each { Path path -> deleteRecursively(path) }
    }
  }
}
