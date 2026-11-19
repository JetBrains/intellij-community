package com.intellij.database.datagrid

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import java.util.function.Function

/**
 * Registers the hookups of a project, so that grids and editors can share one through its [GridHookUpId].
 * A hookup stays registered while a parent or an [EditorOwner] holds it, and a [Disposable] hookup is disposed with the last one.
 * It also stays when an editor says, through its [EditorOwner], that it closes to reopen.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GridHookUpRegistry : Disposable {
  private val lock = Any()
  private val registryId = UUID.randomUUID().toString()

  /** Guarded by [lock]. */
  private val references = HashMap<GridHookUpId, HookUpReference>()

  /** Guarded by [lock]. */
  private var lastSequence = 0L

  /** The hookups that a parent or an owner holds; a kept hookup isn't one of them. */
  val hookUps: List<GridDataHookUp<GridRow, GridColumn>>
    get() = synchronized(lock) { references.values.filter { it.referenceCount > 0 }.map { it.hookUp } }

  /**
   * Registers [hookUp] under [parent] and returns it.
   * Register a hookup once, and share it through [acquire].
   * A second registration gives it a second id and ties its disposal to the second parent.
   */
  fun <T : GridDataHookUp<GridRow, GridColumn>> registerHookUp(hookUp: T, parent: Disposable): T {
    synchronized(lock) {
      addReference(newReference(hookUp, null), parent)
    }
    return hookUp
  }

  /** Returns the id of [hookUp], or null if it isn't registered. */
  fun getId(hookUp: GridDataHookUp<GridRow, GridColumn>): GridHookUpId? =
    synchronized(lock) { references.values.find { it.hookUp === hookUp }?.id }

  /** Adds [parent] to the holders of the hookup of [id] and returns it, or returns null if nothing holds it anymore. */
  fun acquire(id: GridHookUpId, parent: Disposable): GridDataHookUp<GridRow, GridColumn>? {
    synchronized(lock) {
      val reference = references[id]
      if (reference == null || reference.referenceCount <= 0) return null
      addReference(reference, parent)
      return reference.hookUp
    }
  }

  /**
   * Returns a new owner of the hookup of [id], or null if the hookup was disposed.
   * Unlike [acquire], it also gets a hookup that an editor kept when it closed to reopen.
   */
  fun acquireForEditor(id: GridHookUpId): EditorOwner<GridDataHookUp<GridRow, GridColumn>>? {
    synchronized(lock) {
      val reference = references[id]
      if (reference == null || reference.referenceCount <= 0 && !reference.isKeptForReopen) return null
      return newOwner(reference, reference.hookUp)
    }
  }

  /**
   * Returns a new owner of the hookup of [hookUpClass] that an editor of [file] kept to reopen.
   * Without one, it registers a hookup from [hookUpFactory].
   */
  fun <F : VirtualFile, H : GridDataHookUp<GridRow, GridColumn>> acquireForEditor(
    file: F,
    hookUpClass: Class<H>,
    hookUpFactory: Function<F, H>,
  ): EditorOwner<H> {
    synchronized(lock) {
      val kept = references.values.find {
        it.isKeptForReopen && it.reuseClass == hookUpClass && file == (it.hookUp as? HookUpVirtualFileProvider)?.virtualFile
      }
      val reference = kept ?: newReference(hookUpFactory.apply(file), hookUpClass)
      return newOwner(reference, hookUpClass.cast(reference.hookUp))
    }
  }

  /** Disposes the hookups kept for a reopen. An editor that is still open, such as one in a detached window, releases its own later. */
  override fun dispose() {
    val kept = synchronized(lock) {
      references.values.filter { it.isKeptForReopen }.onEach { references.remove(it.id) }
    }
    kept.forEach(Disposer::dispose)
  }

  /** Call under [lock]. */
  private fun newReference(hookUp: GridDataHookUp<GridRow, GridColumn>, reuseClass: Class<*>?): HookUpReference {
    val reference = HookUpReference(GridHookUpId("$registryId-${++lastSequence}"), hookUp, reuseClass)
    references[reference.id] = reference
    return reference
  }

  /** Call under [lock]. */
  private fun <H : GridDataHookUp<GridRow, GridColumn>> newOwner(reference: HookUpReference, hookUp: H): EditorOwner<H> {
    reference.isKeptForReopen = false
    reference.referenceCount++
    return EditorOwner(reference, hookUp)
  }

  /** Call under [lock]. */
  private fun addReference(reference: HookUpReference, parent: Disposable) {
    reference.referenceCount++
    Disposer.register(parent) { releaseReference(reference, false) }
  }

  private fun releaseReference(reference: HookUpReference, keepForReopen: Boolean) {
    synchronized(lock) {
      if (--reference.referenceCount > 0) return
      if (keepForReopen) {
        reference.isKeptForReopen = true
      }
      else {
        Disposer.dispose(reference)
        references.remove(reference.id)
      }
    }
  }

  /**
   * An editor's reference to [hookUp]. With `keepForReopen`, [release] keeps the hookup for a tab move.
   * The editor calls [release] first in its own `dispose()`, because only the editor knows whether it closes to reopen.
   * The owner isn't a Disposer child of the editor: children are disposed before the editor's `dispose()`, without the keep request.
   * Nor is it a child of the registry: closing a project closes only the focused window's tabs before the registry is disposed.
   */
  inner class EditorOwner<out H : GridDataHookUp<GridRow, GridColumn>> internal constructor(
    reference: HookUpReference,
    val hookUp: H,
  ) {
    /** Guarded by [lock]; null once released. */
    private var reference: HookUpReference? = reference

    /**
     * Releases the reference of this owner.
     * With [keepForReopen], a hookup whose last reference this was stays registered until an editor acquires it again.
     * The project's close disposes it otherwise.
     * The flag applies to this reference only: a hookup that a plain parent still holds is disposed with that parent.
     * A second release does nothing.
     */
    fun release(keepForReopen: Boolean) {
      val held = synchronized(lock) { reference.also { reference = null } } ?: return
      releaseReference(held, keepForReopen)
    }
  }

  /** The Disposer parent of a [Disposable] hookup, so that a release disposes any hookup through one call. */
  internal class HookUpReference(
    val id: GridHookUpId,
    val hookUp: GridDataHookUp<GridRow, GridColumn>,
    val reuseClass: Class<*>?,
  ) : Disposable {
    /** Guarded by the registry's lock. */
    var referenceCount = 0

    /** Guarded by the registry's lock. */
    var isKeptForReopen = false

    init {
      if (hookUp is Disposable) {
        Disposer.register(this, hookUp)
      }
    }

    override fun dispose() {
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GridHookUpRegistry = project.service()
  }
}
