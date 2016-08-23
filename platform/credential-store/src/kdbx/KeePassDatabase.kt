package com.intellij.credentialStore.kdbx

import com.intellij.util.getOrCreate
import org.jdom.Element
import org.jdom.xpath.XPath
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private const val LOCATION_CHANGED = "Times/LocationChanged"
private const val USAGE_COUNT_ELEMENT_NAME = "Times/UsageCount"
private const val EXPIRES_ELEMENT_NAME = "Times/Expires"
private const val TIMES_ELEMENT_NAME = "Times"
internal const val GROUP_ELEMENT_NAME = "Group"
internal const val ENTRY_ELEMENT_NAME = "Entry"
internal const val ICON_ELEMENT_NAME = "IconID"
internal const val UUID_ELEMENT_NAME = "UUID"
internal const val NAME_ELEMENT_NAME = "Name"
private const val NOTES_ELEMENT_NAME = "Notes"
internal const val LAST_MODIFICATION_TIME_ELEMENT_NAME = "Times/LastModificationTime"
internal const val CREATION_TIME_ELEMENT_NAME = "Times/CreationTime"
internal const val LAST_ACCESS_TIME_ELEMENT_NAME = "Times/LastAccessTime"
internal const val EXPIRY_TIME_ELEMENT_NAME = "Times/ExpiryTime"

internal var dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

class KeePassDatabase(private val rootElement: Element = createEmptyDatabase()) {
  internal val dbMeta: Element
    get() = rootElement.getChild("Meta") ?: throw IllegalStateException("no meta")

  @Volatile var isDirty: Boolean = false
    internal set

  var name: String?
    get() = dbMeta.getChildText("DatabaseName")
    set(name) {
      dbMeta.getOrCreate("DatabaseName").text = name
      dbMeta.getOrCreate("DatabaseNameChanged").text = formattedNow()
      isDirty = true
    }

  fun save(credentials: KeePassCredentials, outputStream: OutputStream) {
    KdbxStreamFormat().save(rootElement, credentials, outputStream)
    isDirty = false
  }

  fun save(streamFormat: KdbxStreamFormat, credentials: KeePassCredentials, outputStream: OutputStream) {
    streamFormat.save(rootElement, credentials, outputStream)
    isDirty = false
  }

//  fun shouldProtect(name: String): Boolean {
//    val protectionElement = DomHelper.getElement("MemoryProtection/Protect$name", dbMeta, false) ?: return false
//    return protectionElement.textContent.toBoolean()
//  }

  val rootGroup: KdbxGroup
    get() = KdbxGroup(rootElement.getChild("Root").getChild("Group"), this)

  fun createGroup(name: String): KdbxGroup {
    val element = Element(GROUP_ELEMENT_NAME)
    ensureElements(element, mandatoryGroupElements)
    val result = KdbxGroup(element, this)
    result.name = name
    return result
  }

  fun createEntry(title: String): KdbxEntry {
    val element = Element(ENTRY_ELEMENT_NAME)
    ensureElements(element, mandatoryEntryElements)

    val result = KdbxEntry(element, this)
    result.title = title
    result.ensureProperty("Notes")
    result.ensureProperty("Title")
    result.ensureProperty("URL")
    result.ensureProperty("UserName")
    result.ensureProperty("Password")
    return result
  }

  fun getDescription() = dbMeta.getChildText("DatabaseDescription")

  fun setDescription(description: String) {
    dbMeta.getOrCreate("DatabaseDescription").text = description
    dbMeta.getOrCreate("DatabaseDescriptionChanged").text = formattedNow()
    isDirty = true
  }
}

interface Icon {
  var index: Int
}

class DomIconWrapper(private val element: Element) : Icon {
  override var index: Int
    get() = element.text.toInt()
    set(index) {
      element.text = index.toString()
    }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || other !is Icon) return false
    return this.index == other.index
  }

  override fun hashCode() = index
}

private val mandatoryEntryElements: Map<String, ValueCreator> = mapOf (
    UUID_ELEMENT_NAME to UuidValueCreator(),
    ICON_ELEMENT_NAME to ConstantValueCreator("0"),
    TIMES_ELEMENT_NAME to ConstantValueCreator(""),
    LAST_MODIFICATION_TIME_ELEMENT_NAME to DateValueCreator(),
    CREATION_TIME_ELEMENT_NAME to DateValueCreator(),
    LAST_ACCESS_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRY_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRES_ELEMENT_NAME to  ConstantValueCreator("False"),
    USAGE_COUNT_ELEMENT_NAME to ConstantValueCreator("0"),
    LOCATION_CHANGED to DateValueCreator()
)

private val mandatoryGroupElements: Map<String, ValueCreator> = mapOf (
    UUID_ELEMENT_NAME to UuidValueCreator(),
    NAME_ELEMENT_NAME to ConstantValueCreator(""),
    NOTES_ELEMENT_NAME to ConstantValueCreator(""),
    ICON_ELEMENT_NAME to ConstantValueCreator("0"),
    TIMES_ELEMENT_NAME to ConstantValueCreator(""),
    LAST_MODIFICATION_TIME_ELEMENT_NAME to DateValueCreator(),
    CREATION_TIME_ELEMENT_NAME to DateValueCreator(),
    LAST_ACCESS_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRY_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRES_ELEMENT_NAME to ConstantValueCreator("False"),
    USAGE_COUNT_ELEMENT_NAME to ConstantValueCreator("0"),
    LOCATION_CHANGED to DateValueCreator()
)

private fun ensureElements(element: Element, childElements: Map<String, ValueCreator>) {
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

internal fun formattedNow() = LocalDateTime.now().format(dateFormatter)

interface ValueCreator {
  val value: String
}

internal class ConstantValueCreator(override val value: String) : ValueCreator {
}

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

internal fun uuidFromBase64(base64: String): UUID {
  val b = ByteBuffer.wrap(Base64.getDecoder().decode(base64))
  return UUID(b.long, b.long)
}