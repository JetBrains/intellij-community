package com.intellij.credentialStore.kdbx

import org.linguafranca.pwdb.kdbx.dom.DomHelper
import org.linguafranca.pwdb.kdbx.dom.DomSerializableDatabase
import org.w3c.dom.Element
import java.io.InputStream
import java.io.OutputStream
import javax.xml.xpath.XPathConstants

class KeePassDatabase() {
  internal val dbMeta: Element
    get() {
      val list = domDatabase.doc.documentElement.getElementsByTagName("Meta")
      return if (list.length > 0) list.item(0) as Element else throw IllegalStateException("no meta")
    }

  private val domDatabase = DomSerializableDatabase.createEmptyDatabase()

  @Volatile var isDirty: Boolean = false
    internal set

  fun load(streamFormat: StreamFormat, credentials: KeePassCredentials, inputStream: InputStream) {
    streamFormat.load(domDatabase, credentials, inputStream)
  }

  var name: String?
    get() = DomHelper.getElementContent("DatabaseName", dbMeta)
    set(name) {
      DomHelper.setElementContent("DatabaseName", dbMeta, name)
      DomHelper.touchElement("DatabaseNameChanged", dbMeta)
      isDirty = true
    }

  fun save(credentials: KeePassCredentials, outputStream: OutputStream) {
    KdbxStreamFormat().save(domDatabase, credentials, outputStream)
    isDirty = false
  }

  fun save(streamFormat: StreamFormat, credentials: KeePassCredentials, outputStream: OutputStream) {
    streamFormat.save(domDatabase, credentials, outputStream)
    isDirty = false
  }

//  fun shouldProtect(name: String): Boolean {
//    val protectionElement = DomHelper.getElement("MemoryProtection/Protect$name", dbMeta, false) ?: return false
//    return protectionElement.textContent.toBoolean()
//  }

  val rootGroup: KdbxGroup
    get() = KdbxGroup(DomHelper.xpath.evaluate("/KeePassFile/Root/Group", domDatabase.doc, XPathConstants.NODE) as Element, this, false)

  fun newGroup(name: String): KdbxGroup {
    val result = KdbxGroup(domDatabase.doc.createElement(DomHelper.GROUP_ELEMENT_NAME), this, true)
    result.name = name
    return result
  }

  fun newEntry() = KdbxEntry(domDatabase.doc.createElement(DomHelper.ENTRY_ELEMENT_NAME), this, true)

  fun newEntry(title: String): KdbxEntry {
    val result = newEntry()
    result.title = title
    return result
  }

//  fun newIcon() = DomIconWrapper(domDatabase.doc.createElement(DomHelper.ICON_ELEMENT_NAME))

//  fun newIcon(i: Int): Icon {
//    val icon = newIcon()
//    icon.index = i
//    return icon
//  }

  fun getDescription() = DomHelper.getElementContent("DatabaseDescription", dbMeta)

  fun setDescription(description: String) {
    DomHelper.setElementContent("DatabaseDescription", dbMeta, description)
    DomHelper.touchElement("DatabaseDescriptionChanged", dbMeta)
    isDirty = true
  }
}

interface Icon {
  var index: Int
}

class DomIconWrapper(private val element: Element) : Icon {
  override var index: Int
    get() = Integer.parseInt(element.textContent)
    set(index) {
      element.textContent = index.toString()
    }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || other !is Icon) return false
    return this.index == other.index
  }

  override fun hashCode() = index
}