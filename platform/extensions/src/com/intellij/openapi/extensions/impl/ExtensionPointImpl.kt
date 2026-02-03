// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "OVERRIDE_DEPRECATION", "LoggingSimilarMessage")

package com.intellij.openapi.extensions.impl

import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.Java11Shim
import com.intellij.util.ThreeState
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.function.BiPredicate
import java.util.function.Function
import java.util.function.Predicate
import kotlin.concurrent.Volatile

private val LOG: Logger = logger<ExtensionPointImpl<*>>()

@ApiStatus.Internal
sealed class ExtensionPointImpl<T : Any>(@JvmField val name: String,
                                         @JvmField val className: String,
                                         private val extensionPointPluginDescriptor: PluginDescriptor,
                                         @JvmField val componentManager: ComponentManager,
                                         private var extensionClass: Class<T>?,
                                         private val isDynamic: Boolean) : ExtensionPoint<T>, Sequence<T> {
  @Volatile
  private var cachedExtensions: List<T>? = null

  @Volatile
  private var cachedExtensionsAsArray: Array<T>? = null

  @Volatile
  private var adapters: List<ExtensionComponentAdapter> = Java11Shim.INSTANCE.listOf()

  @Volatile
  private var adaptersAreSorted = true

  @Volatile
  private var listeners = persistentListOf<ExtensionPointListener<T>>()

  @Volatile
  private var keyMapperToCache: ConcurrentMap<*, Map<*, *>>? = null

  companion object {
    // XmlExtensionAdapter.createInstance takes a lock on itself.
    // If it's called without EP lock and tries to add an EP listener, we can get a deadlock because of lock ordering violation
    // (EP->adapter in one thread, adapter->EP in the other thread).
    // Could happen if an extension constructor calls addExtensionPointListener on EP.
    // So, updating of listeners is a lock-free as a solution.
    private val listenerUpdater =
      AtomicReferenceFieldUpdater.newUpdater(ExtensionPointImpl::class.java, PersistentList::class.java, "listeners")
    private val keyMapperToCacheUpdater =
      AtomicReferenceFieldUpdater.newUpdater(ExtensionPointImpl::class.java, ConcurrentMap::class.java, "keyMapperToCache")

    fun setCheckCanceledAction(checkCanceled: Runnable) {
      CHECK_CANCELED = {
        try {
          checkCanceled.run()
        }
        catch (e: ProcessCanceledException) {
          // otherwise ExceptionInInitializerError happens and the class is screwed forever
          if (!isInsideClassInitializer(e.stackTrace)) {
            throw e
          }
        }
      }
    }
  }

  fun <CACHE_KEY : Any, V : Any?> getCacheMap(): ConcurrentMap<CACHE_KEY, V> {
    @Suppress("UNCHECKED_CAST")
    return (keyMapperToCache ?: keyMapperToCacheUpdater.updateAndGet(this) { ConcurrentHashMap<Any, Map<*, *>>() })
      as ConcurrentMap<CACHE_KEY, V>
  }

  final override fun isDynamic(): Boolean = isDynamic

  final override fun registerExtension(extension: T) {
    doRegisterExtension(extension = extension,
                        order = LoadingOrder.ANY,
                        pluginDescriptor = extensionPointPluginDescriptor,
                        parentDisposable = null)
  }

  final override fun registerExtension(extension: T, parentDisposable: Disposable) {
    registerExtension(extension = extension, pluginDescriptor = extensionPointPluginDescriptor, parentDisposable = parentDisposable)
  }

  final override fun registerExtension(extension: T, pluginDescriptor: PluginDescriptor, parentDisposable: Disposable) {
    doRegisterExtension(extension = extension,
                        order = LoadingOrder.ANY,
                        pluginDescriptor = pluginDescriptor,
                        parentDisposable = parentDisposable)
  }

  final override fun getPluginDescriptor(): PluginDescriptor = extensionPointPluginDescriptor

  final override fun registerExtension(extension: T, order: LoadingOrder, parentDisposable: Disposable) {
    doRegisterExtension(extension = extension, order = order, pluginDescriptor = getPluginDescriptor(), parentDisposable = parentDisposable)
  }

  private fun doRegisterExtension(extension: T,
                                  order: LoadingOrder,
                                  pluginDescriptor: PluginDescriptor,
                                  parentDisposable: Disposable?) {
    checkExtensionType(extension = extension, extensionClass = getExtensionClass(), adapter = null)

    val adapter = ObjectComponentAdapter(instance = extension, pluginDescriptor = pluginDescriptor, loadingOrder = order)
    synchronized(this) {
      for (a in adapters) {
        if (a is ObjectComponentAdapter<*> && a.instance === extension) {
          LOG.error("Extension was already added: $extension")
          return
        }
      }

      assertNotReadOnlyMode()
      addExtensionAdapter(adapter)
    }

    notifyListeners(isRemoved = false, adapters = Java11Shim.INSTANCE.listOf(adapter), listeners = listeners)

    if (parentDisposable != null) {
      Disposer.register(parentDisposable) {
        synchronized(this) {
          // index by identity
          val index = adapters.indexOfFirst { it === adapter }
          if (index < 0) {
            LOG.error("Extension to be removed not found: ${adapter.instance}")
          }

          adapters = mutateAdapters(adapters) { it.removeAt(index) }
          clearCache()
        }
        notifyListeners(isRemoved = true, adapters = Java11Shim.INSTANCE.listOf(adapter), listeners = listeners)
      }
    }
  }

  /**
   * There are valid cases where we need to register a lot of extensions programmatically,
   * e.g., see SqlDialectTemplateRegistrar, so, special method for bulk insertion is introduced.
   */
  fun registerExtensions(extensions: List<T>) {
    for (adapter in adapters) {
      if (adapter is ObjectComponentAdapter<*>) {
        if (extensions.any { it === adapter }) {
          LOG.error("Extension was already added: ${adapter.instance}")
          return
        }
      }
    }

    val newAdapters = doRegisterExtensions(extensions)
    // do not call notifyListeners under lock
    notifyListeners(isRemoved = false, adapters = newAdapters, listeners = listeners)
  }

  private inline fun mutateAdapters(
    l: List<ExtensionComponentAdapter>,
    operation: (l: MutableList<ExtensionComponentAdapter>) -> Unit,
  ): List<ExtensionComponentAdapter> {
    val result = l.toMutableList()
    operation(result)
    return Java11Shim.INSTANCE.copyOfList(result)
  }

  @Synchronized
  private fun doRegisterExtensions(extensions: List<T>): List<ExtensionComponentAdapter> {
    val newAdapters = extensions.map {
      ObjectComponentAdapter(instance = it, pluginDescriptor = getPluginDescriptor(), loadingOrder = LoadingOrder.ANY)
    }

    val oldAdapters = adapters
    adapters = mutateAdapters(oldAdapters) { it.addAll(findInsertionIndexForAnyOrder(oldAdapters), newAdapters) }
    clearCache()
    return newAdapters
  }

  private fun checkExtensionType(extension: T, extensionClass: Class<T>, adapter: ExtensionComponentAdapter?) {
    if (extensionClass.isInstance(extension)) {
      return
    }

    var message: @NonNls String = "Extension ${extension.javaClass.getName()} does not implement $extensionClass"
    if (adapter == null) {
      throw RuntimeException(message)
    }
    else {
      message += " (adapter=$adapter)"
      throw componentManager.createError(/* message = */ message,
                                         /* error = */ null,
                                         /* pluginId = */ adapter.pluginDescriptor.pluginId,
                                         /* attachments = */ Java11Shim.INSTANCE.mapOf("threadDump", ThreadDumper.dumpThreadsToString()))
    }
  }

  final override fun getExtensionList(): List<T> {
    var result = cachedExtensions
    if (result == null) {
      synchronized(this) {
        result = cachedExtensions
        if (result == null) {
          result = createExtensionInstances()
          cachedExtensions = result
          cachedExtensionsAsArray = null
        }
      }
    }
    return result!!
  }

  final override fun getExtensions(): Array<T> {
    var array = cachedExtensionsAsArray
    if (array == null) {
      synchronized(this) {
        array = cachedExtensionsAsArray
        if (array == null) {
          val list = extensionList
          @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
          array = (list as java.util.List<T>).toArray(java.lang.reflect.Array.newInstance(getExtensionClass(), list.size) as Array<T>)
          cachedExtensionsAsArray = array
        }
      }
    }
    return array!!.clone()
  }

  /**
   * Do not use it if there is any extension point listener, because in this case behavior is not predictable:
   * events will be fired during iteration, which is probably not expected.
   *
   * Use only for interface extension points, not for beans.
   */
  fun asSequence(): Sequence<T> = cachedExtensions?.asSequence() ?: this

  final override fun iterator(): Iterator<T> {
    cachedExtensions?.let {
      return it.iterator()
    }

    val size: Int
    val adapters = sortedAdapters
    size = adapters.size
    if (size == 0) {
      return Collections.emptyIterator()
    }

    return object : Iterator<T> {
      private var currentIndex = 0
      private var nextItem: T? = null

      override fun hasNext(): Boolean {
        if (nextItem == null) {
          advanceToNextNonNull()
        }
        return nextItem != null
      }

      override fun next(): T {
        if (nextItem == null) {
          advanceToNextNonNull()
        }
        val result = nextItem ?: throw NoSuchElementException("No more elements.")
        nextItem = null
        return result
      }

      private fun advanceToNextNonNull() {
        nextItem = null

        while (currentIndex < size && nextItem == null) {
          nextItem = getOrCreateExtensionInstance(adapters.get(currentIndex++), componentManager)
        }
      }
    }
  }

  final override fun size(): Int = adapters.size

  fun processWithPluginDescriptor(consumer: (T, PluginDescriptor) -> Unit) {
    processWithPluginDescriptor(shouldBeSorted = true, consumer)
  }

  fun processUnsortedWithPluginDescriptor(consumer: (T, PluginDescriptor) -> Unit) {
    processWithPluginDescriptor(shouldBeSorted = false, consumer)
  }

  internal inline fun processWithPluginDescriptor(shouldBeSorted: Boolean, consumer: (T, PluginDescriptor) -> Unit) {
    for (adapter in if (shouldBeSorted) sortedAdapters else adapters) {
      try {
        val extension = adapter.createInstance<T>(componentManager) ?: continue
        consumer(extension, adapter.pluginDescriptor)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(componentManager.createError(e, adapter.pluginDescriptor.pluginId))
      }
    }
  }

  @TestOnly
  fun checkImplementations(consumer: (ExtensionComponentAdapter) -> Unit) {
    for (adapter in sortedAdapters) {
      consumer(adapter)
    }
  }

  val sortedAdapters: List<ExtensionComponentAdapter>
    get() {
      if (adaptersAreSorted) {
        return adapters
      }

      synchronized(this) {
        if (adaptersAreSorted) {
          return adapters
        }

        if (adapters.size > 1) {
          adapters = mutateAdapters(adapters) { LoadingOrder.sortByLoadingOrder(it) }
        }
        adaptersAreSorted = true
      }
      return adapters
    }

  private fun createExtensionInstances(): List<T> {
    assertNotReadOnlyMode()

    // check before to avoid any "restore" work if already canceled
    CHECK_CANCELED?.invoke()

    val adapters = sortedAdapters
    val totalSize = adapters.size
    val extensionClass = getExtensionClass()
    if (totalSize == 0) {
      return Java11Shim.INSTANCE.listOf()
    }
    else if (totalSize == 1) {
      val extension = processAdapter(adapter = adapters.get(0),
                                     listeners = listeners,
                                     result = null,
                                     duplicates = null,
                                     extensionClassForCheck = extensionClass,
                                     adapters = adapters) ?: return Java11Shim.INSTANCE.listOf()
      return Java11Shim.INSTANCE.listOf(extension)
    }

    val duplicates = if (this is BeanExtensionPoint<*>) null else Collections.newSetFromMap<T>(IdentityHashMap(totalSize))
    val listeners = listeners

    val result = arrayOfNulls<Any>(totalSize)
    var index = 0
    for (adapter in adapters) {
      val extension = processAdapter(adapter = adapter,
                                     listeners = listeners,
                                     result = result,
                                     duplicates = duplicates,
                                     extensionClassForCheck = extensionClass,
                                     adapters = adapters)
      if (extension != null) {
        result[index++] = extension
      }
    }
    @Suppress("UNCHECKED_CAST")
    return Java11Shim.INSTANCE.listOf(result, index) as List<T>
  }

  private fun processAdapter(adapter: ExtensionComponentAdapter,
                             listeners: List<ExtensionPointListener<T>>?,
                             result: Array<*>?,
                             duplicates: MutableSet<T>?,
                             extensionClassForCheck: Class<T>,
                             adapters: List<ExtensionComponentAdapter>): T? {
    try {
      if (!checkThatClassloaderIsActive(adapter)) {
        return null
      }

      val isNotifyThatAdded = !listeners.isNullOrEmpty() && !adapter.isInstanceCreated && !isDynamic
      // do not call CHECK_CANCELED here in loop because it is called by createInstance()
      val extension = adapter.createInstance<T>(componentManager)
      if (extension == null) {
        LOG.debug { "$adapter not loaded because it reported that not applicable" }
        return null
      }

      if (duplicates != null && !duplicates.add(extension)) {
        var duplicate: T? = null
        for (value in duplicates) {
          if (value == extension) {
            duplicate = value
            break
          }
        }
        LOG.error("""Duplicate extension found:
                   $extension;
  prev extension:  $duplicate;
  adapter:         $adapter;
  extension class: $extensionClassForCheck;
  result:          $result;
  adapters:        $adapters"""
        )
      }
      else {
        checkExtensionType(extension = extension, extensionClass = extensionClassForCheck, adapter = adapter)
        if (isNotifyThatAdded) {
          notifyListeners(isRemoved = false, adapters = listOf(adapter), listeners = listeners ?: emptyList())
        }
        return extension
      }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    return null
  }

  /**
   * Put extension point in read-only mode and replace existing extensions by supplied.
   * For tests this method is more preferable than [registerExtension] because makes registration more isolated and strict
   * (no one can modify extension point until `parentDisposable` is not disposed).
   *
   *
   * Please use [com.intellij.testFramework.ExtensionTestUtil.maskExtensions]
   * instead of direct usage.
   */
  @TestOnly
  @ApiStatus.Internal
  @Synchronized
  fun maskAll(newList: List<T>, parentDisposable: Disposable, fireEvents: Boolean) {
    if (POINTS_IN_READONLY_MODE == null) {
      POINTS_IN_READONLY_MODE = Collections.newSetFromMap(IdentityHashMap())
    }
    else {
      assertNotReadOnlyMode()
    }

    val oldList = cachedExtensions
    val oldAdapters = adapters
    val oldAdaptersAreSorted = adaptersAreSorted

    cachedExtensions = Java11Shim.INSTANCE.copyOfList(newList)
    cachedExtensionsAsArray = null
    if (newList.isEmpty()) {
      adapters = Java11Shim.INSTANCE.listOf()
    }
    else {
      adapters = Java11Shim.INSTANCE.copyOfList(newList.map {
        ObjectComponentAdapter(instance = it, pluginDescriptor = extensionPointPluginDescriptor, loadingOrder = LoadingOrder.ANY)
      })
    }
    adaptersAreSorted = true

    POINTS_IN_READONLY_MODE!!.add(this)

    if (fireEvents) {
      val listeners = listeners
      if (!listeners.isEmpty()) {
        if (oldList != null) {
          doNotifyListeners(isRemoved = true, extensions = oldList, listeners = listeners)
        }
        doNotifyListeners(isRemoved = false, extensions = newList, listeners = listeners)
      }
    }

    clearUserCache()

    Disposer.register(parentDisposable) {
      synchronized(this@ExtensionPointImpl) {
        POINTS_IN_READONLY_MODE!!.remove(this@ExtensionPointImpl)
        cachedExtensions = oldList
        cachedExtensionsAsArray = null
        adapters = oldAdapters
        adaptersAreSorted = oldAdaptersAreSorted

        val listeners = listeners
        if (fireEvents && !listeners.isEmpty()) {
          doNotifyListeners(isRemoved = true, extensions = newList, listeners = listeners)
          if (oldList != null) {
            doNotifyListeners(isRemoved = false, extensions = oldList, listeners = listeners)
          }
        }
        clearUserCache()
      }
    }
  }

  @TestOnly
  private fun doNotifyListeners(isRemoved: Boolean, extensions: List<T>, listeners: List<ExtensionPointListener<T>>) {
    for (listener in listeners) {
      if (listener is ExtensionPointAdapter<*>) {
        try {
          listener.extensionListChanged()
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
      else {
        for (extension in extensions) {
          try {
            if (isRemoved) {
              listener.extensionRemoved(extension, extensionPointPluginDescriptor)
            }
            else {
              listener.extensionAdded(extension, extensionPointPluginDescriptor)
            }
          }
          catch (e: ProcessCanceledException) {
            throw e
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
      }
    }
  }

  @Synchronized
  final override fun unregisterExtension(extension: T) {
    if (!unregisterExtensions(
        extensionClassNameFilter = {
                                   _, adapter -> !adapter.isInstanceCreated || adapter.createInstance<Any>(componentManager) !== extension
        },
        stopAfterFirstMatch = true)) {
      // there is a possible case that a particular extension was replaced in a particular environment
      // (e.g., Upsource replaces some platform extensions important for CoreApplicationEnvironment),
      // so log an error instead of throwing
      LOG.warn("Extension to be removed not found: $extension")
    }
  }

  final override fun unregisterExtension(extensionClass: Class<out T>) {
    val classNameToUnregister = extensionClass.canonicalName
    if (!unregisterExtensions({ aClass, _ -> aClass != classNameToUnregister }, /* stopAfterFirstMatch = */true)) {
      LOG.warn("Extension to be removed not found: $extensionClass")
    }
  }

  final override fun unregisterExtensions(extensionClassNameFilter: BiPredicate<String, ExtensionComponentAdapter>,
                                    stopAfterFirstMatch: Boolean): Boolean {
    val listenerCallbacks = ArrayList<Runnable>()
    val priorityListenerCallbacks = ArrayList<Runnable>()
    val result = unregisterExtensions(stopAfterFirstMatch = stopAfterFirstMatch,
                                      priorityListenerCallbacks = priorityListenerCallbacks,
                                      listenerCallbacks = listenerCallbacks
    ) { adapter -> extensionClassNameFilter.test(adapter.assignableToClassName, adapter) }
    for (callback in priorityListenerCallbacks) {
      callback.run()
    }
    for (callback in listenerCallbacks) {
      callback.run()
    }
    return result
  }

  /**
   * Unregisters extensions for which the specified predicate returns false
   * and collects the runnables for listener invocation into the given list so that listeners can be called later.
   */
  @Synchronized
  fun unregisterExtensions(stopAfterFirstMatch: Boolean,
                           priorityListenerCallbacks: MutableList<in Runnable>,
                           listenerCallbacks: MutableList<in Runnable>,
                           extensionToKeepFilter: Predicate<in ExtensionComponentAdapter>): Boolean {
    val listeners = listeners
    var removedAdapters = persistentListOf<ExtensionComponentAdapter>()
    var adapters = adapters
    for (i in adapters.indices.reversed()) {
      val adapter = adapters[i]
      if (extensionToKeepFilter.test(adapter)) {
        continue
      }
      adapters = mutateAdapters(adapters) { it.removeAt(i) }

      if (!listeners.isEmpty()) {
        removedAdapters = removedAdapters.add(adapter)
      }

      if (stopAfterFirstMatch) {
        break
      }
    }

    if (adapters === this.adapters) {
      return false
    }

    clearCache()
    this.adapters = adapters

    if (removedAdapters.isEmpty()) {
      return true
    }

    val priorityListeners = listeners.filter { it is ExtensionPointPriorityListener }
    val regularListeners = listeners.filter { it !is ExtensionPointPriorityListener }

    if (!priorityListeners.isEmpty()) {
      priorityListenerCallbacks.add { notifyListeners(isRemoved = true, adapters = removedAdapters, listeners = priorityListeners) }
    }
    if (!regularListeners.isEmpty()) {
      listenerCallbacks.add { notifyListeners(isRemoved = true, adapters = removedAdapters, listeners = regularListeners) }
    }
    return true
  }

  abstract fun unregisterExtensions(componentManager: ComponentManager,
                                    pluginDescriptor: PluginDescriptor,
                                    priorityListenerCallbacks: MutableList<in Runnable>,
                                    listenerCallbacks: MutableList<in Runnable>)

  private fun notifyListeners(isRemoved: Boolean,
                              adapters: List<ExtensionComponentAdapter>,
                              listeners: List<ExtensionPointListener<T>>) {
    for (listener in listeners) {
      if (listener is ExtensionPointAdapter<*>) {
        try {
          (listener as ExtensionPointAdapter<T>).extensionListChanged()
        }
        catch (ce: CancellationException) {
          LOG.warn("Cancellation while notifying `${listener}`", ce)
        }
        catch (e: Throwable) {
          LOG.error("Exception while notifying `$listener`", e)
        }
      }
      else {
        for (adapter in adapters) {
          if (isRemoved && !adapter.isInstanceCreated) {
            continue
          }

          try {
            val extension = adapter.createInstance<T>(componentManager)
            if (extension != null) {
              if (isRemoved) {
                listener.extensionRemoved(extension, adapter.pluginDescriptor)
              }
              else {
                listener.extensionAdded(extension, adapter.pluginDescriptor)
              }
            }
          }
          catch (ce: CancellationException) {
            LOG.warn("Cancellation while notifying `$listener`", ce)
          }
          catch (e: Throwable) {
            LOG.error("Exception while notifying `$listener`", e)
          }
        }
      }
    }
  }

  final override fun addExtensionPointListener(
    listener: ExtensionPointListener<T>,
    invokeForLoadedExtensions: Boolean,
    parentDisposable: Disposable?,
  ) {
    if (!addListener(listener)) {
      return
    }

    if (invokeForLoadedExtensions) {
      notifyListeners(isRemoved = false, adapters = sortedAdapters, listeners = listOf(listener))
    }

    if (parentDisposable != null) {
      Disposer.register(parentDisposable) { removeExtensionPointListener(listener) }
    }
  }

  final override fun addExtensionPointListener(
    coroutineScope: CoroutineScope,
    invokeForLoadedExtensions: Boolean,
    listener: ExtensionPointListener<T>,
  ) {
    if (!addListener(listener)) {
      return
    }

    if (invokeForLoadedExtensions) {
      notifyListeners(isRemoved = false, adapters = sortedAdapters, listeners = listOf(listener))
    }

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      removeExtensionPointListener(listener)
    }
  }

  // true if added
  private fun addListener(listener: ExtensionPointListener<T>): Boolean {
    if (listeners.contains(listener)) {
      return false
    }

    listenerUpdater.updateAndGet(this) {
      @Suppress("UNCHECKED_CAST")
      val list = it as PersistentList<ExtensionPointListener<T>>
      if (listener is ExtensionPointPriorityListener) list.add(0, listener) else list.add(listener)
    }
    return true
  }

  final override fun addChangeListener(coroutineScope: CoroutineScope, listener: Runnable) {
    val listenerAdapter = doAddChangeListener(listener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      removeExtensionPointListener(listenerAdapter)
    }
  }

  final override fun addChangeListener(listener: Runnable, parentDisposable: Disposable?) {
    val listenerAdapter = doAddChangeListener(listener)
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) { removeExtensionPointListener(listenerAdapter) }
    }
  }

  private fun doAddChangeListener(listener: Runnable): ExtensionPointAdapter<T> {
    val listenerAdapter = object : ExtensionPointAdapter<T>() {
      override fun extensionListChanged() {
        listener.run()
      }
    }

    listenerUpdater.updateAndGet(this) {
      @Suppress("UNCHECKED_CAST")
      (it as PersistentList<ExtensionPointListener<T>>).add(listenerAdapter)
    }
    return listenerAdapter
  }

  final override fun removeExtensionPointListener(listener: ExtensionPointListener<T>) {
    listenerUpdater.updateAndGet(this) {
      @Suppress("UNCHECKED_CAST")
      (it as PersistentList<ExtensionPointListener<T>>).remove(listener)
    }
  }

  @Synchronized
  fun reset() {
    val adapters = this.adapters
    this.adapters = Java11Shim.INSTANCE.listOf()
    // clear cache before notifying listeners to ensure that listeners don't get outdated data
    clearCache()
    if (!adapters.isEmpty()) {
      notifyListeners(isRemoved = true, adapters = adapters, listeners = listeners)
    }

    // help GC
    listenerUpdater.updateAndGet(this) { it.clear() }
    extensionClass = null
  }

  fun getExtensionClass(): Class<T> {
    var extensionClass = this.extensionClass
    if (extensionClass == null) {
      try {
        extensionClass = componentManager.loadClass(className, extensionPointPluginDescriptor)
        this.extensionClass = extensionClass
      }
      catch (e: ClassNotFoundException) {
        throw componentManager.createError(e, extensionPointPluginDescriptor.pluginId)
      }
    }
    return extensionClass
  }

  final override fun toString(): String = name

  // private, internal only for tests
  @Synchronized
  @VisibleForTesting
  fun addExtensionAdapter(adapter: ExtensionComponentAdapter) {
    adapters = mutateAdapters(adapters) { it.add(adapter) }
    clearCache()
  }

  fun clearUserCache() {
    keyMapperToCache?.clear()
  }

  private fun clearCache() {
    cachedExtensions = null
    cachedExtensionsAsArray = null
    adaptersAreSorted = false
    clearUserCache()

    // asserted here because clearCache is called on any write action
    assertNotReadOnlyMode()
  }

  private fun assertNotReadOnlyMode() {
    if (isInReadOnlyMode) {
      throw IllegalStateException("$this in a read-only mode and cannot be modified")
    }
  }

  abstract fun createAdapter(descriptor: ExtensionDescriptor,
                             pluginDescriptor: PluginDescriptor,
                             componentManager: ComponentManager): ExtensionComponentAdapter

  /**
   * [clearCache] is not called.
   * `adapters` is modified directly without copying - the method must be called only during start-up.
   */
  @Synchronized
  fun registerExtensions(descriptors: List<ExtensionDescriptor>,
                         pluginDescriptor: PluginDescriptor,
                         listenerCallbacks: MutableList<in Runnable>?) {
    adaptersAreSorted = false

    val oldAdapters = adapters
    val oldSize = oldAdapters.size
    val newAdapters = arrayOfNulls<ExtensionComponentAdapter>(oldAdapters.size + descriptors.size)
    var newSize = oldSize
    for ((index, item) in oldAdapters.withIndex()) {
      newAdapters[index] = item
    }
    for (descriptor in descriptors) {
      if (descriptor.os == null || descriptor.os.isSuitableForOs()) {
        newAdapters[newSize++] = createAdapter(descriptor = descriptor, pluginDescriptor = pluginDescriptor, componentManager = componentManager)
      }
    }

    @Suppress("UNCHECKED_CAST")
    adapters = Java11Shim.INSTANCE.listOf(newAdapters as Array<ExtensionComponentAdapter>, newSize)

    clearCache()
    val listeners = listeners
    if (listenerCallbacks == null || listeners.isEmpty()) {
      return
    }

    var addedAdapters = emptyList<ExtensionComponentAdapter>()
    for (listener in listeners) {
      if (listener is ExtensionPointAdapter<*>) {
        continue
      }

      // must be reported in order
      val newlyAddedUnsortedList = adapters.subList(oldSize, newSize)
      val newlyAddedSet = Collections.newSetFromMap<ExtensionComponentAdapter>(IdentityHashMap(newlyAddedUnsortedList.size))
      newlyAddedSet.addAll(newlyAddedUnsortedList)
      addedAdapters = ArrayList(newlyAddedSet.size)
      for (adapter in sortedAdapters) {
        if (newlyAddedSet.contains(adapter)) {
          addedAdapters.add(adapter)
        }
      }
      break
    }

    listenerCallbacks.add { notifyListeners(isRemoved = false, adapters = addedAdapters, listeners = listeners) }
  }

  @TestOnly
  @Synchronized
  fun notifyAreaReplaced(oldArea: ExtensionsArea) {
    for (listener in listeners) {
      if (listener is ExtensionPointAndAreaListener<*>) {
        (listener as ExtensionPointAndAreaListener<*>).areaReplaced(oldArea)
      }
    }
  }

  fun <V : T> findExtension(aClass: Class<V>, isRequired: Boolean, strictMatch: ThreeState): V? {
    if (strictMatch != ThreeState.NO) {
      val result = findExtensionByExactClass(aClass)
      if (result != null) {
        @Suppress("UNCHECKED_CAST")
        return result as V
      }
      else if (strictMatch == ThreeState.YES) {
        return null
      }
    }

    val extensionCache = cachedExtensions
    if (extensionCache == null) {
      for (adapter in sortedAdapters) {
        // findExtension is called for a lot of extension points - do not fail if listeners were added (e.g., FacetTypeRegistryImpl)
        try {
          if (aClass.isAssignableFrom(adapter.getImplementationClass<Any>(componentManager))) {
            return getOrCreateExtensionInstance(adapter, componentManager)
          }
        }
        catch (e: ClassNotFoundException) {
          componentManager.logError(e, adapter.pluginDescriptor.pluginId)
        }
      }
    }
    else {
      for (extension in extensionCache) {
        if (aClass.isInstance(extension)) {
          @Suppress("UNCHECKED_CAST")
          return extension as V
        }
      }
    }

    if (isRequired) {
      var message: @NonNls String = "cannot find extension implementation $aClass(epName=$name, extensionCount=${size()}"
      cachedExtensions?.let {
        message += ", cachedExtensions=$cachedExtensions"
      }
      if (isInReadOnlyMode) {
        message += ", point in read-only mode"
      }
      message += ")"
      throw componentManager.createError(message, getPluginDescriptor().pluginId)
    }
    return null
  }

  fun <V> findExtensions(aClass: Class<V>): List<T> {
    val extensionCache = cachedExtensions
    if (extensionCache == null) {
      val suitableInstances = ArrayList<T>()
      for (adapter in sortedAdapters) {
        try {
          // this enables us to not trigger Class initialization for all extensions, but only for those instanceof V
          if (aClass.isAssignableFrom(adapter.getImplementationClass<Any>(componentManager))) {
            val instance = getOrCreateExtensionInstance<T>(adapter, componentManager)
            if (instance != null) {
              suitableInstances.add(instance)
            }
          }
        }
        catch (e: ClassNotFoundException) {
          componentManager.logError(e, adapter.pluginDescriptor.pluginId)
        }
      }
      return suitableInstances
    }
    else {
      val result = ArrayList<T>()
      for (t in extensionCache) {
        if (aClass.isInstance(t)) {
          result.add(t)
        }
      }
      return result
    }
  }

  private fun findExtensionByExactClass(aClass: Class<out T>): T? {
    val cachedExtensions = cachedExtensions
    if (cachedExtensions == null) {
      for (adapter in sortedAdapters) {
        val classOrName = adapter.implementationClassOrName
        if (if (classOrName is String) classOrName == aClass.name else classOrName === aClass) {
          return getOrCreateExtensionInstance(adapter, componentManager)
        }
      }
    }
    else {
      for (extension in cachedExtensions) {
        if (aClass == extension.javaClass) {
          return extension
        }
      }
    }

    return null
  }

  override fun <K : Any> getByKey(key: K, cacheId: Class<*>, keyMapper: Function<T, K?>): T? {
    return ExtensionProcessingHelper.getByKey(point = this, key = key, cacheId = cacheId, keyMapper = keyMapper)
  }

  @get:Synchronized
  private val isInReadOnlyMode: Boolean
    get() = POINTS_IN_READONLY_MODE != null && POINTS_IN_READONLY_MODE!!.contains(this)
}

private class ObjectComponentAdapter<T : Any>(@JvmField val instance: T, pluginDescriptor: PluginDescriptor, loadingOrder: LoadingOrder)
  : ExtensionComponentAdapter(
  implementationClassName = instance.javaClass.getName(),
  pluginDescriptor = pluginDescriptor, orderId = null, order = loadingOrder,
  implementationClassResolver = ImplementationClassResolver { _, _ -> instance.javaClass }
) {
  override val isInstanceCreated: Boolean
    get() = true

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> createInstance(componentManager: ComponentManager) = instance as T?
}

// test-only
// guarded by this
private var POINTS_IN_READONLY_MODE: MutableSet<ExtensionPointImpl<*>>? = null

private var CHECK_CANCELED: (() -> Unit)? = null

private fun findInsertionIndexForAnyOrder(adapters: List<ExtensionComponentAdapter>): Int {
  var index = adapters.size
  while (index > 0) {
    val lastAdapter = adapters.get(index - 1)
    if (lastAdapter.order === LoadingOrder.LAST) {
      index--
    }
    else {
      break
    }
  }
  return index
}

private fun checkThatClassloaderIsActive(adapter: ExtensionComponentAdapter): Boolean {
  val classLoader = adapter.pluginDescriptor.pluginClassLoader
  if (classLoader is PluginAwareClassLoader && classLoader.state != PluginAwareClassLoader.ACTIVE) {
    LOG.warn("$adapter not loaded because classloader is being unloaded")
    return false
  }
  return true
}

private fun isInsideClassInitializer(trace: Array<StackTraceElement>): Boolean {
  return trace.any {
    "<clinit>" == it.methodName
  }
}

// the instantiation of an extension is done in a safe manner always — will be logged as an error with a plugin id
private fun <T : Any> getOrCreateExtensionInstance(adapter: ExtensionComponentAdapter, componentManager: ComponentManager): T? {
  if (!checkThatClassloaderIsActive(adapter)) {
    return null
  }

  try {
    val instance = adapter.createInstance<T>(componentManager)
    if (instance == null) {
      LOG.debug { "$adapter not loaded because it reported that not applicable" }
    }
    return instance
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.error(componentManager.createError(e, adapter.pluginDescriptor.pluginId))
  }
  return null
}
