// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.kdbx

import com.intellij.credentialStore.LOG
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.getOrCreate
import gnu.trove.THashMap
import org.jdom.Element
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

internal class KdbxGroup(internal val element: Element, private val database: KeePassDatabase, @Volatile private var parent: KdbxGroup?) {
  var name: String
    @Synchronized
    get() = element.getChildText(KdbxDbElementNames.name) ?: "Unnamed"
    @Synchronized
    set(value) {
      val nameElement = element.getOrCreate(KdbxDbElementNames.name)
      if (nameElement.text == value) {
        return
      }

      nameElement.text = value
      database.isDirty = true
    }

  private val groups: MutableMap<String, KdbxGroup> = THashMap()
  private val entries: MutableList<KdbxEntry> by lazy {
    ContainerUtil.createLockFreeCopyOnWriteList(element.getChildren(KdbxDbElementNames.entry).map { KdbxEntry(it, database, this) })
  }

  private var locationChanged: Long
    get() = element.getChild("Times")?.getChild("LocationChanged")?.text?.let(::parseTime) ?: 0
    set(value) {
      element.getOrCreate("Times").getOrCreate("LocationChanged").text = Instant.ofEpochMilli(value).atZone(ZoneOffset.UTC).format(dateFormatter)
    }

  @Synchronized
  fun getGroup(name: String): KdbxGroup? {
    var result = groups.get(name)
    if (result != null) {
      return result
    }

    val groupElement = element.content.firstOrNull { it is Element && it.getChildText(KdbxDbElementNames.name) == name } ?: return null
    result = KdbxGroup(groupElement as Element, database, this)
    groups.put(name, result)
    return result
  }

  @Synchronized
  private fun removeGroup(group: KdbxGroup) {
    val removedGroup = groups.remove(group.name)
    LOG.assertTrue(group === removedGroup)
    element.content.remove(group.element)
    group.parent = null
    database.isDirty = true
  }

  @Synchronized
  fun removeGroup(name: String) {
    getGroup(name)?.let { removeGroup(it) }
  }

  @Synchronized
  fun getOrCreateGroup(name: String) = getGroup(name) ?: createGroup(name)

  private fun createGroup(name: String): KdbxGroup {
    val result = createGroup(database, this)
    result.name = name
    if (result == database.rootGroup) {
      throw IllegalStateException("Cannot set root group as child of another group")
    }
    groups.put(result.name, result)
    result.parent = this
    result.locationChanged = LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC)
    element.addContent(result.element)
    database.isDirty = true
    return result
  }

  @Synchronized
  fun getEntry(matcher: (entry: KdbxEntry) -> Boolean) = entries.firstOrNull(matcher)

  @Synchronized
  fun addEntry(entry: KdbxEntry): KdbxEntry {
    entry.group?.removeEntry(entry)
    entries.add(entry)
    entry.group = this
    database.isDirty = true
    element.addContent(entry.entryElement)
    return entry
  }

  private fun removeEntry(entry: KdbxEntry): KdbxEntry {
    if (entries.remove(entry)) {
      entry.group = null
      element.content.remove(entry.entryElement)
      database.isDirty = true
    }
    return entry
  }

  @Synchronized
  fun getEntry(title: String, userName: String?): KdbxEntry? {
    return getEntry {
      it.title == title && (it.userName == userName || userName == null)
    }
  }

  @Synchronized
  fun createEntry(title: String, userName: String?): KdbxEntry {
    val entry = database.createEntry(title)
    entry.userName = userName
    addEntry(entry)
    return entry
  }

  @Synchronized
  fun removeEntry(title: String, userName: String?): KdbxEntry? {
    return getEntry(title, userName)?.let {
      removeEntry(it)
    }
  }
}

internal fun createGroup(db: KeePassDatabase, parent: KdbxGroup?): KdbxGroup {
  val element = Element(KdbxDbElementNames.group)
  ensureElements(element, mandatoryGroupElements)
  return KdbxGroup(element, db, parent)
}

private val mandatoryGroupElements: Map<Array<String>, ValueCreator> = linkedMapOf(
  UUID_ELEMENT_NAME to UuidValueCreator(),
  arrayOf("Notes") to ConstantValueCreator(""),
  ICON_ELEMENT_NAME to ConstantValueCreator("0"),
  CREATION_TIME_ELEMENT_NAME to DateValueCreator(),
  LAST_MODIFICATION_TIME_ELEMENT_NAME to DateValueCreator(),
  LAST_ACCESS_TIME_ELEMENT_NAME to DateValueCreator(),
  EXPIRY_TIME_ELEMENT_NAME to DateValueCreator(),
  EXPIRES_ELEMENT_NAME to ConstantValueCreator("False"),
  USAGE_COUNT_ELEMENT_NAME to ConstantValueCreator("0"),
  LOCATION_CHANGED to DateValueCreator()
)

private fun parseTime(value: String): Long {
  return try {
    ZonedDateTime.parse(value).toEpochSecond()
  }
  catch (e: DateTimeParseException) {
    0
  }
}