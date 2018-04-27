// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.kdbx

import com.intellij.util.get
import com.intellij.util.getOrCreate
import org.jdom.Element
import org.jdom.xpath.XPath
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

internal const val LOCATION_CHANGED = "Times/LocationChanged"
internal const val USAGE_COUNT_ELEMENT_NAME = "Times/UsageCount"
internal const val EXPIRES_ELEMENT_NAME = "Times/Expires"
internal const val GROUP_ELEMENT_NAME = "Group"
internal const val ENTRY_ELEMENT_NAME = "Entry"
internal const val ICON_ELEMENT_NAME = "IconID"
internal const val UUID_ELEMENT_NAME = "UUID"
internal const val NAME_ELEMENT_NAME = "Name"
internal const val LAST_MODIFICATION_TIME_ELEMENT_NAME = "Times/LastModificationTime"
internal const val CREATION_TIME_ELEMENT_NAME = "Times/CreationTime"
internal const val LAST_ACCESS_TIME_ELEMENT_NAME = "Times/LastAccessTime"
internal const val EXPIRY_TIME_ELEMENT_NAME = "Times/ExpiryTime"

private const val ROOT_ELEMENT_NAME = "Root"

internal var dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

class KeePassDatabase(private val rootElement: Element = createEmptyDatabase()) {
  private val dbMeta: Element
    get() = rootElement.getChild("Meta") ?: throw IllegalStateException("no meta")

  @Volatile var isDirty: Boolean = false
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

  fun setDescription(description: String) {
    dbMeta.getOrCreate("DatabaseDescription").text = description
    dbMeta.getOrCreate("DatabaseDescriptionChanged").text = formattedNow()
    isDirty = true
  }
}

interface Icon {
  var index: Int
}

private val mandatoryEntryElements: Map<String, ValueCreator> = linkedMapOf (
    UUID_ELEMENT_NAME to UuidValueCreator(),
    ICON_ELEMENT_NAME to ConstantValueCreator("0"),
    CREATION_TIME_ELEMENT_NAME to DateValueCreator(),
    LAST_MODIFICATION_TIME_ELEMENT_NAME to DateValueCreator(),
    LAST_ACCESS_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRY_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRES_ELEMENT_NAME to  ConstantValueCreator("False"),
    USAGE_COUNT_ELEMENT_NAME to ConstantValueCreator("0"),
    LOCATION_CHANGED to DateValueCreator()
)

internal fun ensureElements(element: Element, childElements: Map<String, ValueCreator>) {
  for ((elementPath, value) in childElements) {
    var result = XPath.newInstance(elementPath).selectSingleNode(element)
    if (result == null) {
      result = createHierarchically(elementPath, element)
      result.text = value.value
    }
  }
}

private fun createHierarchically(elementPath: String, startElement: Element): Element {
  var currentElement = startElement
  for (elementName in elementPath.split('/')) {
    currentElement = currentElement.getOrCreate(elementName)
  }
  return currentElement
}

internal fun formattedNow() = LocalDateTime.now(ZoneOffset.UTC).format(dateFormatter)

interface ValueCreator {
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