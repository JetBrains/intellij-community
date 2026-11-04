// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.project.Project
import com.intellij.util.messages.SimpleMessageBusConnection
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/**
 * The extensions of a point that apply to a project, see [TechnologyFilter].
 *
 * Most extension points are application-level, and most of their extensions take a [Project] in their methods.
 * Such a consumer gives the project at hand, for example `EP.extensionList(project)` instead of `EP.extensionList`,
 * see [TechnologyAwareExtensionPointName].
 * The consumer of a project-level point uses [TechnologyAwareProjectExtensionPointName].
 *
 * The module of an extension decides if the extension applies, see [TechnologyFilter.isApplicable].
 * Without a filter service, every extension applies to every project.
 *
 * A consumer that caches what it derived from the extensions listens with [TechnologyAwareExtensionPointName.addChangeListener].
 * It recomputes for the project of a change, as it recomputes for a loaded or unloaded plugin.
 */
@ApiStatus.Experimental
sealed class TechnologyAwareExtensions<T : Any>(@JvmField val pointName: BaseExtensionPointName<T>) {
  val name: String
    get() = pointName.name

  /** The point whose extensions can apply to [project]. */
  internal abstract fun point(project: Project): ExtensionPointImpl<T>

  /** The extensions that apply to [project], in the loading order. */
  fun extensionList(project: Project): List<T> {
    if (!TechnologyFilter.getInstance().isRestricted(project)) {
      // every extension applies: the cached list of the point
      return point(project).extensionList
    }
    return lazySequence(project).toList()
  }

  /**
   * The extensions that apply to [project], each created when the iteration reaches it.
   * The sequence skips an extension that does not apply before it creates the extension.
   */
  fun lazySequence(project: Project): Sequence<T> = filterableLazySequence(project).mapNotNull { it.instance }

  /** The extensions that apply to [project], not created yet, see [LazyExtension]. */
  @ApiStatus.Internal
  fun filterableLazySequence(project: Project): Sequence<LazyExtension<T>> {
    val point = point(project)
    val filter = TechnologyFilter.getInstance()
    if (!filter.isRestricted(project)) {
      return point.filterableLazySequence()
    }
    return point.filterableLazySequence().filter { filter.isApplicable(it.pluginDescriptor, project) }
  }

  /** Runs [consumer] for each extension that applies to [project]. The exception of one extension is logged, and the others still run. */
  fun forEachExtensionSafe(project: Project, consumer: Consumer<in T>) {
    for (extension in filterableLazySequence(project)) {
      val instance = extension.instance ?: continue
      try {
        consumer.accept(instance)
      }
      catch (e: Throwable) {
        logOrRethrow(e, extension)
      }
    }
  }

  /** The first extension that applies to [project] and that [predicate] accepts. The exception of an extension is logged and skipped. */
  fun findFirstSafe(project: Project, predicate: Predicate<in T>): T? {
    return computeSafeIfAny(project) { if (predicate.test(it)) it else null }
  }

  /** The first non-null result of [processor] over the extensions that apply to [project]. The exception of an extension is logged and skipped. */
  fun <R : Any> computeSafeIfAny(project: Project, processor: Function<in T, out R?>): R? {
    for (extension in filterableLazySequence(project)) {
      val instance = extension.instance ?: continue
      try {
        processor.apply(instance)?.let { return it }
      }
      catch (e: Throwable) {
        logOrRethrow(e, extension)
      }
    }
    return null
  }

  /**
   * Subscribes [listener] to the changes of [TechnologyFilter.TOPIC].
   * With a [project], the listener gets only the changes of that project.
   */
  internal fun subscribeToFilterChanges(connection: SimpleMessageBusConnection, project: Project?, listener: ApplicableExtensionsListener) {
    connection.subscribe(TechnologyFilter.TOPIC, TechnologyFilterListener { changed ->
      if (project == null || changed === project) {
        listener.applicableExtensionsChanged(changed)
      }
    })
  }

  private fun logOrRethrow(e: Throwable, extension: LazyExtension<T>) {
    rethrowControlFlowException(e)
    LOG.error(ApplicationManager.getApplication().createError(e, extension.pluginDescriptor.pluginId))
  }

  override fun toString(): String = "${javaClass.simpleName}($name)"

