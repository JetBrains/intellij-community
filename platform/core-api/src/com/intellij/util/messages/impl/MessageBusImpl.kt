// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.messages.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.isMessageBusErrorPropagationEnabled
import com.intellij.openapi.application.isMessageBusThrowsWhenDisposed
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.ArrayUtilRt
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.*
import com.intellij.util.messages.Topic.BroadcastDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.lang.invoke.MethodHandle
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate

@Internal
open class MessageBusImpl : MessageBus {
  interface MessageHandlerHolder {
    val isDisposed: Boolean

    fun collectHandlers(topic: Topic<*>, result: MutableList<in Any>)

    fun disconnectIfNeeded(predicate: Predicate<Class<*>>)
  }

  companion object {
    @JvmField
    internal val LOG: Logger = logger<MessageBusImpl>()
  }

  @JvmField
  internal val publisherCache: ConcurrentMap<Topic<*>, Any> = ConcurrentHashMap()

  @JvmField
  internal val subscribers: ConcurrentLinkedQueue<MessageHandlerHolder> = ConcurrentLinkedQueue<MessageHandlerHolder>()

  // caches subscribers for this bus and its children or parents, depending on the topic's broadcast policy
  @JvmField
  internal val subscriberCache: ConcurrentMap<Topic<*>, Array<Any?>> = ConcurrentHashMap()

  @JvmField
  internal val parentBus: CompositeMessageBus?

  @JvmField
  internal val rootBus: RootBus

  @JvmField
  internal val owner: MessageBusOwner

  private var disposeState: BusState = BusState.Alive

  // separate disposable must be used, because container will dispose bus connections in a separate step
  private var connectionDisposable: Disposable? = Disposer.newDisposable()

  @JvmField
  internal var messageDeliveryListeners: MutableSet<MessageDeliveryListener> = ConcurrentHashMap.newKeySet()

  constructor(owner: MessageBusOwner, parentBus: CompositeMessageBus) {
    this.owner = owner
    this.parentBus = parentBus
    rootBus = parentBus.rootBus
    @Suppress("LeakingThis")
    parentBus.addChild(this)
  }

  // root message bus constructor
  internal constructor(owner: MessageBusOwner) {
    this.owner = owner
    @Suppress("LeakingThis")
    rootBus = this as RootBus
    parentBus = null
  }

  override val parent: MessageBus?
    get() = parentBus

  override fun toString(): String = "MessageBus(owner=$owner, disposeState= $disposeState)"

  override fun connect(): MessageBusConnection = connect(connectionDisposable!!)

  override fun connect(parentDisposable: Disposable): MessageBusConnection {
    checkDisposed()
    val connection = MessageBusConnectionImpl(this)
    subscribers.add(connection)
    Disposer.register(parentDisposable, connection)
    return connection
  }

  override fun simpleConnect(): SimpleMessageBusConnection {
    // to avoid registering in a Dispose tree, default handler and deliverImmediately are not supported
    checkDisposed()
    val connection = SimpleMessageBusConnectionImpl(this)
    subscribers.add(connection)
    return connection
  }

  override fun connect(coroutineScope: CoroutineScope): SimpleMessageBusConnection {
    val scopeJob = coroutineScope.coroutineContext.job
    scopeJob.ensureActive()
    checkDisposed()
    val connection = SimpleMessageBusConnectionImpl(this)
    try {
      subscribers.add(connection)
    }
    finally {
      scopeJob.invokeOnCompletion { connection.disconnect() }
    }
    return connection
  }

  override fun <L : Any> syncPublisher(topic: Topic<L>): L {
    if (isDisposed) {
      PluginException.logPluginError(LOG, "Already disposed: $this", null, topic.javaClass)
    }

    @Suppress("UNCHECKED_CAST")
    return publisherCache.computeIfAbsent(topic) { topic1 ->
      val aClass = topic1.listenerClass
      val publisher = createPublisher(topic = topic1, direction = topic1.broadcastDirection)
      Proxy.newProxyInstance(aClass.classLoader, arrayOf(aClass), publisher)
    } as L
  }

