package com.intellij.credentialStore.kdbx

import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Stack
import com.intellij.util.get
import com.intellij.util.getOrCreate
import org.jdom.Element
import org.jdom.filter.ElementFilter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class KdbxGroup(private val element: Element, private val database: KeePassDatabase, private var parent: KdbxGroup?) {
  @Volatile var name = element.getChildText(NAME_ELEMENT_NAME) ?: "Unnamed"
    set(value) {
      if (field != value) {
        field = value
        database.isDirty = true
      }
    }

  private val subGroups: MutableList<KdbxGroup>

  private @Volatile var locationChanged = element.get("Times")?.get("LocationChanged")?.text?.let(::parseTime) ?: 0

  init {
    locationChanged = element.get("Times")?.get("LocationChanged")?.text?.let(::parseTime) ?: 0

    val groups = SmartList<KdbxGroup>()
    val groupIterator = element.getContent(ElementFilter(GROUP_ELEMENT_NAME)).iterator()
    while (groupIterator.hasNext()) {
      val child = groupIterator.next()
      groups.add(KdbxGroup(child, database, this))
      groupIterator.remove()
    }
    subGroups = ContainerUtil.createLockFreeCopyOnWriteList<KdbxGroup>(groups)
  }

  fun toXml(): Element {
    val element = element.clone()
    element.getOrCreate(NAME_ELEMENT_NAME).text = name

    val locationChangedElement = element.getOrCreate("Times").getOrCreate("LocationChanged")
    if (locationChanged == 0L) {
      element.get("Times")?.get("CreationTime")?.text?.let {
        locationChangedElement.text = it
      }
    }
    else {
      locationChangedElement.text = Instant.ofEpochMilli(locationChanged).atZone(ZoneOffset.UTC).format(dateFormatter)
    }

    for (group in subGroups) {
      element.addContent(group.toXml())
    }
    return element
  }

  fun addGroup(group: KdbxGroup): KdbxGroup {
    if (group == database.rootGroup) {
      throw IllegalStateException("Cannot set root group as child of another group")
    }

    group.parent?.removeGroup(group)
    subGroups.add(group)
    group.locationChanged = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC)

    database.isDirty = true
    return group
  }

  fun removeGroup(group: KdbxGroup): KdbxGroup {
    if (subGroups.remove(group)) {
      database.isDirty = true
    }
    return group
  }

  fun removeGroup(name: String) {
    getGroup(name)?.let { removeGroup(it) }
  }

  fun getGroup(name: String) = subGroups.firstOrNull { it.name == name }

  fun getOrCreateGroup(name: String) = getGroup(name) ?: createGroup(name)

  fun createGroup(name: String): KdbxGroup {
    val result = createGroup(database, this)
    result.name = name
    addGroup(result)
    return result
  }

  val entries: List<KdbxEntry>
    get() = element.getChildren(ENTRY_ELEMENT_NAME).map { KdbxEntry(it, database) }

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

internal fun createGroup(db: KeePassDatabase, parent: KdbxGroup?): KdbxGroup {
  val element = Element(GROUP_ELEMENT_NAME)
  ensureElements(element, mandatoryGroupElements)
  val result = KdbxGroup(element, db, parent)
  return result
}

private const val NOTES_ELEMENT_NAME = "Notes"

private val mandatoryGroupElements: Map<String, ValueCreator> = linkedMapOf (
    UUID_ELEMENT_NAME to UuidValueCreator(),
    NOTES_ELEMENT_NAME to ConstantValueCreator(""),
    ICON_ELEMENT_NAME to ConstantValueCreator("0"),
    CREATION_TIME_ELEMENT_NAME to DateValueCreator(),
    LAST_MODIFICATION_TIME_ELEMENT_NAME to DateValueCreator(),
    LAST_ACCESS_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRY_TIME_ELEMENT_NAME to DateValueCreator(),
    EXPIRES_ELEMENT_NAME to ConstantValueCreator("False"),
    USAGE_COUNT_ELEMENT_NAME to ConstantValueCreator("0"),
    LOCATION_CHANGED to DateValueCreator()
)