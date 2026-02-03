// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.input.CloseShieldInputStream
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.BuildDependenciesUtil.normalizeEntryName
import org.w3c.dom.Element
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries

@OptIn(ExperimentalPathApi::class)
@ApiStatus.Internal
object BuildDependenciesUtil {
  private val LOG = Logger.getLogger(BuildDependenciesUtil::class.java.name)
  private val octal_0111 = "111".toInt(8)

  val isWindows: Boolean = System.getProperty("os.name").startsWith("windows", ignoreCase = true)

  @Suppress("HttpUrlsUsage")
  fun createDocumentBuilder(): DocumentBuilder {
    // from https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#jaxp-documentbuilderfactory-saxparserfactory-and-dom4j
    val dbf = DocumentBuilderFactory.newDefaultInstance()
    return try {
      // This is the PRIMARY defense. If DTDs (doctype) are disallowed, almost all
      // XML entity attacks are prevented
      // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
      var feature = "http://apache.org/xml/features/disallow-doctype-decl"
      dbf.setFeature(feature, true)

      // If you can't completely disable DTDs, then at least do the following:
      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-general-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-general-entities
      // JDK7+ - http://xml.org/sax/features/external-general-entities
      //This feature has to be used together with the following one, otherwise it will not protect you from XXE for sure
      feature = "http://xml.org/sax/features/external-general-entities"
      dbf.setFeature(feature, false)

      // Xerces 1 - http://xerces.apache.org/xerces-j/features.html#external-parameter-entities
      // Xerces 2 - http://xerces.apache.org/xerces2-j/features.html#external-parameter-entities
      // JDK7+ - http://xml.org/sax/features/external-parameter-entities
      //This feature has to be used together with the previous one, otherwise it will not protect you from XXE for sure
      feature = "http://xml.org/sax/features/external-parameter-entities"
      dbf.setFeature(feature, false)

      // Disable external DTDs as well
      feature = "http://apache.org/xml/features/nonvalidating/load-external-dtd"
      dbf.setFeature(feature, false)

      // and these as well, per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
      dbf.isXIncludeAware = false
      dbf.isExpandEntityReferences = false

      // And, per Timothy Morgan: "If for some reason support for inline DOCTYPE is a requirement, then
      // ensure the entity settings are disabled (as shown above) and beware that SSRF attacks
      // (http://cwe.mitre.org/data/definitions/918.html) and denial
      // of service attacks (such as a billion laughs or decompression bombs via "jar:") are a risk."
      dbf.newDocumentBuilder()
    }
    catch (throwable: Throwable) {
      throw IllegalStateException("Unable to create DOM parser", throwable)
    }
  }

  fun Element.getChildElements(tagName: String): List<Element> {
    val childNodes = childNodes
    val result = ArrayList<Element>()
    for (i in 0 until childNodes.length) {
      val node = childNodes.item(i)
      if (node is Element && tagName == node.tagName) {
        result.add(node)
      }
    }
    return result
  }

  fun Element.getComponentElement(componentName: String): Element {
    val elements = this.getChildElements("component").filter { x -> componentName == x.getAttribute("name") }
    check(elements.size == 1) { "Expected one and only one component with name '$componentName'" }
    return elements[0]
  }

  val Element.asText: String
    get() {
      val transFactory = TransformerFactory.newInstance()
      val transformer: Transformer = transFactory.newTransformer()
      val buffer = StringWriter()
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
      transformer.transform(DOMSource(this), StreamResult(buffer))
      return buffer.toString().replace("\r", "")
    }

  fun Element.getSingleChildElement(tagName: String): Element {
    val result = this.getChildElements(tagName)
    check(result.size == 1) { "Expected one and only one element by tag '$tagName'" }
    return result[0]
  }

  fun Element.tryGetSingleChildElement(tagName: String): Element? {
    val result = this.getChildElements(tagName)
    return if (result.size == 1) result[0] else null
  }

  fun getLibraryMavenId(libraryXml: Path): String {
    return try {
      val documentBuilder = createDocumentBuilder()
      val document = documentBuilder.parse(libraryXml.toFile())
      val libraryElement = document.documentElement.getSingleChildElement("library")
      val propertiesElement = libraryElement.getSingleChildElement("properties")
      val mavenId = propertiesElement.getAttribute("maven-id")
      check(!mavenId.isBlank()) { "Invalid maven-id" }
      mavenId
    }
    catch (t: Throwable) {
      throw IllegalStateException("Unable to load maven-id from " + libraryXml + ": " + t.message, t)
    }
  }

