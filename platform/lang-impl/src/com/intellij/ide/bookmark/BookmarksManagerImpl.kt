// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.ide.bookmark.ui.GroupCreateDialog
import com.intellij.ide.bookmark.ui.GroupSelectDialog
import com.intellij.ide.bookmarks.BookmarksListener
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.Invoker
import java.util.function.Supplier

@State(name = "BookmarksManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class BookmarksManagerImpl(val project: Project) : BookmarksManager, PersistentStateComponentWithModificationTracker<ManagerState> {

  private val invoker = Invoker.forBackgroundThreadWithReadAction(project)
  private val notifier = ModificationNotifier(project)
  private val allBookmarks = mutableMapOf<Bookmark, InManagerInfo>()
  private val allGroups = mutableListOf<Group>()
  private var defaultGroup: Group? = null
    set(group) = synchronized(notifier) {
      val old = field
      if (old === group) return@synchronized // nothing to change
      group?.let {
        val index = allGroups.indexOf(it)
        if (index < 0) return@synchronized // group is not added
        if (index > 0) { // rearrange groups
          allGroups.removeAt(index)
          notifier.groupRemoved(it)
          allGroups.add(0, it)
          notifier.groupAdded(it)
        }
      }
      field = group
      notifier.defaultGroupChanged(old, group)
    }

  private val sortedProviders: List<BookmarkProvider>
    get() = when {
      project.isDisposed -> emptyList()
      else -> BookmarkProvider.EP.getExtensions(project).sortedByDescending { it.weight }
    }

  internal val snapshot: List<BookmarkOccurrence>
    get() = mutableListOf<BookmarkOccurrence>().also {
      synchronized(notifier) {
        for (group in allGroups) {
          for (bookmark in group.getBookmarks()) {
            it.add(BookmarkOccurrence(group, bookmark, it.size, it))
          }
        }
      }
    }

  override fun getStateModificationCount() = notifier.count

  override fun getState() = ManagerState().apply {
    synchronized(notifier) { allGroups.mapTo(groups) { it.getState() } }
  }

  override fun loadState(state: ManagerState) {
    remove() // see com.intellij.tasks.context.BookmarkContextProvider
    if (state.groups.isNotEmpty()) {
      state.groups.forEach {
        val group = addOrReuseGroup(it.name, it.isDefault)
        it.bookmarks.forEach { bookmark -> group.addLater(bookmark, bookmark.type, { bookmark.description } ) }
      }
    }
  }

  override fun noStateLoaded() {
    val group = addOrReuseGroup(project.name)
    val listener = object : BookmarksListener {
      override fun bookmarkAdded(old: com.intellij.ide.bookmarks.Bookmark) {
        group.addLater(old, old.type, { old.description } )
      }
    }
    project.messageBus.connect().subscribe(BookmarksListener.TOPIC, listener)
    com.intellij.ide.bookmarks.BookmarkManager.getInstance(project).allBookmarks.forEach { listener.bookmarkAdded(it) }
    invoker.invokeLater { noStateLoaded(FavoritesManager.getInstance(project)) }
  }

  private fun noStateLoaded(manager: FavoritesManager) {
    for (name in manager.availableFavoritesListNames) {
      val group = addOrReuseGroup(name)
      for (item in manager.getFavoritesListRootUrls(name)) {
        group.addLater(item.data.first, BookmarkType.DEFAULT, null)
      }
    }
  }

  private fun findGroup(name: String, hash: Int = name.hashCode()) = synchronized(notifier) {
    allGroups.find { it.hash == hash && it.name == name }
  }

  override fun createBookmark(context: Any?) = when (context is BookmarkState) {
    true -> sortedProviders.firstOrNull { it::class.java.name == context.provider }?.createBookmark(context.attributes)
    else -> sortedProviders.firstNotNullOfOrNull { it.createBookmark(context) }
  }

  private fun createDescription(bookmark: Bookmark): String = LineBookmarkProvider.readLineText(bookmark as? LineBookmark)?.trim() ?: ""

  override fun getBookmarks() = synchronized(notifier) { allBookmarks.keys.toList() }

  override fun getDefaultGroup(): BookmarkGroup? = synchronized(notifier) { defaultGroup }

  override fun getGroup(name: String): BookmarkGroup? = findGroup(name)

  override fun getGroups(): List<BookmarkGroup> = synchronized(notifier) { allGroups.toList() }

  override fun getGroups(bookmark: Bookmark) = synchronized(notifier) {
    allBookmarks[bookmark]?.groups?.let { allGroups.filter(it::contains) } ?: emptyList<BookmarkGroup>()
  }

  override fun addGroup(name: String, isDefault: Boolean): BookmarkGroup? = when {
    name.isBlank() -> null
    else -> synchronized(notifier) { if (findGroup(name) != null) null else Group(name, isDefault, true) }
  }

  private fun addOrReuseGroup(name: String, isDefaultState: Boolean? = null) = synchronized(notifier) {
    findGroup(name)?.apply { isDefaultState?.let { isDefault = it } } ?: Group(name, isDefaultState ?: false, false)
  }

  override fun getBookmark(type: BookmarkType) = synchronized(notifier) {
    findInfo(type)?.bookmark
  }

  private fun findInfo(type: BookmarkType) = when (type) {
    BookmarkType.DEFAULT -> null
    else -> allBookmarks.values.find { it.type == type }
  }

  override fun getAssignedTypes() = synchronized(notifier) {
    allBookmarks.values.map { it.type }.filterTo(mutableSetOf()) { it != BookmarkType.DEFAULT }
  }

  override fun getType(bookmark: Bookmark) = synchronized(notifier) {
    allBookmarks[bookmark]?.type
  }

  override fun setType(bookmark: Bookmark, type: BookmarkType) {
    synchronized(notifier) {
      val info = allBookmarks[bookmark] ?: return
      if (info.type == type) return
      findInfo(type)?.changeType(BookmarkType.DEFAULT)
      info.changeType(type)
    }
  }

  private fun canToggle(bookmark: Bookmark, type: BookmarkType) = getType(bookmark)?.let { it != type || canRemove(bookmark) } ?: canAdd(
    bookmark)

  override fun toggle(bookmark: Bookmark, type: BookmarkType) = getType(bookmark)?.let {
    if (it != type) setType(bookmark, type)
    else remove(bookmark)
  } ?: add(bookmark, type)

  private fun canAdd(bookmark: Bookmark) = null != findGroupsToAdd(bookmark)

  override fun add(bookmark: Bookmark, type: BookmarkType) {
    val groups = findGroupsToAdd(bookmark) ?: return
    val group = chooseGroupToAdd(groups) ?: return
    group.add(bookmark, type, { createDescription(bookmark) } )
  }

  private fun findGroupsToAdd(bookmark: Bookmark) = synchronized(notifier) {
    val info = allBookmarks[bookmark]
    when {
      info == null -> defaultGroup?.let { listOf(it) } ?: allGroups.toList()
      bookmark is LineBookmark -> null
      else -> allGroups.filter { !info.groups.contains(it) }
    }
  }

  private fun chooseGroupToAdd(groups: List<Group>) = when (groups.size) {
    1 -> groups[0]
    0 -> GroupCreateDialog(project, null, this).showAndGetGroup(true)
    else -> GroupSelectDialog(project, null, this, groups).showAndGetGroup(true)
  }

  private fun canRemove(bookmark: Bookmark) = !findGroupsToRemove(bookmark).isNullOrEmpty()

  override fun remove(bookmark: Bookmark) {
    val groups = findGroupsToRemove(bookmark)
    if (groups.isNullOrEmpty()) return

    if (groups.size == 1) {
      removeFromGroup(groups[0], bookmark)
    }
    else if (groups.isNotEmpty()) { //TODO:choose
    }
  }

  private fun findGroupsToRemove(bookmark: Bookmark) = synchronized(notifier) {
    allBookmarks[bookmark]?.groups?.let { allGroups.filter(it::contains) }
  }

  private fun removeFromAllGroups(bookmark: Bookmark) = synchronized(notifier) {
    val info = allBookmarks.remove(bookmark) ?: return
    val iterator = info.groups.iterator()
    while (iterator.hasNext()) {
      val group = iterator.next()
      iterator.remove()
      info.bookmarkRemoved(group, !iterator.hasNext())
    }
  }

  private fun removeFromGroup(group: Group, bookmark: Bookmark): Pair<InManagerInfo, InGroupInfo?>? = synchronized(notifier) {
    val info = allBookmarks[bookmark] ?: return null
    if (!info.groups.remove(group)) return null
    val removed = info.groups.isEmpty()
    if (removed) allBookmarks.remove(bookmark)
    val result = info to group.removeInfo(bookmark)
    info.bookmarkRemoved(group, removed)
    return result
  }

  override fun remove() = synchronized(notifier) {
    while (allGroups.isNotEmpty()) allGroups[0].remove()
  }

  fun sort(group: BookmarkGroup) {
    (group as? Group)?.sortLater()
  }

  override fun update(map: MutableMap<Bookmark, Bookmark?>) {
    while (map.isNotEmpty()) {
      val size = map.size
      val iterator = map.iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val updated = entry.value?.let { updateInAllGroups(entry.key, it) }
        if (updated == null) removeFromAllGroups(entry.key)
        if (updated != false) iterator.remove()
      }
      if (map.size == size) break
    }
    if (map.isNotEmpty()) {
      val sb = StringBuilder("cannot resolve")
      for (entry in map) sb.append('\n').append(entry.key).append(" -> ").append(entry.value)
      //TODO:logging:println(sb)
    }
  }

  private fun updateInAllGroups(old: Bookmark, new: Bookmark): Boolean = synchronized(notifier) {
    if (allBookmarks.contains(new)) return false // cannot update to new bookmark if it exists
    val oldInfo = allBookmarks.remove(old) ?: return true
    val newInfo = InManagerInfo(new, oldInfo.type)
    val oldIterator = oldInfo.groups.iterator()
    while (oldIterator.hasNext()) {
      val group = oldIterator.next()
      if (group.updateInfo(old, new)) newInfo.groups.add(group)
      oldIterator.remove()
      oldInfo.bookmarkRemoved(group, !oldIterator.hasNext())
    }
    val newIterator = newInfo.groups.iterator()
    if (newIterator.hasNext()) {
      allBookmarks[new] = newInfo
      newInfo.bookmarkAdded(newIterator.next(), true)
      while (newIterator.hasNext()) {
        newInfo.bookmarkAdded(newIterator.next(), false)
      }
    }
    return true
  }

  private fun contains(group: Group) = allGroups.contains(group)
  private fun contains(group: BookmarkGroup) = group is Group && contains(group)

  private fun canDragInto(group: BookmarkGroup, occurrence: BookmarkOccurrence): Boolean {
    val to = group as? Group ?: return false
    val from = occurrence.group as? Group ?: return false
    return allBookmarks[occurrence.bookmark]?.groups?.run { contains(from) && (from == to || !contains(to)) } ?: false
  }

  fun canDragInto(group: BookmarkGroup, occurrences: List<BookmarkOccurrence>) = synchronized(notifier) {
    contains(group) && occurrences.all { canDragInto(group, it) }
  }

  fun dragInto(group: BookmarkGroup, occurrences: List<BookmarkOccurrence>) = synchronized(notifier) {
    canDragInto(group, occurrences) && drag(group as Group, occurrences) { 0 }
  }

  fun canDrag(above: Boolean, occurrence: BookmarkOccurrence, occurrences: List<BookmarkOccurrence>) = synchronized(notifier) {
    contains(occurrence.group) && occurrences.all { it != occurrence && canDragInto(occurrence.group, it) }
  }

  fun drag(above: Boolean, occurrence: BookmarkOccurrence, occurrences: List<BookmarkOccurrence>) = synchronized(notifier) {
    canDrag(above, occurrence, occurrences) && drag(occurrence.group as Group, occurrences) {
      val index = it.indexOf(occurrence.bookmark)
      if (index < 0 || above) index else index + 1
    }
  }

  fun canDrag(above: Boolean, group: BookmarkGroup, groups: List<BookmarkGroup>) = synchronized(notifier) {
    (!above || group != defaultGroup) && contains(group) && groups.all { it != defaultGroup && it != group && contains(it) }
  }

  fun drag(above: Boolean, group: BookmarkGroup, groups: List<BookmarkGroup>): Boolean = synchronized(notifier) {
    if (!canDrag(above, group, groups)) return false
    val set = mutableSetOf<Group>()
    groups.mapTo(set) { it as Group }
    if (groups.size != set.size) return false
    allGroups.removeAll(set)
    set.forEach { notifier.groupRemoved(it) }
    var index = allGroups.indexOf(group as Group).coerceAtLeast(0) // see #canDrag(Boolean, BookmarkGroup, List<BookmarkGroup>)
    if (!above) index++
    allGroups.addAll(index, set)
    set.forEach { notifier.groupAdded(it) }
    return true
  }

  private fun drag(group: Group, occurrences: List<BookmarkOccurrence>, indexSupplier: (Group) -> Int): Boolean {
    val pairs: List<Pair<InManagerInfo, InGroupInfo?>> = occurrences.mapNotNull { removeFromGroup(it.group as Group, it.bookmark) }.asReversed()
    val index = indexSupplier(group).coerceAtLeast(0)
    pairs.forEach { group.add(it.first.bookmark, it.first.type, it.second?.description ?: Supplier { "" }, index) }
    return true
  }


  internal inner class Group(name: String) : BookmarkGroup {
    internal constructor(name: String, isDefault: Boolean, asFirst: Boolean) : this(name) {
      val index = when {
        isDefault -> 0
        !asFirst -> allGroups.size
        defaultGroup == null -> 0
        else -> allGroups.size.coerceAtMost(1)
      }
      allGroups.add(index, this)
      notifier.groupAdded(this)
      this.isDefault = isDefault
    }

    internal var hash = name.hashCode()
    override var name: String = name
      set(value) = synchronized(notifier) { // do not allow to use an empty name or the name of an existing group
        if (value.isNotBlank() && findGroup(value) == null) {
          field = value
          hash = value.hashCode() // notify only if the group is added
          if (contains(this)) notifier.groupRenamed(this)
        }
      }

    override var isDefault
      get() = this === defaultGroup
      set(value) = synchronized(notifier) {
        when {
          value -> defaultGroup = this
          isDefault -> defaultGroup = null
        }
      }

    private val groupBookmarks = mutableListOf<InGroupInfo>()

    internal fun indexOf(bookmark: Bookmark) = when (groupBookmarks.size) {
      0 -> -1
      1 -> if (groupBookmarks[0].bookmark == bookmark) 0 else -1
      else -> {
        val hash = bookmark.hashCode()
        groupBookmarks.indexOfFirst { it.hash == hash && it.bookmark == bookmark }
      }
    }

    private fun getInfo(bookmark: Bookmark) = indexOf(bookmark).let { if (it < 0) null else groupBookmarks[it] }

    internal fun removeInfo(bookmark: Bookmark) = indexOf(bookmark).let { if (it < 0) null else groupBookmarks.removeAt(it) }

    internal fun updateInfo(old: Bookmark, new: Bookmark): Boolean {
      val index = indexOf(old)
      if (index < 0) return false
      val info = groupBookmarks[index]
      groupBookmarks[index] = InGroupInfo(new, info.description)
      return true
    }

    override fun getBookmarks(): List<Bookmark> = synchronized(notifier) { groupBookmarks.map { it.bookmark } }

    override fun getDescription(bookmark: Bookmark) = synchronized(notifier) { getInfo(bookmark)?.description?.get() }

    override fun setDescription(bookmark: Bookmark, description: Supplier<String>) = synchronized(notifier) {
      val info = getInfo(bookmark) ?: return
      if (info.description == description) return
      info.description = description
      notifier.bookmarkChanged(this, bookmark)
    }

    override fun canAdd(bookmark: Bookmark) = synchronized(notifier) {
      contains(this) && indexOf(bookmark) < 0 && !(bookmark is LineBookmark && allBookmarks.contains(bookmark))
    }

    override fun add(bookmark: Bookmark, type: BookmarkType, description: Supplier<String>) = add(bookmark, type, description, 0)

    internal fun add(bookmark: Bookmark, type: BookmarkType, description: Supplier<String>, index: Int): Boolean = synchronized(notifier) {
      if (!canAdd(bookmark)) return false // bookmark is already exist
      findInfo(type)?.changeType(BookmarkType.DEFAULT)
      val info = allBookmarks.computeIfAbsent(bookmark) { InManagerInfo(it, type) }
      groupBookmarks.add(if (index < 0) groupBookmarks.size else index, InGroupInfo(info.bookmark, description))
      val added = info.groups.isEmpty()
      info.groups.add(this)
      info.bookmarkAdded(this, added)
      info.changeType(type)
      return true
    }

    /**
     * Creates a bookmark from the specified context and adds it to the group if possible.
     * It is intended to restore bookmark state or to migrate old bookmarks and favorites.
     * Each bookmark is created separately that allows to wait for the end of indexing.
     */
    internal fun addLater(context: Any, type: BookmarkType, description: Supplier<String>?) {
      invoker.invokeLater { createBookmark(context)?.let { add(it, type, description ?: Supplier { -> createDescription(it) }, -1) } }
    }

    override fun canRemove(bookmark: Bookmark) = synchronized(notifier) {
      allBookmarks[bookmark]?.groups?.contains(this) ?: false
    }

    override fun remove(bookmark: Bookmark) = removeFromGroup(this, bookmark) != null

    override fun remove() = synchronized(notifier) {
      val index = allGroups.indexOf(this)
      if (index >= 0) {
        if (isDefault) defaultGroup = null
        for (bookmark in getBookmarks()) {
          removeFromGroup(this, bookmark)
        }
        allGroups.removeAt(index)
        notifier.groupRemoved(this)
      }
    }

    internal fun sortLater() = invoker.invokeLater { sort() }

    private fun sort() = synchronized(notifier) {
      if (groupBookmarks.isEmpty()) return
      if (!contains(this)) return
      val list = groupBookmarks.sortedWith(this::compare)
      if (list == groupBookmarks) return
      groupBookmarks.clear()
      groupBookmarks.addAll(list)
      notifier.bookmarksSorted(this)
    }

    private fun compare(info1: InGroupInfo, info2: InGroupInfo): Int {
      val weight1 = info1.bookmark.provider.weight
      val weight2 = info2.bookmark.provider.weight
      if (weight1 > weight2) return -1
      if (weight1 < weight2) return 1
      return info1.bookmark.provider.compare(info1.bookmark, info2.bookmark)
    }

    internal fun getState() = GroupState().also {
      it.name = name
      it.isDefault = isDefault
      for (info in groupBookmarks) {
        it.bookmarks.add(info.getState())
      }
    }
  }


  internal inner class InGroupInfo(val bookmark: Bookmark, var description: Supplier<String>) {
    val hash = bookmark.hashCode()

    fun getState() = BookmarkState().also {
      it.provider = bookmark.provider.javaClass.name
      it.description = description.get()
      it.type = allBookmarks[bookmark]?.type ?: BookmarkType.DEFAULT
      it.attributes.putAll(bookmark.attributes)
    }
  }


  internal fun refreshRenderers(file: VirtualFile) = synchronized(notifier) {
    allBookmarks.values.forEach { if (it.renderer?.bookmark?.file == file) it.refreshRenderer() }
  }

  internal inner class InManagerInfo(val bookmark: Bookmark, var type: BookmarkType) {
    val renderer = (bookmark as? LineBookmark)?.let { GutterLineBookmarkRenderer(it) }
    val groups = mutableSetOf<Group>()

    fun refreshRenderer() = renderer?.refreshHighlighter { groups.isEmpty() }

    fun bookmarkAdded(group: BookmarkGroup, initial: Boolean) {
      notifier.bookmarkAdded(group, bookmark)
      if (initial) refreshRenderer()
    }

    fun bookmarkRemoved(group: BookmarkGroup, completely: Boolean) {
      notifier.bookmarkRemoved(group, bookmark)
      if (completely) refreshRenderer()
    }

    fun bookmarkChanged(group: BookmarkGroup) {
      notifier.bookmarkChanged(group, bookmark)
      refreshRenderer()
    }

    fun changeType(type: BookmarkType) {
      if (this.type == type) return
      this.type = type
      notifier.bookmarkTypeChanged(bookmark)
      refreshRenderer()
    }
  }
}