  /** A listener of the changes of the extensions that apply to a project, see [TechnologyAwareExtensionPointName.addChangeListener]. */
  fun interface ApplicableExtensionsListener {
    /**
     * The extensions of the point that apply to [project] can be different now, because the technologies of the project changed.
     * When [project] is `null`, a plugin load or unload changed the extensions of the point, and every project is affected.
     */
    fun applicableExtensionsChanged(project: Project?)
  }

  private companion object {
    val LOG = logger<TechnologyAwareExtensions<*>>()
  }
}

/** The extensions of an application-level point that apply to a project, see [TechnologyAwareExtensions]. */
@ApiStatus.Experimental
class TechnologyAwareExtensionPointName<T : Any>(pointName: ExtensionPointName<T>) : TechnologyAwareExtensions<T>(pointName) {
  constructor(name: @NonNls String) : this(ExtensionPointName<T>(name))

  private val rootPoint: ExtensionPointImpl<T>
    get() = (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl).getExtensionPoint(name)

  override fun point(project: Project): ExtensionPointImpl<T> = rootPoint

  @ApiStatus.Internal
  fun unfilteredLazySequence(): Sequence<LazyExtension<T>> {
    return rootPoint.filterableLazySequence()
  }

  /**
   * Calls [listener] when the extensions that apply to a project can be different, for one of two causes.
   * A change of [TechnologyFilter.TOPIC] gives the project of the change.
   * A plugin load or unload that changed the extensions of the point gives `null`, because every project is affected.
   *
   * The listener does not get the extensions that changed. It compares a new [extensionList] with the state that it keeps.
   * So the result stays correct when the events come together or late.
   * A removal that must dispose an instance while its plugin is still loaded uses [ExtensionPointListener.extensionRemoved].
   *
   * The listener runs on the thread of the change. For a technology change, this can be a background thread.
   */
  fun addChangeListener(coroutineScope: CoroutineScope, listener: ApplicableExtensionsListener) {
    subscribeToFilterChanges(ApplicationManager.getApplication().messageBus.connect(coroutineScope), project = null, listener = listener)
    rootPoint.addChangeListener(coroutineScope) { listener.applicableExtensionsChanged(null) }
  }

  /** The same as [addChangeListener] with a coroutine scope, for the code that [parentDisposable] disposes. */
  fun addChangeListener(parentDisposable: Disposable, listener: ApplicableExtensionsListener) {
    subscribeToFilterChanges(ApplicationManager.getApplication().messageBus.connect(parentDisposable), project = null, listener = listener)
    rootPoint.addChangeListener({ listener.applicableExtensionsChanged(null) }, parentDisposable)
  }
}

/** The extensions of a project-level point that apply to its project, see [TechnologyAwareExtensions]. */
@ApiStatus.Experimental
class TechnologyAwareProjectExtensionPointName<T : Any>(pointName: ProjectExtensionPointName<T>) : TechnologyAwareExtensions<T>(pointName) {
  constructor(name: @NonNls String) : this(ProjectExtensionPointName<T>(name))

  override fun point(project: Project): ExtensionPointImpl<T> {
    return (project.extensionArea as ExtensionsAreaImpl).getExtensionPoint(name)
  }

  @ApiStatus.Internal
  fun unfilteredLazySequence(project: Project): Sequence<LazyExtension<T>> {
    return point(project).filterableLazySequence()
  }

  /**
   * Calls [listener] when the extensions of the point of [project] that apply to it can be different.
   * The causes are a technology change of the project, or a plugin load or unload that changed the extensions of its point.
   * The listener always gets [project]. The rest of the contract is the one of [TechnologyAwareExtensionPointName.addChangeListener].
   */
  fun addChangeListener(project: Project, coroutineScope: CoroutineScope, listener: ApplicableExtensionsListener) {
    subscribeToFilterChanges(ApplicationManager.getApplication().messageBus.connect(coroutineScope), project, listener)
    point(project).addChangeListener(coroutineScope) { listener.applicableExtensionsChanged(project) }
  }

  /** The same as [addChangeListener] with a coroutine scope, for the code that [parentDisposable] disposes. */
  fun addChangeListener(project: Project, parentDisposable: Disposable, listener: ApplicableExtensionsListener) {
    subscribeToFilterChanges(ApplicationManager.getApplication().messageBus.connect(parentDisposable), project, listener)
    point(project).addChangeListener({ listener.applicableExtensionsChanged(project) }, parentDisposable)
  }
}
