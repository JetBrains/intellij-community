// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.messages.impl

import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.util.ArrayUtilRt
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.messages.*
import com.intellij.util.messages.Topic.BroadcastDirection
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

    fun collectHandlers(topic: Topic<*>, result: MutableList<Any>)

    fun disconnectIfNeeded(predicate: Predicate<Class<*>>)
  }

  companion object {
    @JvmField
    internal val LOG = Logger.getInstance(MessageBusImpl::class.java)
  }

  @JvmField
  internal val publisherCache: ConcurrentMap<Topic<*>, Any> = ConcurrentHashMap()
  @JvmField
  internal val subscribers = ConcurrentLinkedQueue<MessageHandlerHolder>()

  // caches subscribers for this bus and its children or parent, depending on the topic's broadcast policy
  @JvmField
  internal val subscriberCache = ConcurrentHashMap<Topic<*>, Array<Any?>>()
  @JvmField
  internal val parentBus: CompositeMessageBus?
  @JvmField
  internal val rootBus: RootBus
  @JvmField
  internal val owner: MessageBusOwner

  // 0 active, 1 dispose in progress 2 disposed
  private var disposeState = 0

  // separate disposable must be used, because container will dispose bus connections in a separate step
  private var connectionDisposable: Disposable? = Disposer.newDisposable()
  @JvmField
  internal var messageDeliveryListener: MessageDeliveryListener? = null

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

  override fun getParent(): MessageBus? = parentBus

  override fun toString() = "MessageBus(owner=$owner, disposeState= $disposeState)"

  override fun connect() = connect(connectionDisposable!!)

  override fun connect(parentDisposable: Disposable): MessageBusConnection {
    checkNotDisposed()
    val connection = MessageBusConnectionImpl(this)
    subscribers.add(connection)
    Disposer.register(parentDisposable, connection)
    return connection
  }

  override fun simpleConnect(): SimpleMessageBusConnection {
    // avoid registering in Dispose tree, default handler and deliverImmediately are not supported
    checkNotDisposed()
    val connection = SimpleMessageBusConnectionImpl(this)
    subscribers.add(connection)
    return connection
  }

  override fun <L : Any> syncPublisher(topic: Topic<L>): L {
    if (isDisposed) {
      PluginException.logPluginError(LOG, "Already disposed: $this", null, topic.javaClass)
    }
    @Suppress("UNCHECKED_CAST")
    return publisherCache.computeIfAbsent(topic) { topic1: Topic<*> ->
      val aClass = topic1.listenerClass
      Proxy.newProxyInstance(aClass.classLoader, arrayOf(aClass), createPublisher(topic1, topic1.broadcastDirection))
    } as L
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
    disposeState = DISPOSE_IN_PROGRESS
    Disposer.disposeChildren(connectionDisposable!!) { true }
  }

  fun disposeConnection() {
    Disposer.dispose(connectionDisposable!!)
    connectionDisposable = null
  }

  override fun dispose() {
    if (disposeState == DISPOSED_STATE) {
      LOG.error("Already disposed: $this")
    }

    disposeState = DISPOSED_STATE
    disposeChildren()
    connectionDisposable?.let {
      Disposer.dispose(it)
    }
    rootBus.queue.queue.removeIf { it.bus === this }
    parentBus?.onChildBusDisposed(this)
  }

  protected open fun disposeChildren() {
  }

  override fun isDisposed() = disposeState == DISPOSED_STATE || owner.isDisposed

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

  internal fun checkNotDisposed() {
    if (isDisposed) {
      LOG.error("Already disposed: $this")
    }
  }

  open fun doComputeSubscribers(topic: Topic<*>, result: MutableList<Any>, subscribeLazyListeners: Boolean) {
    // todo check that handler implements method (not a default implementation)
    for (subscriber in subscribers) {
      if (!subscriber.isDisposed) {
        subscriber.collectHandlers(topic, result)
      }
    }
  }

  open fun computeSubscribers(topic: Topic<*>): Array<Any?> {
    val result = mutableListOf<Any>()
    doComputeSubscribers(topic = topic, result = result, subscribeLazyListeners = true)
    return if (result.isEmpty()) ArrayUtilRt.EMPTY_OBJECT_ARRAY else result.toTypedArray()
  }

  open fun hasChildren(): Boolean = false

  fun notifyOnSubscription(topic: Topic<*>) {
    subscriberCache.remove(topic)
    if (topic.broadcastDirection != BroadcastDirection.TO_CHILDREN) {
      return
    }

    // Clear parents because parent caches subscribers for TO_CHILDREN direction on it's level and child levels.
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

  open fun notifyOnSubscriptionToTopicToChildren(topic: Topic<*>) {
  }

  open fun removeEmptyConnectionsRecursively() {
    subscribers.removeIf { it.isDisposed }
  }

  open fun notifyConnectionTerminated(topicAndHandlerPairs: Array<Any>): Boolean {
    if (disposeState != 0) {
      return false
    }

    rootBus.scheduleEmptyConnectionRemoving()
    return clearSubscriberCacheOnConnectionTerminated(topicAndHandlerPairs, this)
  }

  // this method is used only in CompositeMessageBus.notifyConnectionTerminated to clear subscriber cache in children
  open fun clearSubscriberCache(topicAndHandlerPairs: Array<Any>) {
    var i = 0
    while (i < topicAndHandlerPairs.size) {
      subscriberCache.remove(topicAndHandlerPairs[i])
      i += 2
    }
  }

  internal fun deliverImmediately(connection: MessageBusConnectionImpl) {
    if (disposeState == DISPOSED_STATE) {
      LOG.error("Already disposed: $this")
    }

    // light project is not disposed in tests properly, so, connection is not removed
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

    var exceptions: MutableList<Throwable>? = null
    for (job in newJobs) {
      // remove here will be not linear as job should be head (first element) in normal conditions
      jobs.removeFirstOccurrence(job)
      exceptions = deliverMessage(job, queue, exceptions)
    }
    exceptions?.let(::throwExceptions)
  }

  fun setMessageDeliveryListener(listener: MessageDeliveryListener?) {
    check(messageDeliveryListener == null || listener == null) { "Already set: $messageDeliveryListener" }
    messageDeliveryListener = listener
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
  val queue = ArrayDeque<Message>()

  @JvmField
  var current: Message? = null
}

private val NA = Any()
private const val DISPOSE_IN_PROGRESS = 1
private const val DISPOSED_STATE = 2

private fun pumpWaiting(jobQueue: MessageQueue) {
  var exceptions: MutableList<Throwable>? = null
  var job = jobQueue.current
  if (job != null) {
    if (job.bus.isDisposed) {
      MessageBusImpl.LOG.error("Accessing disposed message bus ${job.bus}")
    }
    else {
      exceptions = deliverMessage(job, jobQueue, null)
    }
  }

  while (true) {
    job = jobQueue.queue.pollFirst() ?: break
    if (job.bus.isDisposed) {
      MessageBusImpl.LOG.error("Accessing disposed message bus ${job.bus}")
    }
    else {
      exceptions = deliverMessage(job, jobQueue, exceptions)
    }
  }
  exceptions?.let(::throwExceptions)
}

private fun deliverMessage(job: Message, jobQueue: MessageQueue, prevExceptions: MutableList<Throwable>?): MutableList<Throwable>? {
  withClientId(job.clientId).use {
    jobQueue.current = job
    val handlers = job.handlers
    var exceptions = prevExceptions
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
        exceptions = invokeListener(methodHandle = job.method,
                                    methodName = job.methodName,
                                    args = job.args,
                                    topic = job.topic,
                                    handler = handler,
                                    messageDeliveryListener = job.bus.messageDeliveryListener,
                                    prevExceptions = exceptions)
      }
      if (++index != job.currentHandlerIndex) {
        // handler published some event and message queue including current job was processed as result, so, stop processing
        return exceptions
      }
    }
    return exceptions
  }
}

