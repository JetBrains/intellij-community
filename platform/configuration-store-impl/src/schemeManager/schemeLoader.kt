package com.intellij.configurationStore.schemeManager

import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.createDirectories
import com.intellij.util.io.systemIndependentPath
import org.xmlpull.mxp1.MXParser
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.nio.file.Path
import java.util.*

internal inline fun lazyPreloadScheme(bytes: ByteArray, isOldSchemeNaming: Boolean, consumer: (name: String?, parser: XmlPullParser) -> Unit) {
  val parser = MXParser()
  parser.setInput(bytes.inputStream().reader())
  consumer(preload(isOldSchemeNaming, parser), parser)
}

private fun preload(isOldSchemeNaming: Boolean, parser: MXParser): String? {
  var eventType = parser.eventType

  fun findName(): String? {
    eventType = parser.next()
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          if (parser.name == "option" && parser.getAttributeValue(null, "name") == "myName") {
            return parser.getAttributeValue(null, "value")
          }
        }
      }

      eventType = parser.next()
    }
    return null
  }

  do {
    when (eventType) {
      XmlPullParser.START_TAG -> {
        if (!isOldSchemeNaming || parser.name != "component") {
          if (parser.name == "profile" || (isOldSchemeNaming && parser.name == "copyright")) {
            return findName()
          }
          else if (parser.name == "inspections") {
            // backward compatibility - we don't write PROFILE_NAME_TAG anymore
            return parser.getAttributeValue(null, "profile_name") ?: findName()
          }
          else if (parser.name == "configuration") {
            // run configuration
            return parser.getAttributeValue(null, "name")
          }
          else {
            return null
          }
        }
      }
    }
    eventType = parser.next()
  }
  while (eventType != XmlPullParser.END_DOCUMENT)
  return null
}

internal class ExternalInfo(var fileNameWithoutExtension: String, var fileExtension: String?) {
  // we keep it to detect rename
  var schemeName: String? = null

  var digest: ByteArray? = null

  val fileName: String
    get() = "$fileNameWithoutExtension$fileExtension"

  fun setFileNameWithoutExtension(nameWithoutExtension: String, extension: String) {
    fileNameWithoutExtension = nameWithoutExtension
    fileExtension = extension
  }

  fun isDigestEquals(newDigest: ByteArray) = Arrays.equals(digest, newDigest)

  override fun toString() = fileName
}

internal fun VirtualFile.getOrCreateChild(fileName: String, requestor: Any): VirtualFile {
  return findChild(fileName) ?: runUndoTransparentWriteAction { createChildData(requestor, fileName) }
}

internal fun createDir(ioDir: Path, requestor: Any): VirtualFile {
  ioDir.createDirectories()
  val parentFile = ioDir.parent
  val parentVirtualFile = (if (parentFile == null) null else VfsUtil.createDirectoryIfMissing(parentFile.systemIndependentPath))
      ?: throw IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile))
  return parentVirtualFile.getOrCreateChild(ioDir.fileName.toString(), requestor)
}