package com.intellij.credentialStore.kdbx

import com.intellij.util.containers.Stack
import org.linguafranca.pwdb.kdbx.dom.DomHelper.*
import org.w3c.dom.Element
import java.util.*

private val mandatoryGroupElements: Map<String, ValueCreator> = mapOf (
    UUID_ELEMENT_NAME to UuidValueCreator(),
    NAME_ELEMENT_NAME to ConstantValueCreator(""),
    NOTES_ELEMENT_NAME to ConstantValueCreator(""),
    ICON_ELEMENT_NAME to ConstantValueCreator("2"),
    TIMES_ELEMENT_NAME to ConstantValueCreator(""),
    LAST_MODIFICATION_TIME_ELEMENT_NAME to DateValueCreator(),
    CREATION_TIME_ELEMENT_NAME to DateValueCreator(),
    LAST_ACCESS_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRY_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRES_ELEMENT_NAME to ConstantValueCreator("False"),
    USAGE_COUNT_ELEMENT_NAME to ConstantValueCreator("0"),
    LOCATION_CHANGED to DateValueCreator()
)

class KdbxGroup(private val element: Element, private val database: KeePassDatabase, isNewGroup: Boolean) {
  init {
    if (isNewGroup) {
      ensureElements(element, mandatoryGroupElements)
    }
  }

  val isRootGroup: Boolean
    get() = parent != null && (element.parentNode as Element?)?.tagName == "Root"

  var name: String
    get() = getElementContent(NAME_ELEMENT_NAME, element)!!
    set(value) {
      setElementContent(NAME_ELEMENT_NAME, element, value)
      database.isDirty = true
    }

  val uuid: UUID
    get() = uuidFromBase64(getElementContent(UUID_ELEMENT_NAME, element)!!)

  @Suppress("ConvertLambdaToReference")
  var icon: Icon?
    get() = getElement(ICON_ELEMENT_NAME, element, false)?.let { DomIconWrapper(it) }
    set(value) {
      setElementContent(ICON_ELEMENT_NAME, element, icon!!.index.toString())
      database.isDirty = true
    }

  val parent: KdbxGroup?
    get() {
      val parent = element.parentNode as Element? ?: return null
      // if the element is the root group there is no parent
      if (element === element.ownerDocument.documentElement.getElementsByTagName(GROUP_ELEMENT_NAME).item(0)) {
        return null
      }
      return KdbxGroup(parent, database, false)
    }

  fun addGroup(group: KdbxGroup): KdbxGroup {
    if (group.isRootGroup) {
      throw IllegalStateException("Cannot set root group as child of another group")
    }

    // skip if this is a new group with no parent
    group.parent?.removeGroup(group)
    element.appendChild(group.element)
    touchElement("Times/LocationChanged", group.element)
    database.isDirty = true
    return group
  }

  fun removeGroup(group: KdbxGroup): KdbxGroup {
    element.removeChild(group.element)
    database.isDirty = true
    return group
  }

  fun removeGroup(name: String) {
    getGroup(name)?.let { removeGroup(it) }
  }

  fun getGroup(name: String) = getGroup { it.name == name }

  fun getOrCreateGroup(name: String) = getGroup(name) ?: createGroup(name)

  fun createGroup(name: String) = addGroup(database.newGroup(name))

  val entries: List<Entry>
    get() = getElements(ENTRY_ELEMENT_NAME, this.element).map { KdbxEntry(it, database, false) }

  private inline fun getGroup(matcher: (KdbxGroup) -> Boolean): KdbxGroup? {
    for (groupElement in getElements(GROUP_ELEMENT_NAME, this.element)) {
      val item = KdbxGroup(groupElement, database, false)
      if (matcher(item)) {
        return item
      }
    }
    return null
  }

  fun getEntry(matcher: (entry: Entry) -> Boolean): Entry? {
    for (entryElement in getElements(ENTRY_ELEMENT_NAME, element)) {
      val entry = KdbxEntry(entryElement, database, false)
      if (matcher(entry)) {
        return entry
      }
    }

    return null
  }

  fun addEntry(entry: KdbxEntry): KdbxEntry {
    if (entry.parent != null) {
      entry.element.parentNode.removeChild(element)
    }
    element.appendChild(entry.element)
    database.isDirty = true
    return entry
  }

  fun removeEntry(entry: Entry): Entry {
    element.removeChild((entry as KdbxEntry).element)
    database.isDirty = true
    return entry
  }

  fun getEntry(title: String, userName: String?) = getEntry { it.title == title && (it.userName == userName || userName == null) }

  fun getOrCreateEntry(title: String, userName: String?): Entry {
    var entry = getEntry(title, userName)
    if (entry == null) {
      entry = database.newEntry()
      entry.title = title
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