  override fun <L : Any> syncAndPreloadPublisher(topic: Topic<L>): L {
    val publisher = syncPublisher(topic)
    (Proxy.getInvocationHandler(publisher) as MessagePublisher<*>).preload()
    return publisher
  }

  internal open fun <L> createPublisher(topic: Topic<L>, direction: BroadcastDirection): MessagePublisher<L> {
    return when (direction) {
      BroadcastDirection.TO_PARENT -> ToParentMessagePublisher(topic, this)
      BroadcastDirection.TO_DIRECT_CHILDREN -> {
        throw IllegalArgumentException("Broadcast direction TO_DIRECT_CHILDREN is allowed only for app level message bus. " +
                                       "Please publish to app level message bus or change topic broadcast direction to NONE or TO_PARENT")
      }
      else -> {
        LOG.error("Topic ${topic.listenerClass.name} broadcast direction TO_CHILDREN is not allowed for module level message bus. " +
                  "Please change to NONE or TO_PARENT")
        MessagePublisher(topic, this)
      }
    }
  }

  fun disposeConnectionChildren() {
    // avoid any work on notifyConnectionTerminated
    disposeState = BusState.Disposing
    Disposer.disposeChildren(connectionDisposable!!) { true }
  }

  fun disposeConnection() {
    Disposer.dispose(connectionDisposable!!)
    connectionDisposable = null
  }

  override fun dispose() {
    checkBusDisposed()
    disposeState = BusState.Disposed(Throwable())
    disposeChildren()
    connectionDisposable?.let {
      Disposer.dispose(it)
    }
    rootBus.queue.queue.removeIf { it.bus === this }
    parentBus?.onChildBusDisposed(this)
  }

  internal open fun disposeChildren() {
  }

  override val isDisposed: Boolean
    get() = disposeState is BusState.Disposed || owner.isDisposed

  override fun hasUndeliveredEvents(topic: Topic<*>): Boolean {
    if (isDisposed) {
      return false
    }

    val queue = rootBus.queue
    val current = queue.current
    if (current != null && current.topic === topic && current.bus === this) {
      return true
    }
    return queue.queue.any { it.topic === topic && it.bus === this }
  }

  internal fun checkDisposed() {
    checkBusDisposed()
    checkOwnerDisposed()
  }

  private fun checkBusDisposed() {
    val state = disposeState
    if (state is BusState.Disposed) {
      if (isMessageBusThrowsWhenDisposed) {
        throw AlreadyDisposedException(this.toString())
          .initCause(state.where)
      }
      else {
        LOG.error("Already disposed: $this", state.where)
      }
    }
  }

  private fun checkOwnerDisposed() {
    if (owner.isDisposed) {
      if (isMessageBusThrowsWhenDisposed) {
        throw AlreadyDisposedException(this.toString())
      }
      else {
        LOG.error("Already disposed: $this")
      }
    }
  }

  internal open fun doComputeSubscribers(topic: Topic<*>, result: MutableList<in Any>, subscribeLazyListeners: Boolean) {
    // todo check that handler implements method (not a default implementation)
    for (subscriber in subscribers) {
      if (!subscriber.isDisposed) {
        subscriber.collectHandlers(topic, result)
      }
    }
  }

  internal open fun computeSubscribers(topic: Topic<*>): Array<Any?> {
    val result = mutableListOf<Any>()
    doComputeSubscribers(topic, result, true)
    return if (result.isEmpty()) ArrayUtilRt.EMPTY_OBJECT_ARRAY else result.toTypedArray()
  }

  internal open fun hasChildren(): Boolean = false

  internal fun notifyOnSubscription(topic: Topic<*>) {
    subscriberCache.remove(topic)
    if (topic.broadcastDirection != BroadcastDirection.TO_CHILDREN) {
      return
    }

    // Clear parents because parent caches subscribers for `TO_CHILDREN` direction on it's level and child levels.
    // So, on subscription to child bus (this instance) parent cache must be invalidated.
    var parentBus = this
    while (true) {
      parentBus = parentBus.parentBus ?: break
      parentBus.subscriberCache.remove(topic)
    }

    if (hasChildren()) {
      notifyOnSubscriptionToTopicToChildren(topic)
    }
  }

