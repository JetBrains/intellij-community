// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.providers.FileBookmarkImpl
import com.intellij.ide.bookmark.providers.LineBookmarkImpl
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.ide.bookmark.ui.BookmarksViewState
import com.intellij.ide.bookmark.ui.GroupCreateDialog
import com.intellij.ide.bookmark.ui.GroupSelectDialog
import com.intellij.ide.bookmarks.BookmarksListener
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.Invoker
import java.io.File

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
        if (index > 0) moveGroup(index, 0) // rearrange groups
      }
      field = group
      notifier.defaultGroupChanged(old, group)
    }

  private val groupLineBookmarks
    get() = BookmarksViewState.getInstance(project).groupLineBookmarks

  private val sortedProviders: List<BookmarkProvider>
    get() = when {
      project.isDisposed -> emptyList()
      else -> BookmarkProvider.EP.getExtensions(project).sortedByDescending { it.weight }
    }

  internal val snapshot: List<BookmarkOccurrence>
    get() = synchronized(notifier) {
      notifier.snapshot ?: mutableListOf<BookmarkOccurrence>().also {
        notifier.snapshot = it // update cached snapshot
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
    state.groups.forEach {
      val group = addOrReuseGroup(it.name, it.isDefault)
      it.bookmarks.forEach { bookmark -> group.addLater(bookmark, bookmark.type, bookmark.description) }
    }
  }

  override fun noStateLoaded() {
    val group = addOrReuseGroup(project.name)
    val listener = object : BookmarksListener {
      override fun bookmarkAdded(old: com.intellij.ide.bookmarks.Bookmark) {
        group.addLater(old, old.type, old.description)
      }
    }
    project.messageBus.connect().subscribe(BookmarksListener.TOPIC, listener)
    StartupManager.getInstance(project).runAfterOpened {
      com.intellij.ide.bookmarks.BookmarkManager.getInstance(project).allBookmarks.forEach { listener.bookmarkAdded(it) }
      invoker.invokeLater { noStateLoaded(FavoritesManager.getInstance(project)) }
    }
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

  private fun createDescription(bookmark: Bookmark) = LineBookmarkProvider.readLineText(bookmark as? LineBookmark)?.trim() ?: ""

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
  }?.apply { notifier.selectLater { it.select(this) } }

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
    if (!canRewriteType(type, bookmark)) return
    synchronized(notifier) {
      val info = allBookmarks[bookmark] ?: return
      if (info.type == type) return
      rewriteType(type, bookmark)
      info.changeType(type)
    }
  }

  private fun canToggle(bookmark: Bookmark, type: BookmarkType) =
    getType(bookmark)?.let { it != type || canRemove(bookmark) } ?: canAdd(bookmark)

  override fun toggle(bookmark: Bookmark, type: BookmarkType) =
    getType(bookmark)?.let { if (it != type) setType(bookmark, type) else remove(bookmark) } ?: add(bookmark, type)

  private fun canAdd(bookmark: Bookmark) = null != findGroupsToAdd(bookmark)

  override fun add(bookmark: Bookmark, type: BookmarkType) {
    synchronized(notifier) {
      // if all groups are removed we should add default group
      if (allGroups.isEmpty()) addOrReuseGroup(project.name)
    }
    val groups = findGroupsToAdd(bookmark) ?: return
    val group = chooseGroupToAdd(groups) ?: return
    group.add(bookmark, type, null)
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
    0 -> GroupCreateDialog(project, null, this).showAndGetGroup(true) as? Group
    else -> GroupSelectDialog(project, null, this, groups).showAndGetGroup(true) as? Group
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
    findGroupsToRemove(bookmark)?.forEach { removeFromGroup(it, bookmark) }
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

  private fun canRewriteType(type: BookmarkType, allowed: Bookmark): Boolean {
    if (BookmarksViewState.getInstance(project).rewriteBookmarkType) return true
    val bookmark = getBookmark(type) ?: return true
    if (bookmark == allowed) return true
    return MessageDialogBuilder
      .okCancel(message("bookmark.type.confirmation.title"), when (bookmark) {
        is LineBookmark -> message("bookmark.type.confirmation.line.bookmark", type.mnemonic, bookmark.file.presentableName, bookmark.line + 1)
        is FileBookmark -> message("bookmark.type.confirmation.file.bookmark", type.mnemonic, bookmark.file.presentableName)
        else -> message("bookmark.type.confirmation.bookmark", type.mnemonic)
      })
      .doNotAsk(object : DoNotAskOption.Adapter() {
        override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
          BookmarksViewState.getInstance(project).rewriteBookmarkType = isSelected
        }
      })
      .yesText(message("bookmark.type.confirmation.button"))
      .asWarning()
      .ask(project)
  }

  private fun rewriteType(type: BookmarkType, allowed: Bookmark) {
    synchronized(notifier) {
      val info = findInfo(type) ?: return
      when (info.bookmark) {
        allowed -> return
        is LineBookmark -> removeFromAllGroups(info.bookmark)
        else -> info.changeType(BookmarkType.DEFAULT)
      }
    }
  }

  fun sort(group: BookmarkGroup) {
    (group as? Group)?.sortLater()
  }

  private fun addGroupTo(index: Int, group: Group) = allGroups.add(index, group).also { notifier.groupAdded(group) }
  private fun removeGroupFrom(index: Int) = allGroups.removeAt(index).also { notifier.groupRemoved(it) }
  private fun moveGroup(fromIndex: Int, toIndex: Int) = addGroupTo(toIndex, removeGroupFrom(fromIndex))

  fun move(group: BookmarkGroup, anchor: BookmarkGroup) = synchronized(notifier) {
    if (group == defaultGroup || anchor == defaultGroup) return // cannot move default group
    val fromIndex = allGroups.indexOfFirst { it == group }
    if (fromIndex < 0) return // first group does not exist
    val toIndex = allGroups.indexOfFirst { it == anchor }
    if (toIndex < 0 || toIndex == fromIndex) return // second group does not exist or equal the first one
    moveGroup(fromIndex, toIndex)
  }

  fun move(group: BookmarkGroup, bookmark: Bookmark, anchor: Bookmark) {
    (group as? Group)?.run { move(bookmark, anchor) }
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
    val info = allBookmarks[occurrence.bookmark] ?: return groupLineBookmarks // fake file node
    return info.groups.run { contains(from) && (from == to || !contains(to)) }
  }

  fun canDragInto(group: BookmarkGroup, occurrences: List<BookmarkOccurrence>) = synchronized(notifier) {
    contains(group) && occurrences.all { canDragInto(group, it) }
  }

  fun dragInto(group: BookmarkGroup, occurrences: List<BookmarkOccurrence>) = synchronized(notifier) {
    canDragInto(group, occurrences) && drag(group as Group, occurrences) { 0 }
  }

  fun canDrag(above: Boolean, occurrence: BookmarkOccurrence, occurrences: List<BookmarkOccurrence>): Boolean = synchronized(notifier) {
    if (!contains(occurrence.group)) return false
    if (!occurrences.all { it != occurrence && canDragInto(occurrence.group, it) }) return false
    if (!groupLineBookmarks) return true
    if (!above) {
      val file = getFileGrouping(occurrence.bookmark)
      if (file != null && occurrences.all { isLineGrouping(it.bookmark, file) }) return true
    }
    val file = getLineGrouping(occurrence.bookmark)
    if (file != null) return occurrences.all { isLineGrouping(it.bookmark, file) }
    return occurrences.all { it.bookmark !is LineBookmarkImpl }
  }

  fun drag(above: Boolean, occurrence: BookmarkOccurrence, occurrences: List<BookmarkOccurrence>) = synchronized(notifier) {
    canDrag(above, occurrence, occurrences) && drag(occurrence.group as Group, occurrences) {
      val index = it.indexWithGrouping(occurrence.bookmark)
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

  fun canDragAddInto(group: BookmarkGroup, files: List<File>): Boolean = synchronized(notifier) {
    if (!contains(group)) return false
    val set = files.mapTo(mutableSetOf()) { it.systemIndependentPath }
    return !group.getBookmarks().any { it is FileBookmarkImpl && set.contains(it.file.path) }
  }

  fun dragAddInto(group: BookmarkGroup, files: List<File>) = synchronized(notifier) {
    canDragAddInto(group, files) && dragAdd(group as Group, files) { 0 }
  }

  fun dragAdd(above: Boolean, occurrence: BookmarkOccurrence, files: List<File>) = synchronized(notifier) {
    canDragAddInto(occurrence.group, files) && dragAdd(occurrence.group as Group, files) {
      val index = it.indexWithGrouping(occurrence.bookmark)
      if (index < 0 || above) index else index + 1
    }
  }

  private fun dragAdd(group: Group, files: List<File>, indexSupplier: (Group) -> Int): Boolean {
    val provider = LineBookmarkProvider.find(project) ?: return false
    val bookmarks = files.mapNotNull { provider.createBookmark(VfsUtil.findFileByIoFile(it, true)) }.ifEmpty { return false }
    val index = indexSupplier(group).coerceAtLeast(0)
    bookmarks.forEach { group.add(it, BookmarkType.DEFAULT, null, index) }
    return true
  }

  private fun drag(group: Group, occurrences: List<BookmarkOccurrence>, indexSupplier: (Group) -> Int): Boolean {
    val pairs = mutableListOf<Pair<InManagerInfo, InGroupInfo?>>()
    occurrences.forEach { (it.group as Group).removeWithGrouping(it.bookmark) { pair -> pairs.add(0, pair) } }
    val index = indexSupplier(group).coerceAtLeast(0)
    pairs.forEach { group.add(it.first.bookmark, it.first.type, it.second?.description, index) }
    return true
  }

  // see file-line grouping in LineBookmarkProvider.prepareGroup
  private fun getFileGrouping(bookmark: Bookmark) = if (bookmark is FileBookmarkImpl) bookmark.file else null
  private fun getLineGrouping(bookmark: Bookmark) = if (bookmark is LineBookmarkImpl) bookmark.file else null
  private fun isLineGrouping(bookmark: Bookmark, file: VirtualFile) = bookmark is LineBookmarkImpl && bookmark.file == file


  internal inner class Group(name: String) : BookmarkGroup {
    internal constructor(name: String, isDefault: Boolean, asFirst: Boolean) : this(name) {
      val index = when {
        isDefault -> 0
        !asFirst -> allGroups.size
        defaultGroup == null -> 0
        else -> allGroups.size.coerceAtMost(1)
      }
      addGroupTo(index, this)
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

    internal fun indexWithGrouping(bookmark: Bookmark): Int {
      val index = indexOf(bookmark)
      if (index >= 0) return index
      if (!groupLineBookmarks) return -1
      val file = getFileGrouping(bookmark) ?: return -1
      return groupBookmarks.indexOfFirst { isLineGrouping(it.bookmark, file) }
    }

    internal fun removeWithGrouping(bookmark: Bookmark, process: (Pair<InManagerInfo, InGroupInfo?>) -> Unit) {
      removeFromGroup(this, bookmark)?.run { process(this) }
      if (!groupLineBookmarks) return
      val file = getFileGrouping(bookmark) ?: return
      val bookmarks = groupBookmarks.mapNotNull { if (isLineGrouping(it.bookmark, file)) it.bookmark else null }
      bookmarks.forEach { removeFromGroup(this, it)?.run { process(this) } }
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

    override fun getDescription(bookmark: Bookmark) = synchronized(notifier) {
      val info = getInfo(bookmark) ?: return null
      // create description on first request if it is not initialized
      if (info.description == null) info.description = createDescription(bookmark)
      info.description
    }

    override fun setDescription(bookmark: Bookmark, description: String) = synchronized(notifier) {
      val info = getInfo(bookmark) ?: return
      if (info.description == description) return
      info.description = description
      notifier.bookmarkChanged(this, bookmark)
    }

    override fun canAdd(bookmark: Bookmark) = synchronized(notifier) {
      contains(this) && indexOf(bookmark) < 0 && !(bookmark is LineBookmark && allBookmarks.contains(bookmark))
    }

    override fun add(bookmark: Bookmark, type: BookmarkType, description: String?): Boolean {
      if (!canRewriteType(type, bookmark)) return false
      if (!add(bookmark, type, description, 0)) return false
      notifier.selectLater { it.select(this, bookmark) }
      return true
    }

    internal fun add(bookmark: Bookmark, type: BookmarkType, description: String?, index: Int): Boolean = synchronized(notifier) {
      if (!canAdd(bookmark)) return false // bookmark is already exist
      rewriteType(type, bookmark)
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
    internal fun addLater(context: Any, type: BookmarkType, description: String?) {
      invoker.invokeLater { createBookmark(context)?.let { add(it, type, description, -1) } }
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
        removeGroupFrom(index)
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

    internal fun move(bookmark: Bookmark, anchor: Bookmark) = synchronized(notifier) {
      val fromIndex = indexWithGrouping(bookmark)
      if (fromIndex < 0) return // first bookmark does not exist
      val toIndex = indexWithGrouping(anchor)
      if (toIndex < 0 || toIndex == fromIndex) return // second bookmark does not exist or equal the first one
      val info = groupBookmarks.removeAt(fromIndex.coerceAtLeast(toIndex))
      notifier.bookmarkRemoved(this, info.bookmark)
      groupBookmarks.add(toIndex.coerceAtMost(fromIndex), info)
      notifier.bookmarkAdded(this, info.bookmark)
    }

    internal fun getState() = GroupState().also {
      it.name = name
      it.isDefault = isDefault
      for (info in groupBookmarks) {
        it.bookmarks.add(info.getState())
      }
    }
  }


  internal inner class InGroupInfo(val bookmark: Bookmark, var description: String?) {
    val hash = bookmark.hashCode()

    fun getState() = BookmarkState().also {
      it.provider = bookmark.provider.javaClass.name
      it.description = description
      it.type = allBookmarks[bookmark]?.type ?: BookmarkType.DEFAULT
      it.attributes.putAll(bookmark.attributes)
    }
  }


  internal fun findLineHighlighter(bookmark: Bookmark) = synchronized(notifier) {
    allBookmarks[bookmark]?.renderer?.highlighter
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
