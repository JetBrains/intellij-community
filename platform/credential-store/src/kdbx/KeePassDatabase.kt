// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.kdbx

import com.google.common.io.LittleEndianDataOutputStream
import com.intellij.util.getOrCreate
import com.intellij.util.loadElement
import org.jdom.Element
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.GZIPOutputStream

internal object KdbxDbElementNames {
  const val group = "Group"
  const val entry = "Entry"

  const val root = "Root"

  const val name = "Name"
}

internal val LOCATION_CHANGED = arrayOf("Times", "LocationChanged")
internal val USAGE_COUNT_ELEMENT_NAME = arrayOf("Times", "UsageCount")
internal val EXPIRES_ELEMENT_NAME = arrayOf("Times", "Expires")
internal val ICON_ELEMENT_NAME = arrayOf("IconID")
internal val UUID_ELEMENT_NAME = arrayOf("UUID")
internal val LAST_MODIFICATION_TIME_ELEMENT_NAME = arrayOf("Times", "LastModificationTime")
internal val CREATION_TIME_ELEMENT_NAME = arrayOf("Times", "CreationTime")
internal val LAST_ACCESS_TIME_ELEMENT_NAME = arrayOf("Times", "LastAccessTime")
internal val EXPIRY_TIME_ELEMENT_NAME = arrayOf("Times", "ExpiryTime")

internal var dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

// we should on each save change protectedStreamKey for security reasons (as KeeWeb also does)
// so, this requirement (is it really required?) can force us to re-encrypt all passwords on save
internal class KeePassDatabase(private val rootElement: Element = createEmptyDatabase()) {
  @Volatile
  var isDirty: Boolean = false
    internal set

  val rootGroup: KdbxGroup

  init {
    val rootElement = rootElement.getOrCreate(KdbxDbElementNames.root)
    val groupElement = rootElement.getChild(KdbxDbElementNames.group)
    if (groupElement == null) {
      rootGroup = createGroup(this, null)
      rootGroup.name = KdbxDbElementNames.root
      rootElement.addContent(rootGroup.element)
    }
    else {
      rootGroup = KdbxGroup(groupElement, this, null)
    }
  }

  @Synchronized
  fun save(credentials: KeePassCredentials, outputStream: OutputStream) {
    val kdbxHeader = KdbxHeader()
    kdbxHeader.writeKdbxHeader(outputStream)

    val metaElement = rootElement.getOrCreate("Meta")
    metaElement.getOrCreate("HeaderHash").text = Base64.getEncoder().encodeToString(kdbxHeader.headerHash)
    metaElement.getOrCreate("MemoryProtection").getOrCreate("ProtectPassword").text = "True"

    val encryptedOutputStream = kdbxHeader.createEncryptedStream(credentials.key, outputStream)
    LittleEndianDataOutputStream(encryptedOutputStream).write(kdbxHeader.streamStartBytes)

    var kdbxOutput: OutputStream = HashedBlockOutputStream(encryptedOutputStream)
    if (kdbxHeader.compressionFlags == KdbxHeader.CompressionFlags.GZIP) {
      kdbxOutput = GZIPOutputStream(kdbxOutput, 8 * 1024)
    }

    kdbxOutput.writer().use {
      ProtectedXmlWriter(createSalsa20StreamCipher(kdbxHeader.protectedStreamKey)).printElement(it, rootElement, 0)
    }

    isDirty = false
  }

  fun createEntry(title: String): KdbxEntry {
    val element = Element(KdbxDbElementNames.entry)
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
    get() = base64FromUuid(UUID.randomUUID())
}

private fun base64FromUuid(uuid: UUID): String {
  val b = ByteBuffer.wrap(ByteArray(16))
  b.putLong(uuid.mostSignificantBits)
  b.putLong(uuid.leastSignificantBits)
  return Base64.getEncoder().encodeToString(b.array())
}

private fun createEmptyDatabase(): Element {
  val creationDate = formattedNow()
  @Suppress("SpellCheckingInspection")
  return loadElement("""<KeePassFile>
    <Meta>
      <Generator>IJ</Generator>
      <HeaderHash></HeaderHash>
      <DatabaseName>New Database</DatabaseName>
      <DatabaseNameChanged>${creationDate}</DatabaseNameChanged>
      <DatabaseDescription>Empty Database</DatabaseDescription>
      <DatabaseDescriptionChanged>${creationDate}</DatabaseDescriptionChanged>
      <DefaultUserName/>
      <DefaultUserNameChanged>${creationDate}</DefaultUserNameChanged>
      <MaintenanceHistoryDays>365</MaintenanceHistoryDays>
      <Color/>
      <MasterKeyChanged>${creationDate}</MasterKeyChanged>
      <MasterKeyChangeRec>-1</MasterKeyChangeRec>
      <MasterKeyChangeForce>-1</MasterKeyChangeForce>
      <MemoryProtection>
          <ProtectTitle>False</ProtectTitle>
          <ProtectUserName>False</ProtectUserName>
          <ProtectPassword>True</ProtectPassword>
          <ProtectURL>False</ProtectURL>
          <ProtectNotes>False</ProtectNotes>
      </MemoryProtection>
      <CustomIcons/>
      <RecycleBinEnabled>True</RecycleBinEnabled>
      <RecycleBinUUID>AAAAAAAAAAAAAAAAAAAAAA==</RecycleBinUUID>
      <RecycleBinChanged>${creationDate}</RecycleBinChanged>
      <EntryTemplatesGroup>AAAAAAAAAAAAAAAAAAAAAA==</EntryTemplatesGroup>
      <EntryTemplatesGroupChanged>${creationDate}</EntryTemplatesGroupChanged>
      <LastSelectedGroup>AAAAAAAAAAAAAAAAAAAAAA==</LastSelectedGroup>
      <LastTopVisibleGroup>AAAAAAAAAAAAAAAAAAAAAA==</LastTopVisibleGroup>
      <HistoryMaxItems>10</HistoryMaxItems>
      <HistoryMaxSize>6291456</HistoryMaxSize>
      <Binaries/>
      <CustomData/>
    </Meta>
  </KeePassFile>""")
}