package com.intellij.credentialStore.kdbx

import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Stack
import com.intellij.util.get
import com.intellij.util.getOrCreate
import com.intellij.util.remove
import org.jdom.Element
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class KdbxGroup(private val element: Element, private val database: KeePassDatabase, private @Volatile var parent: KdbxGroup?) {
  @Volatile var name: String = element.getChildText(NAME_ELEMENT_NAME) ?: "Unnamed"
    set(value) {
      if (field != value) {
        field = value
        database.isDirty = true
      }
    }

  private val groups: MutableList<KdbxGroup>
  val entries: MutableList<KdbxEntry>

  private @Volatile var locationChanged = element.get("Times")?.get("LocationChanged")?.text?.let(::parseTime) ?: 0

  init {
    locationChanged = element.get("Times")?.get("LocationChanged")?.text?.let(::parseTime) ?: 0

    groups = ContainerUtil.createLockFreeCopyOnWriteList(element.remove(GROUP_ELEMENT_NAME) { KdbxGroup(it, database, this) })
    entries = ContainerUtil.createLockFreeCopyOnWriteList(element.remove(ENTRY_ELEMENT_NAME) { KdbxEntry(it, database, this) })
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

    for (group in groups) {
      element.addContent(group.toXml())
    }
    for (entry in entries) {
      element.addContent(entry.toXml())
    }
    return element
  }

  fun addGroup(group: KdbxGroup): KdbxGroup {
    if (group == database.rootGroup) {
      throw IllegalStateException("Cannot set root group as child of another group")
    }

    group.parent?.removeGroup(group)
    groups.add(group)
    group.parent = this
    group.locationChanged = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC)

    database.isDirty = true
    return group
  }

  fun removeGroup(group: KdbxGroup): KdbxGroup {
    if (groups.remove(group)) {
      group.parent = null
      database.isDirty = true
    }
    return group
  }

  fun removeGroup(name: String) {
    getGroup(name)?.let { removeGroup(it) }
  }

  fun getGroup(name: String): KdbxGroup? = groups.firstOrNull { it.name == name }

  fun getOrCreateGroup(name: String): KdbxGroup = getGroup(name) ?: createGroup(name)

  fun createGroup(name: String): KdbxGroup {
    val result = createGroup(database, this)
    result.name = name
    addGroup(result)
    return result
  }

  fun getEntry(matcher: (entry: KdbxEntry) -> Boolean): KdbxEntry? = entries.firstOrNull(matcher)

  fun addEntry(entry: KdbxEntry): KdbxEntry {
    entry.group?.let {
      it.removeEntry(entry)
    }
    entries.add(entry)
    entry.group = this
    database.isDirty = true
    return entry
  }

  fun removeEntry(entry: KdbxEntry): KdbxEntry {
    if (entries.remove(entry)) {
      entry.group = null
      database.isDirty = true
    }
    return entry
  }

  fun getEntry(title: String, userName: String?): KdbxEntry? = getEntry { it.title == title && (it.userName == userName || userName == null) }

  fun getOrCreateEntry(title: String, userName: String?): KdbxEntry {
    var entry = getEntry(title, userName)
    if (entry == null) {
      entry = database.createEntry(title)
      entry.userName = userName
      addEntry(entry)
    }
    return entry
  }

  fun removeEntry(title: String, userName: String?): KdbxEntry? = getEntry(title, userName)?.let { removeEntry(it) }

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

  override fun toString(): String = path
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