  internal open fun notifyOnSubscriptionToTopicToChildren(topic: Topic<*>) {
  }

  open fun removeEmptyConnectionsRecursively() {
    subscribers.removeIf { it.isDisposed }
  }

  internal open fun notifyConnectionTerminated(topicAndHandlerPairs: Array<Any>): Boolean {
    if (disposeState != BusState.Alive) {
      return false
    }

    rootBus.scheduleEmptyConnectionRemoving()
    return clearSubscriberCacheOnConnectionTerminated(topicAndHandlerPairs, this)
  }

  // this method is used only in CompositeMessageBus.notifyConnectionTerminated to clear subscriber cache in children
  internal open fun clearSubscriberCache(topicAndHandlerPairs: Array<Any>) {
    var i = 0
    while (i < topicAndHandlerPairs.size) {
      subscriberCache.remove(topicAndHandlerPairs[i])
      i += 2
    }
  }

  internal fun deliverImmediately(connection: MessageBusConnectionImpl) {
    checkBusDisposed()

    // a light project is not disposed in tests properly, so, connection is not removed
    if (owner.isDisposed) {
      return
    }

    val queue = rootBus.queue
    val jobs = queue.queue
    if (jobs.isEmpty()) {
      return
    }

    val newJobs = deliverImmediately(connection, jobs) ?: return

    // add to queue to ensure that hasUndeliveredEvents works correctly
    for (i in newJobs.indices.reversed()) {
      jobs.addFirst(newJobs[i])
    }

    var error: Throwable? = null
    for (job in newJobs) {
      // remove here will be not linear as a job should be head (first element) in normal conditions
      jobs.removeFirstOccurrence(job)
      error = deliverMessage(job, queue, error)
    }
    error?.let(::throwError)
  }

  fun addMessageDeliveryListener(listener: MessageDeliveryListener) {
    messageDeliveryListeners.add(listener)
  }

  fun removeMessageDeliveryListener(listener: MessageDeliveryListener) {
    messageDeliveryListeners.remove(listener)
  }

  open fun disconnectPluginConnections(predicate: Predicate<Class<*>>) {
    for (holder in subscribers) {
      holder.disconnectIfNeeded(predicate)
    }
    subscriberCache.clear()
  }
}

@Internal
@VisibleForTesting
class RootBus(owner: MessageBusOwner) : CompositeMessageBus(owner) {
  private val compactionFutureRef = AtomicReference<CompletableFuture<*>?>()
  private val compactionRequest = AtomicInteger()
  private val emptyConnectionCounter = AtomicInteger()
  private val queueThreadLocal: ThreadLocal<MessageQueue> = ThreadLocal.withInitial { MessageQueue() }

  internal val queue: MessageQueue
    get() = queueThreadLocal.get()

  fun scheduleEmptyConnectionRemoving() {
    val counter = emptyConnectionCounter.incrementAndGet()
    if (counter < 128 || !emptyConnectionCounter.compareAndSet(counter, 0)) {
      return
    }

    // The first thread detected a need for compaction schedules a compaction task.
    // The task runs until all compaction requests are served.
    if (compactionRequest.incrementAndGet() == 1) {
      compactionFutureRef.set(CompletableFuture.runAsync({
                                                           var request: Int
                                                           do {
                                                             request = compactionRequest.get()
                                                             removeEmptyConnectionsRecursively()
                                                           }
                                                           while (!compactionRequest.compareAndSet(request, 0))
                                                         }, AppExecutorUtil.getAppExecutorService()))
    }
  }

  override fun dispose() {
    val compactionFuture = compactionFutureRef.getAndSet(null)
    compactionFuture?.cancel(false)
    compactionRequest.set(0)
    super.dispose()
  }

  fun removeDisposedHandlers(topic: Topic<*>, handler: Any) {
    val queue = queue.queue
    if (queue.isEmpty()) {
      return
    }

    queue.removeIf { message ->
      for (messageIndex in message.handlers.indices) {
        val messageHandler = message.handlers[messageIndex] ?: return@removeIf false
        if (message.topic === topic && messageHandler === handler) {
          message.handlers[messageIndex] = null
          return@removeIf message.handlers.size == 1
        }
      }
      false
    }
  }
}

