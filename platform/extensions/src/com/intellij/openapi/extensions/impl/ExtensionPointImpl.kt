// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "OVERRIDE_DEPRECATION")

package com.intellij.openapi.extensions.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.extensions.LoadingOrder.Companion.sort
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.util.ArrayUtil
import com.intellij.util.SmartList
import com.intellij.util.ThreeState
import com.intellij.util.containers.ContainerUtil
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentList.add
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.Unmodifiable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.*
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.concurrent.Volatile

private val LOG: Logger = logger<ExtensionPointImpl<*>>()

@ApiStatus.Internal
abstract class ExtensionPointImpl<T> internal constructor(val name: String,
                                                          val className: String,
                                                          protected val pluginDescriptor: PluginDescriptor,
                                                          val componentManager: ComponentManager,
                                                          private var extensionClass: Class<T>?,
                                                          private val isDynamic: Boolean) : ExtensionPoint<T>, Iterable<T> {
  // immutable list, never modified in-place, only swapped atomically
  @Volatile
  private var cachedExtensions: List<T>? = null

  // Since JDK 9, Arrays.ArrayList.toArray() returns {@code Object[]} instead of {@code T[]}
  // (https://bugs.openjdk.org/browse/JDK-6260652), so we cannot use it anymore.
  // Only array.clone should be used because of performance reasons (https://youtrack.jetbrains.com/issue/IDEA-198172).
  @Volatile
  private var cachedExtensionsAsArray: Array<T>? = null

  // guarded by this
  @Volatile
  private var adapters = emptyList<ExtensionComponentAdapter>()

  @Volatile
  private var adaptersAreSorted = true

  // guarded by this
  private var listeners = persistentListOf<ExtensionPointListener<T>>()

  private val keyMapperToCacheRef = AtomicReference<ConcurrentMap<*, Map<*, *>>>()

  companion object {
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

  fun <CACHE_KEY : Any?, V : Any?> getCacheMap(): ConcurrentMap<CACHE_KEY, V> {
    var keyMapperToCache = keyMapperToCacheRef.get()
    if (keyMapperToCache == null) {
      keyMapperToCache = keyMapperToCacheRef.updateAndGet { prev -> prev ?: ConcurrentHashMap<Any?, Map<*, *>>() }
    }
    @Suppress("UNCHECKED_CAST")
    return keyMapperToCache as ConcurrentMap<CACHE_KEY, V>
  }

  override fun isDynamic(): Boolean = isDynamic

  override fun registerExtension(extension: T) {
    doRegisterExtension(extension = extension, order = LoadingOrder.ANY, pluginDescriptor = pluginDescriptor, parentDisposable = null)
  }

  override fun registerExtension(extension: T, parentDisposable: Disposable) {
    registerExtension(extension = extension, pluginDescriptor = pluginDescriptor, parentDisposable = parentDisposable)
  }

  override fun registerExtension(extension: T, pluginDescriptor: PluginDescriptor, parentDisposable: Disposable) {
    doRegisterExtension(extension = extension,
                        order = LoadingOrder.ANY,
                        pluginDescriptor = pluginDescriptor,
                        parentDisposable = parentDisposable)
  }

  override fun getPluginDescriptor(): PluginDescriptor = pluginDescriptor

  override fun registerExtension(extension: T, order: LoadingOrder, parentDisposable: Disposable) {
    doRegisterExtension(extension = extension, order = order, pluginDescriptor = getPluginDescriptor(), parentDisposable = parentDisposable)
  }

  @Synchronized
  private fun doRegisterExtension(extension: T,
                                  order: LoadingOrder,
                                  pluginDescriptor: PluginDescriptor,
                                  parentDisposable: Disposable?) {
    assertNotReadOnlyMode()
    checkExtensionType(extension, getExtensionClass(), null)

    for (adapter in adapters) {
      if (adapter is ObjectComponentAdapter<*> && adapter.componentInstance === extension) {
        LOG.error("Extension was already added: $extension")
        return
      }
    }

    val adapter = ObjectComponentAdapter(extension, pluginDescriptor, order)
    addExtensionAdapter(adapter)
    notifyListeners(false, persistentListOf(adapter), listeners)

    if (parentDisposable != null) {
      Disposer.register(parentDisposable) {
        synchronized(this) {
          val index = ContainerUtil.indexOfIdentity(adapters, adapter)
          if (index < 0) {
            LOG.error("Extension to be removed not found: " + adapter.componentInstance)
          }

          val list: List<ExtensionComponentAdapter> = ArrayList(adapters)
          list.removeAt(index)
          adapters = list
          clearCache()
          notifyListeners(true, persistentListOf(adapter), listeners)
        }
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
        if (ContainerUtil.containsIdentity(extensions, adapter)) {
          LOG.error("Extension was already added: " + adapter.componentInstance)
          return
        }
      }
    }

    val newAdapters = doRegisterExtensions(extensions)
    // do not call notifyListeners under lock
    var listeners: PersistentList<ExtensionPointListener<T>>
    synchronized(this) {
      listeners = this.listeners
    }
    notifyListeners(false, newAdapters, listeners)
  }

  @Synchronized
  private fun doRegisterExtensions(extensions: List<T>): List<ExtensionComponentAdapter> {
    val newAdapters: MutableList<ExtensionComponentAdapter> = ArrayList(extensions.size)
    for (extension in extensions) {
      newAdapters.add(ObjectComponentAdapter(extension, getPluginDescriptor(), LoadingOrder.ANY))
    }

    if (adapters === emptyList<ExtensionComponentAdapter>()) {
      adapters = newAdapters
    }
    else {
      val list: MutableList<ExtensionComponentAdapter> = ArrayList(adapters.size + newAdapters.size)
      list.addAll(adapters)
      list.addAll(findInsertionIndexForAnyOrder(adapters), newAdapters)
      adapters = list
    }
    clearCache()
    return newAdapters
  }

  private fun checkExtensionType(extension: T, extensionClass: Class<T>, adapter: ExtensionComponentAdapter?) {
    if (!extensionClass.isInstance(extension)) {
      var message: @NonNls String = "Extension " + extension.javaClass.getName() + " does not implement " + extensionClass
      if (adapter == null) {
        throw RuntimeException(message)
      }
      else {
        message += " (adapter=$adapter)"
        throw componentManager.createError(message, null, adapter.pluginDescriptor.pluginId,
                                           Collections.singletonMap("threadDump", ThreadDumper.dumpThreadsToString()))
      }
    }
  }

  override fun getExtensionList(): List<T> {
    var result = cachedExtensions
    if (result == null) {
      synchronized(this) {
        result = cachedExtensions
        if (result == null) {
          val array = processAdapters()
          cachedExtensionsAsArray = array
          result = if (array.size == 0) emptyList() else ContainerUtil.immutableList(*array)
          cachedExtensions = result
        }
      }
    }
    return result!!
  }

  override fun getExtensions(): Array<T> {
    var array = cachedExtensionsAsArray
    if (array == null) {
      synchronized(this) {
        array = cachedExtensionsAsArray
        if (array == null) {
          array = processAdapters()
          cachedExtensionsAsArray = array
          cachedExtensions = if (array!!.size == 0) emptyList<T>() else ContainerUtil.immutableList<T>(*array!!)
        }
      }
    }
    return if (array!!.size == 0) array!! else array!!.clone()
  }

  /**
   * Do not use it if there is any extension point listener, because in this case behavior is not predictable:
   * events will be fired during iteration, which is probably not expected.
   *
   *
   * Use only for interface extension points, not for beans.
   *
   *
   * Due to internal reasons, there is no easy way to implement hasNext in a reliable manner,
   * so, `next` may return `null` (in this case, stop iteration).
   */
  @ApiStatus.Experimental
  override fun iterator(): MutableIterator<T> {
    val result = cachedExtensions
    return result?.iterator() ?: createIterator()
  }

  fun processWithPluginDescriptor(shouldBeSorted: Boolean, consumer: BiConsumer<in T, in PluginDescriptor?>) {
    if (isInReadOnlyMode) {
      for (extension in Objects.requireNonNull(cachedExtensionsAsArray)) {
        consumer.accept(extension, pluginDescriptor /* doesn't matter for tests */)
      }
      return
    }

    for (adapter in if (shouldBeSorted) sortedAdapters else adapters) {
      val extension = processAdapter(adapter)
      if (extension != null) {
        consumer.accept(extension, adapter.pluginDescriptor)
      }
    }
  }

  fun processImplementations(shouldBeSorted: Boolean, consumer: BiConsumer<in Supplier<out T>?, in PluginDescriptor?>) {
    if (isInReadOnlyMode) {
      for (extension in Objects.requireNonNull(cachedExtensionsAsArray)) {
        consumer.accept(Supplier<T> { extension }, pluginDescriptor /* doesn't matter for tests */)
      }
      return
    }

    // do not use getThreadSafeAdapterList - no need to check that no listeners, because processImplementations is not a generic-purpose method
    for (adapter in if (shouldBeSorted) sortedAdapters else adapters) {
      consumer.accept(
        Supplier { adapter.createInstance<T>(componentManager) } as Supplier<T>, adapter.pluginDescriptor)
    }
  }

  @TestOnly
  fun checkImplementations(consumer: Consumer<in ExtensionComponentAdapter?>) {
    for (adapter in sortedAdapters) {
      consumer.accept(adapter)
    }
  }

  private fun createIterator(): MutableIterator<T?> {
    val size: Int
    val adapters = sortedAdapters
    size = adapters.size
    if (size == 0) {
      return Collections.emptyIterator()
    }

    return object : MutableIterator<T> {
      private var currentIndex = 0

      override fun hasNext(): Boolean {
        return currentIndex < size
      }

      override fun next(): T {
        do {
          val extension = processAdapter(adapters[currentIndex++])
          if (extension != null) {
            return extension
          }
        }
        while (hasNext())
        return null
      }
    }
  }

  override fun spliterator(): Spliterator<T> {
    return Spliterators.spliterator(iterator(), size().toLong(), Spliterator.IMMUTABLE or Spliterator.NONNULL or Spliterator.DISTINCT)
  }

  override fun extensions(): Stream<T> {
    val result = cachedExtensionsAsArray
    return if (result == null) StreamSupport.stream(spliterator(), false) else Arrays.stream(result)
  }

  override fun size(): Int {
    val cache = cachedExtensionsAsArray
    return cache?.size ?: adapters.size
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
          val list: List<ExtensionComponentAdapter> = ArrayList(adapters)
          sort(list)
          adapters = list
        }
        adaptersAreSorted = true
      }
      return adapters
    }

  private fun processAdapters(): Array<T> {
    assertNotReadOnlyMode()

    // check before to avoid any "restore" work if already canceled
    CHECK_CANCELED.run()

    val startTime = System.nanoTime()

    val adapters = sortedAdapters
    val totalSize = adapters.size
    val extensionClass = getExtensionClass()
    var result = ArrayUtil.newArray(extensionClass, totalSize)
    if (totalSize == 0) {
      return result
    }

    val duplicates: MutableSet<T>? = if (this is BeanExtensionPoint<*>) null else HashSet(totalSize)
    val listeners = this.listeners
    var extensionIndex = 0
    for (i in adapters.indices) {
      val extension = processAdapter(adapters[i], listeners, result, duplicates, extensionClass, adapters)
      if (extension != null) {
        result[extensionIndex++] = extension
      }
    }

    if (extensionIndex != result.size) {
      result = result.copyOf(extensionIndex)
    }

    // do not count ProcessCanceledException as a valid action to measure (later special category can be introduced if needed)
    val category = componentManager.getActivityCategory(true)
    StartUpMeasurer.addCompletedActivity(startTime, extensionClass, category,  /* pluginId = */null, StartUpMeasurer.MEASURE_THRESHOLD)
    return result
  }

  // This method needs to be synchronized because XmlExtensionAdapter.createInstance takes a lock on itself, and if it's called without
  // EP lock and tries to add an EP listener, we can get a deadlock because of lock ordering violation
  // (EP->adapter in one thread, adapter->EP in the other thread)
  @Synchronized
  private fun processAdapter(adapter: ExtensionComponentAdapter): T? {
    try {
      if (!checkThatClassloaderIsActive(adapter)) {
        return null
      }
      val instance = adapter.createInstance<T?>(componentManager)
      if (instance == null && LOG.isDebugEnabled) {
        LOG.debug(
          "$adapter not loaded because it reported that not applicable")
      }
      return instance
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
    return null
  }

  private fun processAdapter(adapter: ExtensionComponentAdapter,
                             listeners: List<ExtensionPointListener<T>>?,
                             result: Array<T>?,
                             duplicates: MutableSet<T>?,
                             extensionClassForCheck: Class<T>,
                             adapters: List<ExtensionComponentAdapter>): T? {
    try {
      if (!checkThatClassloaderIsActive(adapter)) {
        return null
      }

      val isNotifyThatAdded = listeners != null && !listeners.isEmpty() && !adapter.isInstanceCreated &&
                              !isDynamic
      // do not call CHECK_CANCELED here in loop because it is called by createInstance()
      val extension = adapter.createInstance<T?>(componentManager)
      if (extension == null) {
        if (LOG.isDebugEnabled) {
          LOG.debug(
            "$adapter not loaded because it reported that not applicable")
        }
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
        assert(result != null)
        LOG.error("""Duplicate extension found:
                   $extension;
  prev extension:  $duplicate;
  adapter:         $adapter;
  extension class: $extensionClassForCheck;
  result:          ${Arrays.asList(*result)};
  adapters:        $adapters"""
        )
      }
      else {
        checkExtensionType(extension, extensionClassForCheck, adapter)
        if (isNotifyThatAdded) {
          notifyListeners(false, listOf(adapter), listeners!!)
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
   * For tests this method is more preferable than [)][.registerExtension] because makes registration more isolated and strict
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
    val oldArray = cachedExtensionsAsArray
    val oldAdapters = adapters
    val oldAdaptersAreSorted = adaptersAreSorted

    val newArray = newList.toArray<T>(java.lang.reflect.Array.newInstance(getExtensionClass(), 0) as Array<T>)
    cachedExtensionsAsArray = newArray
    cachedExtensions = ContainerUtil.immutableList(*newArray)
    val result: @Unmodifiable MutableList<ExtensionComponentAdapter>
    if ((newList as Collection<T>).isEmpty()) {
      result = emptyList<ExtensionComponentAdapter>()
    }
    else {
      result = ArrayList(newList.size)
      for (t in newList) {
        result.add(ObjectComponentAdapter(t as Any, pluginDescriptor, LoadingOrder.ANY))
      }
    }
    adapters = result
    adaptersAreSorted = true

    POINTS_IN_READONLY_MODE!!.add(this)

    val listeners = this.listeners
    if (fireEvents && !listeners.isEmpty()) {
      if (oldList != null) {
        doNotifyListeners(true, oldList, listeners)
      }
      doNotifyListeners(false, newList, this.listeners)
    }

    clearUserCache()

    Disposer.register(parentDisposable, object : Disposable {
      @TestOnly
      override fun dispose() {
        synchronized(this) {
          POINTS_IN_READONLY_MODE!!.remove(this@ExtensionPointImpl)
          cachedExtensions = oldList
          cachedExtensionsAsArray = oldArray
          adapters = oldAdapters
          adaptersAreSorted = oldAdaptersAreSorted

          val listeners = this@ExtensionPointImpl.listeners
          if (fireEvents && !listeners.isEmpty()) {
            doNotifyListeners(true, newList, listeners)
            if (oldList != null) {
              doNotifyListeners(false, oldList, listeners)
            }
          }
          clearUserCache()
        }
      }
    })
  }

  @TestOnly
  private fun doNotifyListeners(isRemoved: Boolean,
                                extensions: List<T>,
                                listeners: List<ExtensionPointListener<T>>) {
    for (listener in listeners) {
      if (listener is ExtensionPointAdapter<*>) {
        try {
          (listener as ExtensionPointAdapter<T>).extensionListChanged()
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
              listener.extensionRemoved(extension, pluginDescriptor)
            }
            else {
              listener.extensionAdded(extension, pluginDescriptor)
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
  override fun unregisterExtension(extension: T) {
    if (!unregisterExtensions(
        { `__`: String?, adapter: ExtensionComponentAdapter ->
          !adapter.isInstanceCreated ||
          adapter.createInstance<Any>(
            componentManager) !== extension
        }, true)) {
      // there is a possible case that a particular extension was replaced in a particular environment
      // (e.g., Upsource replaces some platform extensions important for CoreApplicationEnvironment),
      // so just log an error instead of throwing
      LOG.warn("Extension to be removed not found: $extension")
    }
  }

  override fun unregisterExtension(extensionClass: Class<out T>) {
    val classNameToUnregister = extensionClass.canonicalName
    if (!unregisterExtensions(
        { cls: String, adapter: ExtensionComponentAdapter? -> cls != classNameToUnregister },  /* stopAfterFirstMatch = */true)) {
      LOG.warn("Extension to be removed not found: $extensionClass")
    }
  }

  override fun unregisterExtensions(extensionClassNameFilter: BiPredicate<String, ExtensionComponentAdapter>,
                                    stopAfterFirstMatch: Boolean): Boolean {
    val listenerCallbacks: MutableList<Runnable> = ArrayList()
    val priorityListenerCallbacks: MutableList<Runnable> = ArrayList()
    val result = unregisterExtensions(stopAfterFirstMatch, priorityListenerCallbacks, listenerCallbacks
    ) { adapter: ExtensionComponentAdapter -> extensionClassNameFilter.test(adapter.assignableToClassName, adapter) }
    for (callback in priorityListenerCallbacks) {
      callback.run()
    }
    for (callback in listenerCallbacks) {
      callback.run()
    }
    return result
  }

  /**
   * Unregisters extensions for which the specified predicate returns false and collects the runnables for listener invocation into the given list
   * so that listeners can be called later.
   */
  @Synchronized
  fun unregisterExtensions(stopAfterFirstMatch: Boolean,
                           priorityListenerCallbacks: MutableList<in Runnable>,
                           listenerCallbacks: MutableList<in Runnable>,
                           extensionToKeepFilter: Predicate<in ExtensionComponentAdapter>): Boolean {
    val listeners = this.listeners
    var removedAdapters: MutableList<ExtensionComponentAdapter>? = null
    var adapters = this.adapters
    for (i in adapters.indices.reversed()) {
      val adapter = adapters[i]
      if (extensionToKeepFilter.test(adapter)) {
        continue
      }

      if (adapters === this.adapters) {
        adapters = ArrayList(adapters)
      }
      adapters.removeAt(i)

      if (!listeners.isEmpty()) {
        if (removedAdapters == null) {
          removedAdapters = ArrayList()
        }
        removedAdapters.add(adapter)
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

    if (removedAdapters == null) {
      return true
    }

    var priorityListeners: MutableList<ExtensionPointListener<T>> = SmartList()
    for (t in listeners) {
      if (t is ExtensionPointPriorityListener) {
        priorityListeners.add(t)
      }
    }
    priorityListeners = Collections.unmodifiableList(priorityListeners)

    var regularListeners: MutableList<ExtensionPointListener<T>> = ArrayList()
    for (t in listeners) {
      if (t !is ExtensionPointPriorityListener) {
        regularListeners.add(t)
      }
    }
    regularListeners = Collections.unmodifiableList(regularListeners)

    val finalRemovedAdapters: List<ExtensionComponentAdapter> = removedAdapters
    if (!priorityListeners.isEmpty()) {
      val finalPriorityListeners: List<ExtensionPointListener<T>> = priorityListeners
      priorityListenerCallbacks.add(Runnable { notifyListeners(true, finalRemovedAdapters, finalPriorityListeners) }
      )
    }
    if (!regularListeners.isEmpty()) {
      val finalRegularListeners: List<ExtensionPointListener<T>> = regularListeners
      listenerCallbacks.add(Runnable { notifyListeners(true, finalRemovedAdapters, finalRegularListeners) }
      )
    }
    return true
  }

  abstract fun unregisterExtensions(componentManager: ComponentManager,
                                    pluginDescriptor: PluginDescriptor,
                                    priorityListenerCallbacks: List<in Runnable?>,
                                    listenerCallbacks: List<in Runnable?>)

  private fun notifyListeners(isRemoved: Boolean,
                              adapters: List<ExtensionComponentAdapter>,
                              listeners: List<ExtensionPointListener<T>>) {
    for (listener in listeners) {
      if (listener is ExtensionPointAdapter<*>) {
        try {
          (listener as ExtensionPointAdapter<T>).extensionListChanged()
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
      else {
        for (adapter in adapters) {
          if (isRemoved && !adapter.isInstanceCreated) {
            continue
          }

          try {
            val extension = adapter.createInstance<T?>(componentManager)
            if (extension != null) {
              if (isRemoved) {
                listener.extensionRemoved(extension, adapter.pluginDescriptor)
              }
              else {
                listener.extensionAdded(extension, adapter.pluginDescriptor)
              }
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

  override fun addExtensionPointListener(listener: ExtensionPointListener<T>,
                                         invokeForLoadedExtensions: Boolean,
                                         parentDisposable: Disposable?) {
    val isAdded = addListener(listener)
    if (!isAdded) {
      return
    }

    if (invokeForLoadedExtensions) {
      notifyListeners(false, sortedAdapters, listOf(listener))
    }

    if (parentDisposable != null) {
      Disposer.register(parentDisposable) { removeExtensionPointListener(listener) }
    }
  }

  // true if added
  @Synchronized
  private fun addListener(listener: ExtensionPointListener<T>): Boolean {
    if (listeners.contains(listener)) {
      return false
    }

    listeners = if (listener is ExtensionPointPriorityListener) listeners.add(0, listener) else listeners.add(listener)
    return true
  }

  @Synchronized
  override fun addChangeListener(listener: Runnable, parentDisposable: Disposable?) {
    val listenerAdapter: ExtensionPointAdapter<T> = object : ExtensionPointAdapter<T>() {
      override fun extensionListChanged() {
        listener.run()
      }
    }

    listeners = listeners.add(listenerAdapter)
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) { removeExtensionPointListener(listenerAdapter) }
    }
  }

  @Synchronized
  override fun removeExtensionPointListener(listener: ExtensionPointListener<T>) {
    listeners = listeners.remove(listener)
  }

  @Synchronized
  fun reset() {
    val adapters = this.adapters
    this.adapters = emptyList()
    // clear cache before notifying listeners to ensure that listeners don't get outdated data
    clearCache()
    if (!adapters.isEmpty() && !listeners.isEmpty()) {
      notifyListeners(true, adapters, listeners)
    }

    // help GC
    listeners = persistentListOf()
    extensionClass = null
  }

  fun getExtensionClass(): Class<T> {
    var extensionClass = this.extensionClass
    if (extensionClass == null) {
      try {
        extensionClass = componentManager.loadClass(className, pluginDescriptor)
        this.extensionClass = extensionClass
      }
      catch (e: ClassNotFoundException) {
        throw componentManager.createError(e, pluginDescriptor.pluginId)
      }
    }
    return extensionClass
  }

  override fun toString(): String {
    return name
  }

  // private, internal only for tests
  @Synchronized
  fun addExtensionAdapter(adapter: ExtensionComponentAdapter) {
    val list: MutableList<ExtensionComponentAdapter> = ArrayList(adapters.size + 1)
    list.addAll(adapters)
    list.add(adapter)
    adapters = list
    clearCache()
  }

  fun clearUserCache() {
    val map = keyMapperToCacheRef.get()
    map?.clear()
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

  abstract fun createAdapter(extensionElement: ExtensionDescriptor,
                             pluginDescriptor: PluginDescriptor,
                             componentManager: ComponentManager): ExtensionComponentAdapter

  /**
   * [.clearCache] is not called.
   * `myAdapters` is modified directly without copying - method must be called only during start-up.
   */
  @Synchronized
  fun registerExtensions(extensionElements: List<ExtensionDescriptor>,
                         pluginDescriptor: PluginDescriptor,
                         listenerCallbacks: MutableList<in Runnable?>?) {
    var adapters = this.adapters
    if (adapters === emptyList<ExtensionComponentAdapter>()) {
      adapters = ArrayList(extensionElements.size)
      this.adapters = adapters
      adaptersAreSorted = false
    }
    else {
      (adapters as ArrayList<ExtensionComponentAdapter>).ensureCapacity(adapters.size + extensionElements.size)
    }

    val oldSize = adapters.size
    for (extensionElement in extensionElements) {
      if (extensionElement.os == null || componentManager.isSuitableForOs(extensionElement.os)) {
        adapters.add(createAdapter(extensionElement, pluginDescriptor, componentManager))
      }
    }
    val newSize = adapters.size

    clearCache()
    val listeners = this.listeners
    if (listenerCallbacks == null || listeners.isEmpty()) {
      return
    }

    var addedAdapters = emptyList<ExtensionComponentAdapter>()
    for (listener in listeners) {
      if (listener !is ExtensionPointAdapter<*>) {
        // must be reported in order
        val newlyAddedUnsortedList = adapters.subList(oldSize, newSize)
        val newlyAddedSet = Collections.newSetFromMap(
          IdentityHashMap<ExtensionComponentAdapter, Boolean>(newlyAddedUnsortedList.size))
        newlyAddedSet.addAll(newlyAddedUnsortedList)
        addedAdapters = ArrayList(newlyAddedSet.size)
        for (adapter in sortedAdapters) {
          if (newlyAddedSet.contains(adapter)) {
            addedAdapters.add(adapter)
          }
        }
        break
      }
    }

    val finalAddedAdapters = addedAdapters
    listenerCallbacks.add(Runnable { notifyListeners(false, finalAddedAdapters, listeners) })
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

  fun <V : T?> findExtension(aClass: Class<V>, isRequired: Boolean, strictMatch: ThreeState): V? {
    if (strictMatch != ThreeState.NO) {
      val result = findExtensionByExactClass(aClass) as V?
      if (result != null) {
        return result
      }
      else if (strictMatch == ThreeState.YES) {
        return null
      }
    }

    val extensionCache = cachedExtensionsAsArray
    if (extensionCache == null) {
      for (adapter in sortedAdapters) {
        // findExtension is called for a lot of extension points - do not fail if listeners were added (e.g., FacetTypeRegistryImpl)
        try {
          if (aClass.isAssignableFrom(adapter.getImplementationClass<Any>(componentManager))) {
            return processAdapter(adapter) as V?
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
          return extension as V
        }
      }
    }

    if (isRequired) {
      var message: @NonNls String = "cannot find extension implementation " + aClass + "(epName=" + name + ", extensionCount=" + size()
      val cache = cachedExtensionsAsArray
      if (cache != null) {
        message += ", cachedExtensions"
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
    val extensionCache = cachedExtensionsAsArray
    if (extensionCache == null) {
      val suitableInstances: MutableList<T> = ArrayList()
      for (adapter in sortedAdapters) {
        try {
          // this enables us to not trigger Class initialization for all extensions, but only for those instanceof V
          if (aClass.isAssignableFrom(adapter.getImplementationClass<Any>(componentManager))) {
            val instance = processAdapter(adapter)
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
      val result: MutableList<T> = ArrayList()
      for (t in extensionCache) {
        if (aClass.isInstance(t)) {
          result.add(t)
        }
      }
      return result
    }
  }

  private fun findExtensionByExactClass(aClass: Class<out T>): T? {
    val cachedExtensions = this.cachedExtensionsAsArray
    if (cachedExtensions == null) {
      for (adapter in sortedAdapters) {
        val classOrName = adapter.implementationClassOrName
        if (if (classOrName is String) classOrName == aClass.name else classOrName === aClass) {
          return processAdapter(adapter)
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

  private class ObjectComponentAdapter<T>(@JvmField val componentInstance: T,
                                          pluginDescriptor: PluginDescriptor,
                                          loadingOrder: LoadingOrder) : ExtensionComponentAdapter(extension.javaClass.getName(),
                                                                                                  pluginDescriptor, null, loadingOrder,
                                                                                                  ImplementationClassResolver { `__`: ComponentManager?, `___`: ExtensionComponentAdapter? -> extension.javaClass }) {
    val `isInstanceCreated$intellij_platform_extensions`: Boolean
      get() = true

    override fun <I> createInstance(componentManager: ComponentManager?): I {
      return componentInstance as I
    }
  }

  @get:Synchronized
  private val isInReadOnlyMode: Boolean
    get() = POINTS_IN_READONLY_MODE != null && POINTS_IN_READONLY_MODE!!.contains(this)
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
  return trace.any { "<clinit>" == it.methodName }
}