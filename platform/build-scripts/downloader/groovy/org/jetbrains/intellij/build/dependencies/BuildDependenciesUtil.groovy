// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.dependencies

import groovy.transform.CompileStatic
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.w3c.dom.Element
import org.w3c.dom.Node

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions

@CompileStatic
final class BuildDependenciesUtil {
  static boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")

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

  static void extractZip(Path archiveFile, Path target) {
    new ZipFile(archiveFile.toFile(), "UTF-8").withCloseable { zipFile ->
      zipFile.entries.each { ZipArchiveEntry entry ->
        def entryPath = entryFile(target, entry.name)
        if (entry.isDirectory()) {
          Files.createDirectories(entryPath)
        }
        else {
          zipFile.getInputStream(entry).withCloseable { entryInputStream ->
            Files.copy(entryInputStream, entryPath)
          }

          if (isPosix && (entry.unixMode & 0111) != 0) {
            Files.setPosixFilePermissions(entryPath, PosixFilePermissions.fromString("rwxr-xr-x"))
          }
        }
      }
    }
  }

  private static char forwardSlash = '/'

  static Path entryFile(Path outputDir, String entryName) throws IOException {
    ensureValidPath(entryName)
    return outputDir.resolve(trimStart(entryName, forwardSlash))
  }

  private static void ensureValidPath(String entryName) throws IOException {
    if (entryName.contains("..") && entryName.split("[/\\\\]").contains("..")) {
      throw new IOException("Invalid entry name: " + entryName)
    }
  }

  private static String trimStart(String s, char charToTrim) {
    int index = 0
    while (s.charAt(index) == charToTrim) index++
    return s.substring(index)
  }

  static void cleanDirectory(Path directory) {
    Files.createDirectories(directory)
    Files.list(directory).withCloseable { pathStream ->
      pathStream.each { Path path -> deleteRecursively(path) }
    }
  }
}