internal class MessageQueue {
  @JvmField
  val queue: ArrayDeque<Message> = ArrayDeque<Message>()

  @JvmField
  var current: Message? = null
}

private val NA: Any = Any()

private sealed class BusState {
  data object Alive : BusState()
  data object Disposing : BusState()
  class Disposed(@JvmField val where: Throwable) : BusState()
}

private fun pumpWaiting(jobQueue: MessageQueue) {
  var error: Throwable? = null
  var job = jobQueue.current
  if (job != null) {
    if (job.bus.isDisposed) {
      MessageBusImpl.LOG.error("Accessing disposed message bus ${job.bus} (job=$job)")
    }
    else {
      error = deliverMessage(job = job, jobQueue = jobQueue, prevError = null)
    }
  }

  while (true) {
    job = jobQueue.queue.pollFirst() ?: break
    if (job.bus.isDisposed) {
      MessageBusImpl.LOG.error("Accessing disposed message bus ${job.bus} (job=$job)")
    }
    else {
      error = deliverMessage(job = job, jobQueue = jobQueue, prevError = error)
    }
  }
  error?.let(::throwError)
}

private fun deliverMessage(job: Message, jobQueue: MessageQueue, prevError: Throwable?): Throwable? {
  ClientId.withExplicitClientId(job.clientId).use {
    jobQueue.current = job
    val handlers = job.handlers
    var error = prevError
    var index = job.currentHandlerIndex
    val size = handlers.size
    val lastIndex = size - 1
    while (index < size) {
      if (index == lastIndex) {
        jobQueue.current = null
      }
      job.currentHandlerIndex++
      val handler = handlers[index]
      if (handler != null) {
        error = invokeListener(
          methodHandle = job.method,
          methodName = job.methodName,
          args = job.args,
          topic = job.topic,
          handler = handler,
          messageDeliveryListeners = job.bus.messageDeliveryListeners,
          prevError = error,
        )
      }
      if (++index != job.currentHandlerIndex) {
        // handler published some events, and message queue including a current job was processed as a result, so, stop processing
        return error
      }
    }
    return error
  }
}

internal open class MessagePublisher<L>(
  @JvmField protected val topic: Topic<L>,
  @JvmField protected val bus: MessageBusImpl,
) : InvocationHandler {
  final override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
    if (method.declaringClass == Any::class.java) {
      return EventDispatcher.handleObjectMethod(proxy, args, method.name)
    }

    if (topic.isImmediateDelivery) {
      bus.checkDisposed()
      publish(method = method, args = args, queue = null)
      return NA
    }

    val queue = bus.rootBus.queue
    if (!queue.queue.isEmpty()) {
      pumpWaiting(queue)
    }

    if (publish(method = method, args = args, queue = queue)) {
      // we must deliver messages now, even if currently processing message queue, because if published as part of handler invocation,
      // the handler code expects that message will be delivered immediately after publishing
      pumpWaiting(queue)
    }
    return NA
  }

  internal open fun publish(method: Method, args: Array<Any?>?, queue: MessageQueue?): Boolean {
    val handlers = bus.subscriberCache.computeIfAbsent(topic, bus::computeSubscribers)
    if (handlers.isEmpty()) {
      return false
    }

    executeOrAddToQueue(
      topic = topic,
      method = method,
      args = args,
      handlers = handlers,
      jobQueue = queue,
      prevError = null,
      bus = bus,
    )?.let(::throwError)
    return true
  }

  open fun preload() {
  }
}

