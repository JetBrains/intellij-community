// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.kdbx

import com.intellij.util.get
import com.intellij.util.getOrCreate
import org.jdom.Element
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

internal const val ENTRY_ELEMENT_NAME = "Entry"
internal const val GROUP_ELEMENT_NAME = "Group"
internal const val NAME_ELEMENT_NAME = "Name"

internal val LOCATION_CHANGED = arrayOf("Times", "LocationChanged")
internal val USAGE_COUNT_ELEMENT_NAME = arrayOf("Times", "UsageCount")
internal val EXPIRES_ELEMENT_NAME = arrayOf("Times", "Expires")
internal val ICON_ELEMENT_NAME = arrayOf("IconID")
internal val UUID_ELEMENT_NAME = arrayOf("UUID")
internal val LAST_MODIFICATION_TIME_ELEMENT_NAME = arrayOf("Times", "LastModificationTime")
internal val CREATION_TIME_ELEMENT_NAME = arrayOf("Times", "CreationTime")
internal val LAST_ACCESS_TIME_ELEMENT_NAME = arrayOf("Times", "LastAccessTime")
internal val EXPIRY_TIME_ELEMENT_NAME = arrayOf("Times", "ExpiryTime")

private const val ROOT_ELEMENT_NAME = "Root"

internal var dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

internal class KeePassDatabase(private val rootElement: Element = createEmptyDatabase()) {
  @Volatile
  var isDirty: Boolean = false
    internal set

  val rootGroup: KdbxGroup

  init {
    val rootElement = rootElement.get(ROOT_ELEMENT_NAME)
    val groupElement = rootElement?.get("Group")
    if (groupElement == null) {
      rootGroup = createGroup(this, null)
      rootGroup.name = ROOT_ELEMENT_NAME
    }
    else {
      rootElement.removeChild("Group")
      rootGroup = KdbxGroup(groupElement, this, null)
    }
  }

  fun save(credentials: KeePassCredentials, outputStream: OutputStream) {
    val element = rootElement.clone()
    element.getOrCreate(ROOT_ELEMENT_NAME).addContent(rootGroup.toXml())
    val kdbxHeader = KdbxHeader()
    KdbxSerializer.createEncryptedOutputStream(credentials, kdbxHeader, outputStream).use {
      element.getOrCreate("HeaderHash").text = Base64.getEncoder().encodeToString(kdbxHeader.headerHash)
      save(element, it, Salsa20Encryption(kdbxHeader.protectedStreamKey))
    }
    isDirty = false
  }

  fun createEntry(title: String): KdbxEntry {
    val element = Element(ENTRY_ELEMENT_NAME)
    ensureElements(element, mandatoryEntryElements)

    val result = KdbxEntry(element, this, null)
    result.title = title
    return result
  }
}

private val mandatoryEntryElements: Map<Array<String>, ValueCreator> = linkedMapOf(
  UUID_ELEMENT_NAME to UuidValueCreator(),
  ICON_ELEMENT_NAME to ConstantValueCreator("0"),
  CREATION_TIME_ELEMENT_NAME to DateValueCreator(),
  LAST_MODIFICATION_TIME_ELEMENT_NAME to DateValueCreator(),
  LAST_ACCESS_TIME_ELEMENT_NAME to DateValueCreator(),
  EXPIRY_TIME_ELEMENT_NAME to DateValueCreator(),
  EXPIRES_ELEMENT_NAME to ConstantValueCreator("False"),
  USAGE_COUNT_ELEMENT_NAME to ConstantValueCreator("0"),
  LOCATION_CHANGED to DateValueCreator()
)

internal fun ensureElements(element: Element, childElements: Map<Array<String>, ValueCreator>) {
  for ((elementPath, value) in childElements) {
    val result = findElement(element, elementPath)
    if (result == null) {
      var currentElement = element
      for (elementName in elementPath) {
        currentElement = currentElement.getOrCreate(elementName)
      }
      currentElement.text = value.value
    }
  }
}

private fun findElement(element: Element, elementPath: Array<String>): Element? {
  var result = element
  for (elementName in elementPath) {
    result = result.getChild(elementName) ?: return null
  }
  return result
}

internal fun formattedNow() = LocalDateTime.now(ZoneOffset.UTC).format(dateFormatter)

internal interface ValueCreator {
  val value: String
}

internal class ConstantValueCreator(override val value: String) : ValueCreator

internal class DateValueCreator : ValueCreator {
  override val value: String
    get() = formattedNow()
}

internal class UuidValueCreator : ValueCreator {
  override val value: String
    get() = base64RandomUuid()
}

internal fun base64RandomUuid() = base64FromUuid(UUID.randomUUID())

private fun base64FromUuid(uuid: UUID): String {
  val b = ByteBuffer.wrap(ByteArray(16))
  b.putLong(uuid.mostSignificantBits)
  b.putLong(uuid.leastSignificantBits)
  return Base64.getEncoder().encodeToString(b.array())
}