  fun Element.getLibraryElement(libraryName: String, iml: Path): Element {
    val rootManager = this.getComponentElement("NewModuleRootManager")
    val library = rootManager.getChildElements("orderEntry")
                    .filter { it.getAttribute("type") == "module-library" }
                    .map { it.getSingleChildElement("library") }
                    .singleOrNull { it.getAttribute("name") == libraryName }
                  ?: error("Library '$libraryName' was not found in '$iml'")

    return library
  }

  fun extractZip(archiveFile: Path, target: Path, stripRoot: Boolean) {
    ZipFile.Builder().setSeekableByteChannel(FileChannel.open(archiveFile)).get().use { zipFile ->
      val entries = zipFile.entries
      genericExtract(archiveFile, object : ArchiveContent {
        override val nextEntry: Entry?
          get() {
            if (!entries.hasMoreElements()) {
              return null
            }
            val entry = entries.nextElement()
            return object : Entry {
              override val type: Entry.Type
                get() = when {
                  entry.isUnixSymlink -> Entry.Type.SYMLINK
                  entry.isDirectory -> Entry.Type.DIR
                  else -> Entry.Type.FILE
                }
              override val name: String
                get() = entry.name
              override val isExecutable: Boolean
                get() = entry.unixMode and octal_0111 != 0
              @get:Throws(IOException::class)
              override val linkTarget: String?
                get() = zipFile.getUnixSymlink(entry)
              @get:Throws(IOException::class)
              override val inputStream: InputStream
                get() = zipFile.getInputStream(entry)
            }
          }
      }, target, stripRoot)
    }
  }

  fun extractTarBz2(archiveFile: Path, target: Path, stripRoot: Boolean) {
    extractTarBasedArchive(archiveFile, target, stripRoot) { BZip2CompressorInputStream(it) }
  }

  fun extractTarGz(archiveFile: Path, target: Path, stripRoot: Boolean) {
    extractTarBasedArchive(archiveFile, target, stripRoot) { GzipCompressorInputStream(it) }
  }

  private fun extractTarBasedArchive(archiveFile: Path, target: Path, stripRoot: Boolean, decompressor: (InputStream) -> InputStream) {
    TarArchiveInputStream(decompressor(BufferedInputStream(Files.newInputStream(archiveFile)))).use { archive ->
      genericExtract(archiveFile, object : ArchiveContent {
        @get:Throws(IOException::class)
        override val nextEntry: Entry?
          get() {
            val entry = archive.nextEntry ?: return null
            return object : Entry {
              override val type: Entry.Type
                get() = when {
                  entry.isSymbolicLink -> Entry.Type.SYMLINK
                  entry.isDirectory -> Entry.Type.DIR
                  entry.isFile -> Entry.Type.FILE
                  else -> throw IllegalStateException("${archiveFile}: unknown entry type at '${entry.name}")
                }
              override val name: String
                get() = entry.name
              override val isExecutable: Boolean
                get() = entry.mode and octal_0111 != 0
              override val linkTarget: String?
                get() = entry.linkName
              override val inputStream: InputStream
                get() = CloseShieldInputStream.wrap(archive)
            }
          }
      }, target, stripRoot)
    }
  }

  private fun genericExtract(archiveFile: Path, archive: ArchiveContent, target: Path, stripRoot: Boolean) {
    val isPosixFs = target.fileSystem.supportedFileAttributeViews().contains("posix")

    // avoid extra createDirectories calls
    val createdDirs: MutableSet<Path> = HashSet()
    val converter = EntryNameConverter(archiveFile, target, stripRoot)
    val canonicalTarget = target.normalize()
    while (true) {
      val entry: Entry = archive.nextEntry ?: break
      val type: Entry.Type = entry.type
      val entryPath = converter.getOutputPath(entry.name, type == Entry.Type.DIR) ?: continue
      if (type == Entry.Type.DIR) {
        Files.createDirectories(entryPath)
        createdDirs.add(entryPath)
      }
      else {
        val parent = entryPath.parent
        if (createdDirs.add(parent)) {
          Files.createDirectories(parent)
        }
        if (type == Entry.Type.SYMLINK) {
          val relativeSymlinkTarget = Path.of(entry.linkTarget!!)
          val resolvedTarget = entryPath.resolveSibling(relativeSymlinkTarget).normalize()
          if (!resolvedTarget.startsWith(canonicalTarget) || resolvedTarget == canonicalTarget) {
            LOG.fine("""
  $archiveFile: skipping symlink entry '${entry.name}' which points outside of archive extraction directory, which is forbidden.
  resolved target = $resolvedTarget
  root = $canonicalTarget
  
  """.trimIndent())
            continue
          }
          if (isWindows) {
            // On Windows symlink creation is still gated by various registry keys
            if (Files.isRegularFile(resolvedTarget)) {
              Files.copy(resolvedTarget, entryPath, StandardCopyOption.REPLACE_EXISTING)
            }
          }
          else {
            Files.createSymbolicLink(entryPath, relativeSymlinkTarget)
          }
        }
        else if (type == Entry.Type.FILE) {
          entry.inputStream.use { fs -> Files.copy(fs, entryPath, StandardCopyOption.REPLACE_EXISTING) }
          if (isPosixFs && entry.isExecutable) {
            @Suppress("SpellCheckingInspection")
            Files.setPosixFilePermissions(entryPath, PosixFilePermissions.fromString("rwxr-xr-x"))
          }
        }
        else {
          throw IllegalStateException("Unknown entry type: $type")
        }
      }
    }
  }