internal fun executeOrAddToQueue(
  topic: Topic<*>,
  method: Method,
  args: Array<Any?>?,
  handlers: Array<Any?>,
  jobQueue: MessageQueue?,
  prevError: Throwable?,
  bus: MessageBusImpl,
): Throwable? {
  val methodHandle = MethodHandleCache.compute(method, args)
  if (jobQueue == null) {
    var error = prevError
    for (handler in handlers) {
      error = invokeListener(
        methodHandle = methodHandle,
        methodName = method.name,
        args = args,
        topic = topic,
        handler = handler ?: continue,
        messageDeliveryListeners = bus.messageDeliveryListeners,
        prevError = error,
      )
    }
    return error
  }
  else {
    jobQueue.queue.offerLast(Message(
      topic = topic,
      method = methodHandle,
      methodName = method.name,
      args = args,
      handlers = handlers,
      bus = bus,
    ))
    return prevError
  }
}

internal class ToParentMessagePublisher<L>(topic: Topic<L>, bus: MessageBusImpl) : MessagePublisher<L>(topic, bus), InvocationHandler {
  override fun preload() {
    // expected the only parent (project -> app)
    getOrComputeHandlers(bus)
    bus.parentBus?.let(::getOrComputeHandlers)
  }

  // args not-null
  override fun publish(method: Method, args: Array<Any?>?, queue: MessageQueue?): Boolean {
    var error: Throwable? = null
    var parentBus = bus
    var hasHandlers = false
    while (true) {
      // computeIfAbsent cannot be used here: https://youtrack.jetbrains.com/issue/IDEA-250464
      val handlers = getOrComputeHandlers(parentBus)
      if (handlers.isNotEmpty()) {
        hasHandlers = true
        error = executeOrAddToQueue(
          topic = topic,
          method = method,
          args = args,
          handlers = handlers,
          jobQueue = queue,
          prevError = error,
          bus = bus,
        )
      }
      parentBus = parentBus.parentBus ?: break
    }

    error?.let(::throwError)
    return hasHandlers
  }

  private fun getOrComputeHandlers(parentBus: MessageBusImpl): Array<Any?> {
    var handlers = parentBus.subscriberCache.get(topic)
    if (handlers == null) {
      handlers = parentBus.computeSubscribers(topic)
      val existing = parentBus.subscriberCache.putIfAbsent(topic, handlers)
      if (existing != null) {
        return existing
      }
    }
    return handlers
  }
}

/**
 * For `TO_DIRECT_CHILDREN` broadcast direction we don't need special clean logic because cache is computed per bus, exactly as for
 * `NONE`` broadcast direction.
 * And on publish, direct children of buses are queried to get message handlers.
 */
private fun clearSubscriberCacheOnConnectionTerminated(topicAndHandlerPairs: Array<Any>, bus: MessageBusImpl): Boolean {
  var isChildClearingNeeded = false
  var i = 0
  while (i < topicAndHandlerPairs.size) {
    val topic = topicAndHandlerPairs[i] as Topic<*>
    removeDisposedHandlers(topicAndHandlerPairs, i, topic, bus)
    val direction = topic.broadcastDirection
    if (direction != BroadcastDirection.TO_CHILDREN) {
      i += 2
      continue
    }

    // clear parents
    var parentBus = bus
    while (true) {
      parentBus = parentBus.parentBus ?: break
      removeDisposedHandlers(topicAndHandlerPairs, i, topic, parentBus)
    }
    if (bus.hasChildren()) {
      // clear children
      isChildClearingNeeded = true
    }
    i += 2
  }
  return isChildClearingNeeded
}

private fun removeDisposedHandlers(topicAndHandlerPairs: Array<Any>, index: Int, topic: Topic<*>, bus: MessageBusImpl) {
  val cachedHandlers = bus.subscriberCache.remove(topic) ?: return
  val handler = topicAndHandlerPairs[index + 1]
  // during immediate delivery, execution of one handler can lead to dispose of some connection,
  // and as a result, other handlers may become obsolete
  if (topic.isImmediateDelivery) {
    var i = 0
    val length = cachedHandlers.size
    while (i < length) {
      if (cachedHandlers[i] === handler) {
        cachedHandlers[i] = null
      }
      i++
    }
  }
  bus.rootBus.removeDisposedHandlers(topic, handler)
}

