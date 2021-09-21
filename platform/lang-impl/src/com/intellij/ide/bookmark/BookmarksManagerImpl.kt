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
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.Invoker

@State(name = "BookmarksManagerState", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
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

  val snapshot: List<Pair<BookmarkGroup, Bookmark>>
    get() = mutableListOf<Pair<BookmarkGroup, Bookmark>>().also {
      synchronized(notifier) {
        for (group in allGroups) {
          for (bookmark in group.getBookmarks()) {
            it.add(group to bookmark)
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
      val pairs = mutableListOf<Pair<BookmarkState, Group>>()
      state.groups.forEach {
        val group = addOrReuseGroup(it.name, it.isDefault)
        it.bookmarks.forEach { bookmark -> pairs.add(bookmark to group) }
      }
      for (pair in pairs) {
        invoker.invokeLater { createBookmark(pair.first)?.let { pair.second.add(it, pair.first.type, pair.first.description, false) } }
      }
    }
  }

  override fun noStateLoaded() {
    val group = addOrReuseGroup(project.name, true)
    project.messageBus.connect().subscribe(BookmarksListener.TOPIC, object : BookmarksListener {
      override fun bookmarkAdded(old: com.intellij.ide.bookmarks.Bookmark) {
        val bookmark = LineBookmarkProvider.find(project)?.createBookmark(old.file, old.line) ?: return
        group.add(bookmark, old.type, old.description, false)
      }
    })
    invoker.invokeLater { noStateLoaded(FavoritesManager.getInstance(project)) }
  }

  private fun noStateLoaded(manager: FavoritesManager) {
    for (name in manager.availableFavoritesListNames) {
      val group = addOrReuseGroup(name, false)
      for (item in manager.getFavoritesListRootUrls(name)) {
        val bookmark = LineBookmarkProvider.find(project)?.createBookmark(item.data.first) ?: continue
        group.add(bookmark, BookmarkType.DEFAULT, item.data.second ?: "", false)
      }
    }
  }

  private fun findGroup(name: String, hash: Int = name.hashCode()) = synchronized(notifier) {
    allGroups.find { it.hash == hash && it.name == name }
  }

  private fun createBookmark(state: BookmarkState): Bookmark? {
    if (project.isDisposed) return null
    val name = state.provider ?: return null
    val provider = BookmarkProvider.EP.findFirstSafe(project) { it::class.java.name == name }
    return provider?.createBookmark(state.attributes)
  }

  override fun createBookmark(context: Any?) = when {
    project.isDisposed -> null
    else -> BookmarkProvider.EP.getExtensions(project).sortedByDescending { it.weight }.firstNotNullOfOrNull { it.createBookmark(context) }
  }

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

  private fun addOrReuseGroup(name: String, isDefault: Boolean) = synchronized(notifier) {
    findGroup(name)?.also { it.isDefault = isDefault } ?: Group(name, isDefault, false)
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
    val text = LineBookmarkProvider.readLineText(bookmark as? LineBookmark)
    group.add(bookmark, type, text?.trim() ?: "")
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
    else -> GroupSelectDialog(project, null, this, groups).showAndGetGroup()
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

  private fun removeFromGroup(group: Group, bookmark: Bookmark): InManagerInfo? = synchronized(notifier) {
    val info = allBookmarks[bookmark] ?: return null
    if (!info.groups.remove(group)) return null
    val removed = info.groups.isEmpty()
    if (removed) allBookmarks.remove(bookmark)
    group.removeInfo(bookmark)
    info.bookmarkRemoved(group, removed)
    return info
  }

  override fun remove() = synchronized(notifier) {
    while (allGroups.isNotEmpty()) allGroups[0].remove()
  }

  fun sort() = synchronized(notifier) {
    sort(allGroups, this::compare, notifier::groupsSorted)
    allGroups.forEach { it.sortLater() }
  }

  fun sort(group: BookmarkGroup) {
    (group as? Group)?.sortLater()
  }

  private fun <T> sort(list: MutableList<T>, comparator: Comparator<T>, notify: () -> Unit) {
    val sorted = list.sortedWith(comparator)
    if (sorted == list) return
    list.clear()
    list.addAll(sorted)
    notify()
  }

  private fun compare(group1: Group, group2: Group) = when {
    group1 == defaultGroup -> -1
    group2 == defaultGroup -> 1
    else -> NaturalComparator.INSTANCE.compare(group1.name, group2.name)
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
          if (allGroups.contains(this)) notifier.groupRenamed(this)
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

    internal val isValid
      get() = synchronized(notifier) { allGroups.contains(this) }

    private val groupBookmarks = mutableListOf<InGroupInfo>()

    private fun indexOf(bookmark: Bookmark) = when (groupBookmarks.size) {
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

    override fun getDescription(bookmark: Bookmark) = synchronized(notifier) { getInfo(bookmark)?.description }

    override fun setDescription(bookmark: Bookmark, description: String) = synchronized(notifier) {
      val info = getInfo(bookmark) ?: return
      if (info.description == description) return
      info.description = description
    }

    override fun canAdd(bookmark: Bookmark) = synchronized(notifier) {
      allGroups.contains(this) && indexOf(bookmark) < 0 && !(bookmark is LineBookmark && allBookmarks.contains(bookmark))
    }

    override fun add(bookmark: Bookmark, type: BookmarkType, description: String) = add(bookmark, type, description, true)

    internal fun add(bookmark: Bookmark, type: BookmarkType, description: String, asFirst: Boolean): Boolean = synchronized(notifier) {
      if (!canAdd(bookmark)) return false // bookmark is already exist
      findInfo(type)?.changeType(BookmarkType.DEFAULT)
      val info = allBookmarks.computeIfAbsent(bookmark) { InManagerInfo(it, type) }
      val index = if (asFirst) 0 else groupBookmarks.size
      groupBookmarks.add(index, InGroupInfo(info.bookmark, description))
      val added = info.groups.isEmpty()
      info.groups.add(this)
      info.bookmarkAdded(this, added)
      info.changeType(type)
      return true
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
      if (isValid) sort(groupBookmarks, this::compare) { notifier.bookmarksSorted(this) }
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


  internal inner class InGroupInfo(val bookmark: Bookmark, var description: String) {
    val hash = bookmark.hashCode()

    fun getState() = BookmarkState().also {
      it.provider = bookmark.provider.javaClass.name
      it.description = description
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
