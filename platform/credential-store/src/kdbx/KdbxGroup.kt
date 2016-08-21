package com.intellij.credentialStore.kdbx

import com.intellij.util.containers.Stack
import com.intellij.util.getOrCreate
import org.jdom.Element
import java.util.*

class KdbxGroup(private val element: Element, private val database: KeePassDatabase) {
  val isRootGroup: Boolean
    get() = parent != null && element.parentElement?.name == "Root"

  var name: String
    get() = element.getChildText(NAME_ELEMENT_NAME)
    set(value) {
      element.getOrCreate(NAME_ELEMENT_NAME).text = value
      database.isDirty = true
    }

  val uuid: UUID
    get() = uuidFromBase64(element.getChildText(UUID_ELEMENT_NAME))

  @Suppress("ConvertLambdaToReference")
  var icon: Icon?
    get() = element.getChild(ICON_ELEMENT_NAME)?.let { DomIconWrapper(it) }
    set(value) {
      element.getOrCreate(ICON_ELEMENT_NAME).text = value!!.index.toString()
      database.isDirty = true
    }

  val parent: KdbxGroup?
    get() {
      val parent = element.parentElement ?: return null
      return if (isRootGroup) null else KdbxGroup(parent, database)
    }

  fun addGroup(group: KdbxGroup): KdbxGroup {
    if (group.isRootGroup) {
      throw IllegalStateException("Cannot set root group as child of another group")
    }

    // skip if this is a new group with no parent
    group.parent?.removeGroup(group)
    element.addContent(group.element)

    group.element.getOrCreate("Times").getOrCreate("LocationChanged").text = formattedNow()
    database.isDirty = true
    return group
  }

  fun removeGroup(group: KdbxGroup): KdbxGroup {
    element.removeContent(group.element)
    database.isDirty = true
    return group
  }

  fun removeGroup(name: String) {
    getGroup(name)?.let { removeGroup(it) }
  }

  fun getGroup(name: String) = getGroup { it.name == name }

  fun getOrCreateGroup(name: String) = getGroup(name) ?: createGroup(name)

  fun createGroup(name: String) = addGroup(database.createGroup(name))

  val entries: List<KdbxEntry>
    get() = element.getChildren(ENTRY_ELEMENT_NAME).map { KdbxEntry(it, database) }

  private inline fun getGroup(matcher: (KdbxGroup) -> Boolean): KdbxGroup? {
    for (groupElement in element.getChildren(GROUP_ELEMENT_NAME)) {
      val item = KdbxGroup(groupElement, database)
      if (matcher(item)) {
        return item
      }
    }
    return null
  }

  fun getEntry(matcher: (entry: KdbxEntry) -> Boolean): KdbxEntry? {
    for (entryElement in element.getChildren(ENTRY_ELEMENT_NAME)) {
      val entry = KdbxEntry(entryElement, database)
      if (matcher(entry)) {
        return entry
      }
    }

    return null
  }

  fun addEntry(entry: KdbxEntry): KdbxEntry {
    entry.element.parentElement?.let {
      it.removeContent(entry.element)
    }
    element.addContent(entry.element)
    database.isDirty = true
    return entry
  }

  fun removeEntry(entry: KdbxEntry): KdbxEntry {
    element.removeContent(entry.element)
    database.isDirty = true
    return entry
  }

  fun getEntry(title: String, userName: String?) = getEntry { it.title == title && (it.userName == userName || userName == null) }

  fun getOrCreateEntry(title: String, userName: String?): KdbxEntry {
    var entry = getEntry(title, userName)
    if (entry == null) {
      entry = database.createEntry(title)
      entry.userName = userName
      addEntry(entry)
    }
    return entry
  }

  fun removeEntry(title: String, userName: String?) = getEntry(title, userName)?.let { removeEntry(it) }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false

    val that = other as KdbxGroup?
    return element == that!!.element && database == that.database
  }

  val path: String
    get() {
      val parents = Stack<KdbxGroup>()
      var parent: KdbxGroup = this
      parents.push(this)
      while (true) {
        parent = parent.parent ?: break
        parents.push(parent)
      }
      val result = StringBuilder("/")
      while (parents.size > 0) {
        result.append(parents.pop().name).append('/')
      }
      return result.toString()
    }

  override fun toString() = this.path

  override fun hashCode(): Int {
    var result = element.hashCode()
    result = 31 * result + database.hashCode()
    return result
  }
}