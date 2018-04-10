/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.credentialStore.kdbx

import com.intellij.util.SmartList
import com.intellij.util.io.inputStreamIfExists
import com.intellij.util.loadElement
import com.intellij.util.write
import org.bouncycastle.crypto.engines.Salsa20Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.jdom.Element
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.util.*

internal fun loadKdbx(file: Path, credentials: KeePassCredentials) = file.inputStreamIfExists()?.use {
  KeePassDatabase(KdbxStreamFormat().load(credentials, it))
}

class KdbxPassword(password: ByteArray) : KeePassCredentials {
  override val key: ByteArray

  init {
    val md = MessageDigest.getInstance("SHA-256")
    key = md.digest(md.digest(password))
  }
}

interface KeePassCredentials {
  val key: ByteArray
}

class KdbxStreamFormat {
  fun load(credentials: KeePassCredentials, inputStream: InputStream): Element {
    val kdbxHeader = KdbxHeader()
    KdbxSerializer.createUnencryptedInputStream(credentials, kdbxHeader, inputStream).use {
      val encryption = Salsa20Encryption(kdbxHeader.protectedStreamKey)
      return load(it, encryption)
    }
  }
}

internal fun save(rootElement: Element, outputStream: OutputStream, encryption: KdbxEncryption) {
  val meta = rootElement.getChild("Meta")?.getChild("MemoryProtection")
  if (meta != null) {
    val propertiesToProtect = SmartList<String>()
    for (element in meta.children) {
      val propertyName = element.name.removePrefix("Protect")
      if (propertyName != element.name && element.text.equals("true", ignoreCase = true)) {
        propertiesToProtect.add(propertyName)
      }
    }

    rootElement.getChild("Root")?.getChild("Group")?.let { rootGroupElement ->
      processEntries(rootGroupElement) { container, valueElement ->
        val key = container.getChildText("Key") ?: return@processEntries
        for (propertyName in propertiesToProtect) {
          if (key == propertyName) {
            valueElement.setAttribute("Protected", "True")
            valueElement.text = Base64.getEncoder().encodeToString(encryption.encrypt(valueElement.text.toByteArray()))
          }
        }
      }
    }
  }
  rootElement.write(outputStream)
}

private fun load(inputStream: InputStream, encryption: KdbxEncryption): Element {
  val rootElement = loadElement(inputStream)
  rootElement.getChild("Root")?.getChild("Group")?.let { rootGroupElement ->
    processEntries(rootGroupElement) { _, valueElement ->
      if (valueElement.getAttributeValue("Protected", "false").equals("true", ignoreCase = true)) {
        valueElement.text = encryption.decrypt(Base64.getDecoder().decode(valueElement.text)).toString(Charsets.UTF_8)
        valueElement.removeAttribute("Protected")
      }
    }
  }
  return rootElement
}

private fun processEntries(groupElement: Element, processor: (container: Element, valueElement: Element) -> Unit) {
  // we must process in exact order
  for (element in groupElement.children) {
    if (element.name == GROUP_ELEMENT_NAME) {
      processEntries(element, processor)
    }
    else if (element.name == ENTRY_ELEMENT_NAME) {
      for (container in element.getChildren("String")) {
        val valueElement = container.getChild("Value") ?: continue
        processor(container, valueElement)
      }
    }
  }
}

internal fun createEmptyDatabase(): Element {
  val creationDate = formattedNow()
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

internal interface KdbxEncryption {
  val key: ByteArray

  fun decrypt(encryptedText: ByteArray): ByteArray

  fun encrypt(decryptedText: ByteArray): ByteArray
}

private val SALSA20_IV = byteArrayOf(-24, 48, 9, 75, -105, 32, 93, 42)  // 0xE830094B97205D2A

/**
 * Salsa20 doesn't quite fit the KeePass memory model - all encrypted items have to be en/decrypted in order of encryption,
 * i.e. in document order and at the same time.
 */
internal class Salsa20Encryption(override val key: ByteArray) : KdbxEncryption {
  private val salsa20 = Salsa20Engine()

  init {
    val keyParameter = KeyParameter(sha256MessageDigest().digest(key))
    salsa20.init(true, ParametersWithIV(keyParameter, SALSA20_IV))
  }

  override fun decrypt(encryptedText: ByteArray): ByteArray {
    val output = ByteArray(encryptedText.size)
    salsa20.processBytes(encryptedText, 0, encryptedText.size, output, 0)
    return output
  }

  override fun encrypt(decryptedText: ByteArray): ByteArray {
    val output = ByteArray(decryptedText.size)
    salsa20.processBytes(decryptedText, 0, decryptedText.size, output, 0)
    return output
  }
}

internal fun parseTime(value: String): Long {
  return try {
    ZonedDateTime.parse(value).toEpochSecond()
  }
  catch (e: DateTimeParseException) {
    0
  }
}