internal open class MessagePublisher<L>(@JvmField protected val topic: Topic<L>,
                                        @JvmField protected val bus: MessageBusImpl) : InvocationHandler {
  final override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
    if (method.declaringClass == Any::class.java) {
      return EventDispatcher.handleObjectMethod(proxy, args, method.name)
    }

    if (topic.isImmediateDelivery) {
      bus.checkNotDisposed()
      publish(method = method, args = args, queue = null)
      return NA
    }

    val queue = bus.rootBus.queue
    if (!queue.queue.isEmpty()) {
      pumpWaiting(queue)
    }

    if (publish(method = method, args = args, queue = queue)) {
      // we must deliver messages now even if currently processing message queue, because if published as part of handler invocation,
      // handler code expects that message will be delivered immediately after publishing
      pumpWaiting(queue)
    }
    return NA
  }

  internal open fun publish(method: Method, args: Array<Any?>?, queue: MessageQueue?): Boolean {
    val handlers = bus.subscriberCache.computeIfAbsent(topic, bus::computeSubscribers)
    if (handlers.isEmpty()) {
      return false
    }

    executeOrAddToQueue(topic = topic,
                        method = method,
                        args = args,
                        handlers = handlers,
                        jobQueue = queue,
                        prevExceptions = null,
                        bus = bus)?.let(::throwExceptions)
    return true
  }
}

internal fun executeOrAddToQueue(topic: Topic<*>,
                                 method: Method,
                                 args: Array<Any?>?,
                                 handlers: Array<Any?>,
                                 jobQueue: MessageQueue?,
                                 prevExceptions: MutableList<Throwable>?,
                                 bus: MessageBusImpl): MutableList<Throwable>? {
  val methodHandle = MethodHandleCache.compute(method, args)
  if (jobQueue == null) {
    var exceptions = prevExceptions
    for (handler in handlers) {
      exceptions = invokeListener(methodHandle = methodHandle,
                                  methodName = method.name,
                                  args = args,
                                  topic = topic,
                                  handler = handler ?: continue,
                                  messageDeliveryListener = bus.messageDeliveryListener,
                                  prevExceptions = exceptions)
    }
    return exceptions
  }
  else {
    jobQueue.queue.offerLast(Message(topic = topic,
                                     method = methodHandle,
                                     methodName = method.name,
                                     args = args,
                                     handlers = handlers,
                                     bus = bus))
    return prevExceptions
  }
}

