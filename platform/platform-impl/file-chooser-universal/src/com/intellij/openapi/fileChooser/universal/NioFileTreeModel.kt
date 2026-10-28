// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.LayeredIcon
import com.intellij.ui.icons.PredefinedIconOverlayService
import com.intellij.ui.tree.MapBasedTree
import com.intellij.ui.tree.MapBasedTree.Entry
import com.intellij.util.PlatformIcons
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.InvokerSupplier
import com.intellij.util.io.PlatformNioHelper
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.tree.AbstractTreeModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.tree.TreePath
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.seconds

/**
 * A tree model backed by Java NIO [Path] instead of [com.intellij.openapi.vfs.VirtualFile].
 * Provides the same public interface as [com.intellij.openapi.fileChooser.tree.FileTreeModel]
 * but does not use the Virtual File System (VFS) internally.
 *
 * The blocking NIO calls never run on the shared invoker thread (IJPL-259465).
 * A listing runs in a cancellable coroutine on [Dispatchers.IO]; [invalidate] and a node
 * collapse cancel it, so a Refresh restarts the load. A child whose attributes do not
 * arrive in time appears as an unresolved node, and a lazy background job resolves it.
 */
@ApiStatus.Internal
class NioFileTreeModel(
  descriptor: FileChooserDescriptor,
  sortDirectories: Boolean = true,
  internal val contributor: UniversalFileChooserContributor? = null,
) : AbstractTreeModel(), InvokerSupplier {

  companion object {

    private val LOG = Logger.getInstance(NioFileTreeModel::class.java)

    /** The most symlink resolutions that run at the same time; the rest wait for a permit. */
    private const val MAX_SYM_LINKS_RESOLVED = 4

    /** How long a listing waits for the attributes of one child before the child stays unresolved. */
    private val LISTING_ATTRIBUTES_TIMEOUT = 5.seconds

    /** How long a lazy resolution runs before it marks the node as not valid. */
    private val RESOLVE_TIMEOUT = 10.seconds

    /**
     * Whether a node may have children. Resolved from [attrs] when the caller already read them
     * (children come from [PlatformNioHelper.visitDirectory] with attributes), so expanding
     * a directory does not `stat` anything again. A symlink stays expandable until the lazy
     * resolution reads its target, and an unresolved entry stays expandable too.
     */
    private fun resolveIsDirectory(path: Path?, attrs: BasicFileAttributes?): Boolean = when {
      path == null -> true                                    // a virtual root is a container and must stay expandable
      attrs == null -> true                                   // a root or an unresolved entry stays expandable
      else -> attrs.isDirectory || attrs.isSymbolicLink
    }

    /** The icon of an entry whose target kind is not known yet: a question mark with the symlink arrow. */
    private fun unresolvedIcon(): Icon = withSymlinkOverlay(AllIcons.FileTypes.Unknown)

    private fun withSymlinkOverlay(icon: Icon): Icon =
      ApplicationManager.getApplication().getService(PredefinedIconOverlayService::class.java)?.createSymlinkIcon(icon) ?: icon
  }

  private val invoker: Invoker = Invoker.forBackgroundThreadWithoutReadAction(this)
  private val scope = service<NioFileTreeModelScopeHolder>().scope.childScope("NioFileTreeModel")
  private val resolveSemaphore = Semaphore(MAX_SYM_LINKS_RESOLVED)
  private val listingJobs = ConcurrentHashMap<Path, Deferred<List<ChildEntry>?>>()
  private val state: State = State(descriptor, sortDirectories, this)

  @Volatile
  private var contributorRoots: List<UniversalFileChooserContributor.Root> = emptyList()

  @Volatile
  private var roots: List<Root>? = null

  override fun dispose() {
    scope.cancel()
    super.dispose()
  }

  fun invalidate() {
    cancelLoading()
    invoker.invoke {
      roots?.forEach { it.tree.invalidate() }
      treeStructureChanged(state.path, null, null)
    }
  }

  /** Cancels the in-flight listing of [path], or of every path when [path] is null. */
  fun cancelLoading(path: Path? = null) {
    if (path == null) {
      listingJobs.values.forEach { it.cancel() }
    }
    else {
      listingJobs[path]?.cancel()
    }
  }

  fun setContributorRoots(newRoots: List<UniversalFileChooserContributor.Root>) {
    invoker.invoke {
      contributorRoots = newRoots
      roots = null
      treeStructureChanged(state.path, null, null)
    }
  }

  override fun getInvoker(): Invoker = invoker

  override fun getRoot(): Any? {
    if (state.path != null) return state
    if (roots == null) roots = state.getRoots()
    val r = roots!!
    return if (r.size == 1) r.first() else null
  }

  override fun getChild(parent: Any?, index: Int): Any? {
    if (parent === state) {
      if (roots == null) roots = state.getRoots()
      val r = roots!!
      if (index in r.indices) return r[index]
    }
    else if (parent is Node) {
      val entry = getEntry(parent, true)
      if (entry != null) return entry.getChild(index)
    }
    return null
  }

  override fun getChildCount(parent: Any?): Int {
    if (parent === state) {
      if (roots == null) roots = state.getRoots()
      return roots!!.size
    }
    else if (parent is Node) {
      val entry = getEntry(parent, true)
      if (entry != null) return entry.childCount
    }
    return 0
  }

  override fun isLeaf(node: Any?): Boolean {
    if (node is Node) {
      val entry = getEntry(node, false)
      if (entry != null) return entry.isLeaf
    }
    return false
  }

  override fun getIndexOfChild(parent: Any?, child: Any?): Int {
    if (parent === state) {
      if (roots == null) roots = state.getRoots()
      val r = roots!!
      for (i in r.indices) {
        if (child === r[i]) return i
      }
    }
    else if (parent is Node && child is Node) {
      val entry = getEntry(parent, true)
      if (entry != null) return entry.getIndexOf(child)
    }
    return -1
  }

  private fun getEntry(node: Node, loadChildren: Boolean): Entry<Node>? {
    val r = roots ?: return null
    for (root in r) {
      // VirtualRoot has no path, so MapBasedTree can't look it up by key.
      // Match it directly by identity to avoid the "key function provides null" warning.
      val entry = when {
        node === root -> root.tree.rootEntry
        node.path == null -> null
        else -> root.tree.getEntry(node)
      }
      if (entry != null) {
        if (loadChildren && entry.isLoadingRequired) {
          root.updateChildren(state, entry)
        }
        return entry
      }
    }
    return null
  }

  fun matchRoot(path: Path): Path? =
    state.getRoots().firstOrNull { root -> root.path?.invariantSeparatorsPathString == path.invariantSeparatorsPathString }?.path

  fun getVirtualRoot(node: NioFileNode): UniversalFileChooserContributor.Root? =
    (node as? VirtualRoot)?.contributorRoot

  fun setRootError(path: Path) {
    invoker.invoke {
      val r = roots ?: return@invoke
      val target = r.firstOrNull { it.path?.invariantSeparatorsPathString == path.invariantSeparatorsPathString }
                   ?: return@invoke
      if (target.updateIcon(AllIcons.General.Error)) {
        treeNodesChanged(TreePath(target), null, null)
      }
    }
  }

  /**
   * Waits for the listing of [node] on the caller thread.
   * The blocking NIO calls run in a cancellable coroutine on [Dispatchers.IO],
   * so [cancelLoading] frees the shared invoker thread when a path hangs.
   * Returns null when the listing was cancelled.
   */
  private fun loadChildren(node: Node): Listing? {
    val path = node.path
    if (path == null || !node.isDirectory) return Listing(null)
    val deferred = listingJobs.computeIfAbsent(path) {
      val job = scope.async { listChildren(path) }
      job.invokeOnCompletion { listingJobs.remove(path, job) }
      job
    }
    return runBlockingMaybeCancellable {
      deferred.join()
      // `isCancelled` also covers a failed listing; the entry stays in the loading-required state
      if (deferred.isCancelled) null else Listing(deferred.await())
    }
  }

  /**
   * Lists [directory] with the NOFOLLOW attributes that [PlatformNioHelper.visitDirectory] already read.
   * The attributes a directory stream did not cache are read concurrently, each within
   * [LISTING_ATTRIBUTES_TIMEOUT], so one child that does not answer cannot stall the listing.
   * Returns null when the directory cannot be listed.
   */
  private suspend fun listChildren(directory: Path): List<ChildEntry>? {
    val candidates = try {
      runInterruptible(Dispatchers.IO) {
        val found = ArrayList<ChildEntry>()
        PlatformNioHelper.visitDirectory(directory, null) { childPath, attrsResult ->
          found.add(ChildEntry(childPath, runCatching { attrsResult?.get() }.getOrNull()))
          true
        }
        found
      }
    }
    catch (e: IOException) {
      LOG.debug("cannot list directory: $directory", e)
      return null
    }
    catch (e: SecurityException) {
      LOG.debug("cannot list directory: $directory", e)
      return null
    }
    return coroutineScope {
      candidates.map { candidate ->
        async(Dispatchers.IO) {
          if (candidate.attrs != null) candidate else readChildEntry(candidate.path)
        }
      }.awaitAll().filterNotNull()
    }
  }

  /**
   * Reads the NOFOLLOW attributes of one child.
   * Returns an unresolved entry when the read does not answer within [LISTING_ATTRIBUTES_TIMEOUT],
   * and null when the child is unreadable, so the listing skips it.
   */
  private suspend fun readChildEntry(path: Path): ChildEntry? {
    val attrs = try {
      withTimeoutOrNull(LISTING_ATTRIBUTES_TIMEOUT) {
        runInterruptible(Dispatchers.IO) { NioFiles.readAttributes(path) }
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: IOException) {
      return null
    }
    catch (_: RuntimeException) {
      return null
    }
    return ChildEntry(path, attrs)
  }

  /**
   * Resolves the target of a symlink, or the missing attributes of an unresolved entry,
   * in a cancellable background job on [Dispatchers.IO].
   * At most [MAX_SYM_LINKS_RESOLVED] resolutions run at the same time; the rest wait for a permit.
   * On a timeout or an error the node becomes not valid and shows the error overlay icon.
   */
  private fun scheduleResolve(node: Node, attrs: BasicFileAttributes?) {
    val path = node.path ?: return
    if (!node.startResolve()) return
    scope.launch {
      val resolved = resolveSemaphore.withPermit {
        withTimeoutOrNull(RESOLVE_TIMEOUT) {
          runInterruptible(Dispatchers.IO) {
            readResolved(path, attrs)
          }
        }
      }
      ensureActive()
      invoker.invoke {
        if (node.applyResolved(resolved)) {
          val entry = getEntry(node, false)
          if (entry != null) treeNodesChanged(entry, null, null)
        }
      }
    }
  }

  private fun readResolved(path: Path, attrs: BasicFileAttributes?): Resolved? {
    try {
      val noFollow = attrs ?: NioFiles.readAttributes(path)
      val target = if (noFollow.isSymbolicLink) Files.readAttributes(path, BasicFileAttributes::class.java) else noFollow
      return Resolved(noFollow, target)
    }
    catch (e: IOException) {
      LOG.debug("cannot resolve: $path", e)
      return null
    }
    catch (e: SecurityException) {
      LOG.debug("cannot resolve: $path", e)
      return null
    }
  }

  /** The result of one directory listing. A null [entries] means the directory cannot be listed. */
  private class Listing(@JvmField val entries: List<ChildEntry>?)

  /** The NOFOLLOW [attrs] of a node and the [target] attributes with symlinks followed. */
  private class Resolved(@JvmField val attrs: BasicFileAttributes, @JvmField val target: BasicFileAttributes)

  private enum class ResolveState { UNRESOLVED, RESOLVING, RESOLVED, FAILED }

  /** A child of a listed directory. Null [attrs] means the attributes did not arrive in time. */
  private data class ChildEntry(
    val path: Path,
    val attrs: BasicFileAttributes?,
  ) {
    val isDirectory: Boolean
      get() = resolveIsDirectory(path, attrs)
  }

  private class State(
    val descriptor: FileChooserDescriptor,
    private val sortDirectories: Boolean,
    val model: NioFileTreeModel,
  ) {
    val descriptorRoots: List<Path>? = getRoots(descriptor)
    val path: TreePath? = if (descriptorRoots != null && descriptorRoots.size == 1) null else TreePath(this)

    fun compare(one: ChildEntry, two: ChildEntry): Int {
      if (sortDirectories) {
        if (one.isDirectory != two.isDirectory) return if (one.isDirectory) -1 else 1
      }
      return StringUtil.naturalCompare(model.contributor.fileName(one.path), model.contributor.fileName(two.path))
    }

    fun isVisible(entry: ChildEntry): Boolean {
      if (!descriptor.isShowHiddenFiles) {
        if (isHiddenFromAttrs(entry)) return false
      }
      if (!descriptor.isChooseFiles && !entry.isDirectory &&
          !(descriptor.isChooseJarContents && NioFileChooserUtil.isArchiveFile(entry.path))) {
        return false
      }
      if (!entry.isDirectory) {
        val extFilter = descriptor.extensionFilter
        if (extFilter != null) {
          val name = entry.path.fileName?.toString() ?: return false
          if (!extFilter.second.any { StringUtil.endsWithIgnoreCase(name, ".$it") }) return false
        }
        val pathFilter = descriptor.pathFilter
        if (pathFilter != null && !pathFilter.test(entry.path)) return false
      }
      return true
    }

    private fun isHiddenFromAttrs(entry: ChildEntry): Boolean = NioFileChooserUtil.isHidden(entry.path, entry.attrs)

    fun getRoots(): List<Root> {
      if (!model.invoker.isValidThread) {
        LOG.error(IllegalStateException(Thread.currentThread().name))
      }
      if (descriptorRoots != null) {
        val files = descriptorRoots
        if (files.isEmpty()) return emptyList()
        return files.map { file -> Root(this, model.contributor, file, contributorRoot = null) }
      }
      val realRoots = model.contributorRoots.filter { it.path != null }
      val virtualRoots = model.contributorRoots.filter { it.path == null }
      if (realRoots.isEmpty() && virtualRoots.isEmpty()) return emptyList()
      val realRootNodes = realRoots.map { root -> Root(this, model.contributor, root.path!!, contributorRoot = root) }
      val virtualRootNodes = virtualRoots.map { root -> VirtualRoot(this, model.contributor, root) }
      return realRootNodes + virtualRootNodes
    }


    override fun toString(): String = descriptor.title

    /**
     * Whether [path] can be a root of this tree. The check asks the contributor for ownership
     * and does not touch the file system. An existence check such as [Files.exists] reads the
     * path, and on Windows a read of a `\\wsl.localhost\<distro>\` root starts the WSL machine
     * of a stopped distribution (IJPL-258774).
     */
    private fun isValid(path: Path?): Boolean {
      if (path == null) return false
      val contributor = model.contributor ?: return Files.exists(path)
      return runCatching { contributor.ownsPath(path) }.getOrDefault(false)
    }

    private fun getRoots(descriptor: FileChooserDescriptor): List<Path>? {
      val list = NioFileChooserUtil.getNioRoots(descriptor).filter { isValid(it) }
      return if (list.isEmpty() && descriptor.isShowFileSystemRoots) null else list
    }
  }

  private open class Node(
    protected val contributor: UniversalFileChooserContributor?,
    path: Path?,
    attrs: BasicFileAttributes? = null,
    isDirectory: Boolean? = null,) : NioFileNode(path) {
    val isDirectory: Boolean = isDirectory ?: resolveIsDirectory(path, attrs)
    private val resolveState = AtomicReference(
      if (path != null && (attrs == null || attrs.isSymbolicLink)) ResolveState.UNRESOLVED else ResolveState.RESOLVED)

    init {
      updateContent(attrs)
    }

    /** Moves the node into the resolving state. Returns false when no resolution is needed or one is in flight. */
    fun startResolve(): Boolean =
      resolveState.compareAndSet(ResolveState.UNRESOLVED, ResolveState.RESOLVING) ||
      resolveState.compareAndSet(ResolveState.FAILED, ResolveState.RESOLVING)

    /**
     * Applies the result of a lazy resolution. A null [resolved] means a timeout or an error.
     * Returns true when the presentation changed.
     */
    fun applyResolved(resolved: Resolved?): Boolean {
      val p = path
      check(p != null)
      if (resolved == null) {
        resolveState.set(ResolveState.FAILED)
        var changed = updateValid(false)
        changed = updateIcon(LayeredIcon.layeredIcon(arrayOf(icon ?: unresolvedIcon(), AllIcons.Nodes.ErrorMark))) || changed
        return changed
      }
      resolveState.set(ResolveState.RESOLVED)
      val isSymlink = resolved.attrs.isSymbolicLink
      var icon: Icon? =
        if (resolved.target.isDirectory) PlatformIcons.FOLDER_ICON
        else FileTypeRegistry.getInstance().getFileTypeByFileName(p.fileName?.toString() ?: p.toString()).icon
      if (isSymlink && icon != null) {
        icon = withSymlinkOverlay(icon)
      }
      var changed = updateIcon(icon)
      changed = updateValid(true) || changed
      changed = updateHidden(NioFileChooserUtil.isHidden(p, resolved.attrs)) || changed
      changed = updateSymlink(isSymlink) || changed
      return changed
    }

    protected open fun updateContent(attrs: BasicFileAttributes?) {
      val p = path
      check(p != null)
      updateName(contributor.fileName(p))
      when {
        attrs != null -> {
          val isSymlink = attrs.isSymbolicLink
          var icon: Icon? = when {
            isSymlink -> AllIcons.FileTypes.Unknown  // the target kind is not known until the lazy resolution reads it
            isDirectory -> PlatformIcons.FOLDER_ICON
            else -> FileTypeRegistry.getInstance().getFileTypeByFileName(p.fileName?.toString() ?: p.toString()).icon
          }
          if (isSymlink && icon != null) {
            icon = withSymlinkOverlay(icon)
          }
          updateIcon(icon)
          updateValid(true)
          updateHidden(NioFileChooserUtil.isHidden(p, attrs))
          updateSymlink(isSymlink)
        }
        p.parent == null -> {
          // a file-system root: no read, because a read of a `\\wsl.localhost\<distro>\` root starts the WSL machine (IJPL-258774)
          updateIcon(PlatformIcons.FOLDER_ICON)
          updateValid(true)
          updateHidden(false)
          updateSymlink(false)
        }
        else -> {
          // an unresolved entry: the attributes did not arrive in time; the lazy resolution fills them in
          updateIcon(unresolvedIcon())
          updateValid(false)
          updateHidden(false)
          updateSymlink(false)
        }
      }
    }

    override fun toString(): String = name ?: ""
  }

  private open class Root(
    state: State,
    contributor: UniversalFileChooserContributor?,
    path: Path?,
    contributorRoot: UniversalFileChooserContributor.Root? = null,
  ) :
    Node(contributor, path) {
    val tree: MapBasedTree<Path, Node> = MapBasedTree(false, { it.path }, state.path)

    init {
      tree.updateRoot(Pair.create(this, !isDirectory))
      if (path?.parent != null) {
        // trust a root without a read; a bad root fails on the listing (see also setRootError)
        updateIcon(PlatformIcons.FOLDER_ICON)
        updateValid(true)
      }
      if (contributorRoot != null) {
        val presentation = contributorRoot.presentation
        presentation.icon?.let { updateIcon(it) }
        updateName(presentation.presentableName)
        updateComment(presentation.comment)
      }
    }

    open fun updateChildren(state: State, parent: Entry<Node>) {
      val listing = state.model.loadChildren(parent.node)
                    ?: return  // the load was cancelled; the entry stays in the loading-required state
      val children = listing.entries
      if (children == null) {
        tree.update(parent, null)
        return
      }
      if (children.isEmpty()) {
        tree.update(parent, emptyList())
        return
      }
      tree.update(parent, children
        .filter { state.isVisible(it) }
        .sortedWith { a, b -> state.compare(a, b) }
        .map { entry ->
          val existing = tree.findEntry(entry.path)
          val node =
            if (existing != null && parent === existing.parentPath) existing.node
            else Node(contributor, entry.path, entry.attrs, entry.isDirectory)
          state.model.scheduleResolve(node, entry.attrs)
          Pair.create(node, !entry.isDirectory)
        })
    }
  }

  private class VirtualRoot(
    state: State,
    contributor: UniversalFileChooserContributor?,
    val contributorRoot: UniversalFileChooserContributor.Root,
  ) : Root(state, contributor, null) {
    init {
      updateName(contributorRoot.presentation.presentableName)
      updateComment(contributorRoot.presentation.comment)
      updateIcon(contributorRoot.presentation.icon ?: EmptyIcon.ICON_16)
      updateValid(true)
    }

    override fun updateContent(attrs: BasicFileAttributes?) {
    }

    override fun updateChildren(state: State, parent: Entry<Node>) {
      tree.update(parent, emptyList())
    }

    override fun toString(): String = name ?: ""
  }
}

/**
 * Resolves a path's display name via the owning contributor.
 */
internal fun UniversalFileChooserContributor?.fileName(path: Path): String =
  this?.getFileName(path) ?: path.fileName?.toString() ?: path.toString()

/** Holds the parent scope of the background jobs of every [NioFileTreeModel]. */
@Service(Service.Level.APP)
private class NioFileTreeModelScopeHolder(@JvmField val scope: CoroutineScope)