  fun normalizeEntryName(name: String): String {
    val normalized = name.replace('\\', '/').trim('/')
    assertValidEntryName(normalized)
    return normalized
  }

  private fun assertValidEntryName(normalizedEntryName: String) {
    check(!normalizedEntryName.isBlank()) { "Entry names should not be blank" }
    check(!normalizedEntryName.contains('\\')) { "Normalized entry names should not contain '\\'" }
    check(!normalizedEntryName.startsWith('/')) { "Normalized entry names should not start with '/': ${normalizedEntryName}" }
    check(!normalizedEntryName.endsWith('/')) { "Normalized entry names should not end with '/': ${normalizedEntryName}" }
    check(!normalizedEntryName.contains("//")) { "Normalized entry name should not contain '//': ${normalizedEntryName}" }
    check(!(normalizedEntryName.contains("..") && normalizedEntryName.split('/').contains(".."))) { "Invalid entry name: ${normalizedEntryName}" }
  }

  fun deleteFileOrFolder(file: Path) {
    file.deleteRecursively()
  }

  fun cleanDirectory(directory: Path) {
    Files.createDirectories(directory)
    directory.listDirectoryEntries().forEach { deleteFileOrFolder(it) }
  }

  fun loadPropertiesFile(file: Path): Map<String, String> {
    return Files.newBufferedReader(file).use { val properties = Properties(); properties.load(it); properties }
      .map { (k, v) -> k as String to v as String }
      .toMap()
  }

  fun listDirectory(directory: Path): List<Path> = Files.newDirectoryStream(directory).use { it.toList() }

  fun directoryContentToString(directory: Path, humanReadableName: String?): String {
    val contents = listDirectory(directory)
    val sb = StringBuilder()
    sb.append("Directory contents of ")
    sb.append(directory.toAbsolutePath())
    sb.append(" (")
    sb.append(humanReadableName)
    sb.append("), ")
    sb.append(contents.size)
    sb.append(" entries:")
    for (p in contents) {
      sb.append(if (Files.isDirectory(p)) "\nD " else "\nF ")
      sb.append(p.fileName.toString())
    }
    return sb.toString()
  }
}

private interface ArchiveContent {
  val nextEntry: Entry?
}

private interface Entry {
  enum class Type { FILE, DIR, SYMLINK }

  val type: Type
  val name: String
  val isExecutable: Boolean
  val linkTarget: String?
  val inputStream: InputStream
}

private class EntryNameConverter(private val archiveFile: Path, private val target: Path, private val stripRoot: Boolean) {
  private var leadingComponentPrefix: String? = null
  fun getOutputPath(entryName: String, isDirectory: Boolean): Path? {
    val normalizedName = normalizeEntryName(entryName)
    if (!stripRoot) {
      return target.resolve(normalizedName)
    }
    if (leadingComponentPrefix == null) {
      val split = normalizedName.split('/'.toString().toRegex(), limit = 2).toTypedArray()
      leadingComponentPrefix = split[0] + '/'
      return if (split.size < 2) {
        check(isDirectory) { "$archiveFile: first top-level entry must be a directory if strip root is enabled" }
        null
      }
      else {
        target.resolve(split[1])
      }
    }
    check(normalizedName.startsWith(
      leadingComponentPrefix!!)) { "$archiveFile: entry name '$normalizedName' should start with previously found prefix '$leadingComponentPrefix'" }
    return target.resolve(normalizedName.substring(leadingComponentPrefix!!.length))
  }
}