internal class ToParentMessagePublisher<L>(topic: Topic<L>, bus: MessageBusImpl) : MessagePublisher<L>(topic, bus), InvocationHandler {
  // args not-null
  override fun publish(method: Method, args: Array<Any?>?, queue: MessageQueue?): Boolean {
    var exceptions: MutableList<Throwable>? = null
    var parentBus = bus
    var hasHandlers = false
    while (true) {
      // computeIfAbsent cannot be used here: https://youtrack.jetbrains.com/issue/IDEA-250464
      var handlers = parentBus.subscriberCache.get(topic)
      if (handlers == null) {
        handlers = parentBus.computeSubscribers(topic)
        val existing = parentBus.subscriberCache.putIfAbsent(topic, handlers)
        if (existing != null) {
          handlers = existing
        }
      }

      if (!handlers.isEmpty()) {
        hasHandlers = true
        exceptions = executeOrAddToQueue(topic, method, args, handlers, queue, exceptions, bus)
      }
      parentBus = parentBus.parentBus ?: break
    }

    exceptions?.let(::throwExceptions)
    return hasHandlers
  }
}

/**
 * For TO_DIRECT_CHILDREN broadcast direction we don't need special clean logic because cache is computed per bus, exactly as for
 * NONE broadcast direction. And on publish, direct children of bus are queried to get message handlers.
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
  // during immediate delivery execution of one handler can lead to dispose of some connection
  // and as result other handlers may become obsolete
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
  // do not deliver messages as part of iteration because during delivery another messages maybe posted
  val jobIterator = jobs.iterator()
  while (jobIterator.hasNext()) {
    val job = jobIterator.next()
    var connectionHandlers: MutableList<Any>? = null
    val allHandlers = job.handlers
    var i = 0
    val length = allHandlers.size
    while (i < length) {
      val handler = allHandlers[i]
      if (handler != null && connection.bus === job.bus && connection.isMyHandler(job.topic, handler)) {
        allHandlers[i] = null
        if (connectionHandlers == null) {
          connectionHandlers = mutableListOf()
        }
        connectionHandlers.add(handler)
      }
      i++
    }

    if (connectionHandlers == null) {
      continue
    }

    if (allHandlers.size == connectionHandlers.size) {
      jobIterator.remove()
    }
    val filteredJob = Message(topic = job.topic,
                              method = job.method,
                              methodName = job.methodName,
                              args = job.args,
                              handlers = connectionHandlers.toTypedArray(),
                              bus = job.bus)
    if (newJobs == null) {
      newJobs = mutableListOf()
    }
    newJobs.add(filteredJob)
  }
  return newJobs
}

private fun invokeListener(methodHandle: MethodHandle,
                           methodName: String,
                           args: Array<Any?>?,
                           topic: Topic<*>,
                           handler: Any,
                           messageDeliveryListener: MessageDeliveryListener?,
                           prevExceptions: MutableList<Throwable>?): MutableList<Throwable>? {
  try {
    //println("${topic.displayName} ${topic.isImmediateDelivery}: $methodName(${args.contentToString()})")
    if (handler is MessageHandler) {
      handler.handle(methodHandle, *(args ?: ArrayUtilRt.EMPTY_OBJECT_ARRAY))
    }
    else if (messageDeliveryListener == null) {
      invokeMethod(handler, args, methodHandle)
    }
    else {
      val startTime = System.nanoTime()
      invokeMethod(handler, args, methodHandle)
      messageDeliveryListener.messageDelivered(topic, methodName, handler, System.nanoTime() - startTime)
    }
  }
  catch (e: AbstractMethodError) {
    // do nothing for AbstractMethodError - this listener just does not implement something newly added yet
  }
  catch (e: Throwable) {
    val exceptions = prevExceptions ?: mutableListOf()
    // ProcessCanceledException is rethrown only after executing all handlers
    if (e is ProcessCanceledException || e is AssertionError || e is CancellationException) {
      exceptions.add(e)
    }
    else {
      exceptions.add(RuntimeException("Cannot invoke (" +
                                      "class=${handler::class.java.simpleName}, " +
                                      "method=${methodName}, " +
                                      "topic=${topic.displayName}" +
                                      ")", e))
    }
    return exceptions
  }
  return prevExceptions
}

private fun invokeMethod(handler: Any, args: Array<Any?>?, methodHandle: MethodHandle) {
  if (args == null) {
    methodHandle.invoke(handler)
  }
  else {
    methodHandle.bindTo(handler).invokeExact(args)
  }
}

internal fun throwExceptions(exceptions: List<Throwable>) {
  if (exceptions.size == 1) {
    throw exceptions[0]
  }

  exceptions.firstOrNull { it is ProcessCanceledException || it is CancellationException }?.let { throw it }

  throw CompoundRuntimeException(exceptions)
}