private fun deliverImmediately(connection: MessageBusConnectionImpl, jobs: Deque<Message>): List<Message>? {
  var newJobs: MutableList<Message>? = null
  // do not deliver messages as part of the iteration because during delivery another message maybe posted
  val jobIterator = jobs.iterator()
  while (jobIterator.hasNext()) {
    val job = jobIterator.next()
    var handlers: MutableList<Any>? = null
    val allHandlers = job.handlers
    var i = 0
    val length = allHandlers.size
    while (i < length) {
      val handler = allHandlers[i]
      if (handler != null && connection.bus === job.bus && connection.isMyHandler(job.topic, handler)) {
        allHandlers[i] = null
        if (handlers == null) {
          handlers = mutableListOf()
        }
        handlers.add(handler)
      }
      i++
    }

    if (handlers == null) {
      continue
    }

    if (allHandlers.size == handlers.size) {
      jobIterator.remove()
    }
    val filteredJob = Message(job.topic, job.method, job.methodName, job.args, handlers.toTypedArray(), job.bus)
    if (newJobs == null) {
      newJobs = mutableListOf()
    }
    newJobs.add(filteredJob)
  }
  return newJobs
}

private fun invokeListener(
  methodHandle: MethodHandle,
  methodName: String,
  args: Array<Any?>?,
  topic: Topic<*>,
  handler: Any,
  messageDeliveryListeners: Set<MessageDeliveryListener>,
  prevError: Throwable?,
): Throwable? {
  try {
    //println("${topic.displayName} ${topic.isImmediateDelivery}: $methodName(${args.contentToString()})")
    if (handler is MessageHandler) {
      handler.handle(methodHandle, *(args ?: ArrayUtilRt.EMPTY_OBJECT_ARRAY))
    }
    else if (messageDeliveryListeners.isEmpty()) {
      invokeMethod(handler, args, methodHandle)
    }
    else {
      val startTime = System.nanoTime()
      invokeMethod(handler, args, methodHandle)
      for (listener in messageDeliveryListeners) {
        listener.messageDelivered(topic, methodName, handler, System.nanoTime() - startTime)
      }
    }
    return prevError
  }
  catch (_: AbstractMethodError) {
    // do nothing for AbstractMethodError - this listener just does not implement something newly added yet
    return prevError
  }
  catch (e: CancellationException) {
    if (isMessageBusErrorPropagationEnabled) {
      // CancellationException is rethrown only after executing all handlers
      return mergeErrors(prevError, e)
    }
    else {
      // ignore cancellation exception
      return prevError
    }
  }
  catch (e: Throwable) {
    val detailedError = when (e) {
      is AssertionError -> e
      else -> RuntimeException("Cannot invoke (" +
                               "class=${handler::class.java.simpleName}, " +
                               "method=${methodName}, " +
                               "topic=${topic.displayName}" +
                               ")", e)
    }

    if (isMessageBusErrorPropagationEnabled) {
      return mergeErrors(prevError, detailedError)
    }
    else {
      MessageBusImpl.LOG.error(e)
      return prevError
    }
  }
}

private fun mergeErrors(prevError: Throwable?, newError: Throwable): Throwable {
  if (prevError == null) {
    return newError
  }
  else {
    prevError.addSuppressed(newError)
    return prevError
  }
}

private fun invokeMethod(handler: Any, args: Array<Any?>?, methodHandle: MethodHandle) {
  if (args == null) {
    methodHandle.invoke(handler)
  }
  else {
    methodHandle.bindTo(handler).invokeExact(args)
  }
}

internal fun throwError(error: Throwable) {
  MessageBusImpl.LOG.assertTrue(isMessageBusErrorPropagationEnabled)

  val suppressed = error.suppressed
  if (suppressed.size > 1) {
    suppressed.firstOrNull { it is ProcessCanceledException || it is CancellationException }?.let { throw it }
  }
  throw error
}

private class SimpleMessageBusConnectionImpl(bus: MessageBusImpl) : BaseBusConnection(bus), SimpleMessageBusConnection {
  override fun disconnect() {
    val bus = bus ?: return
    this.bus = null
    // reset as bus will not remove disposed connection from a list immediately
    bus.notifyConnectionTerminated(subscriptions.getAndSet(ArrayUtilRt.EMPTY_OBJECT_ARRAY))
  }
}