// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.codeWithMe.ClientId;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.*;
import com.intellij.util.messages.Topic.BroadcastDirection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

@ApiStatus.Internal
public class MessageBusImpl implements MessageBus {
  interface MessageHandlerHolder {
    <L> void collectHandlers(@NotNull Topic<L> topic, @NotNull List<? super L> result);

    void disconnectIfNeeded(@NotNull Predicate<? super Class<?>> predicate);

    boolean isDisposed();
  }

  static final Logger LOG = Logger.getInstance(MessageBusImpl.class);
  private static final Object NA = new Object();

  final ThreadLocal<JobQueue> messageQueue = ThreadLocal.withInitial(JobQueue::new);

  /**
   * Root's order is empty
   * Child bus's order is its parent order plus one more element, an int that's bigger than that of all sibling buses that come before
   * Sorting by these vectors lexicographically gives DFS order
   */
  final int[] order;

  final ConcurrentMap<Topic<?>, Object> publisherCache = new ConcurrentHashMap<>();

  final Collection<MessageHandlerHolder> subscribers = new ConcurrentLinkedQueue<>();
  // caches subscribers for this bus and its children or parent, depending on the topic's broadcast policy
  final Map<Topic<?>, List<?>> subscriberCache = new ConcurrentHashMap<>();

  final @Nullable CompositeMessageBus parentBus;
  final RootBus rootBus;

  final MessageBusOwner owner;
  private static final int DISPOSE_IN_PROGRESS = 1;
  private static final int DISPOSED_STATE = 2;
  // 0 active, 1 dispose in progress 2 disposed
  private int disposeState;
  // separate disposable must be used, because container will dispose bus connections in a separate step
  private Disposable connectionDisposable = Disposer.newDisposable();
  MessageDeliveryListener messageDeliveryListener;

  MessageBusImpl(@NotNull MessageBusOwner owner, @NotNull CompositeMessageBus parentBus) {
    this.owner = owner;
    this.parentBus = parentBus;
    rootBus = parentBus.rootBus;

    MessageBusImpl p = this;
    while ((p = p.parentBus) != null) {
      p.subscriberCache.clear();
    }

    order = parentBus.addChild(this);
  }

  // root message bus constructor
  MessageBusImpl(@NotNull MessageBusOwner owner) {
    this.owner = owner;
    order = ArrayUtil.EMPTY_INT_ARRAY;
    rootBus = (RootBus)this;
    parentBus = null;
  }

  @Override
  public final MessageBus getParent() {
    return parentBus;
  }

  @Override
  public final String toString() {
    return "MessageBus(owner=" + owner + ", disposeState= " + disposeState + ")";
  }

  @Override
  public final @NotNull MessageBusConnection connect() {
    return connect(connectionDisposable);
  }

  @Override
  public final @NotNull MessageBusConnectionImpl connect(@NotNull Disposable parentDisposable) {
    checkNotDisposed();
    MessageBusConnectionImpl connection = new MessageBusConnectionImpl(this);
    subscribers.add(connection);
    Disposer.register(parentDisposable, connection);
    return connection;
  }

  @Override
  public final @NotNull SimpleMessageBusConnection simpleConnect() {
    // avoid registering in Dispose tree, default handler and deliverImmediately are not supported
    checkNotDisposed();
    SimpleMessageBusConnectionImpl connection = new SimpleMessageBusConnectionImpl(this);
    subscribers.add(connection);
    return connection;
  }

  @Override
  public final @NotNull <L> L syncPublisher(@NotNull Topic<L> topic) {
    checkNotDisposed();
    //noinspection unchecked
    return (L)publisherCache.computeIfAbsent(topic, topic1 -> {
      Class<?> listenerClass = topic1.getListenerClass();
      return Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, createPublisher(topic1, topic1.getBroadcastDirection()));
    });
  }

  @NotNull <L> MessagePublisher<L> createPublisher(@NotNull Topic<L> topic, @NotNull BroadcastDirection direction) {
    if (direction == BroadcastDirection.TO_PARENT) {
      return new ToParentMessagePublisher<>(topic, this);
    }
    else if (direction == BroadcastDirection.TO_DIRECT_CHILDREN) {
      throw new IllegalArgumentException("Broadcast direction TO_DIRECT_CHILDREN is allowed only for app level message bus. " +
                                         "Please publish to app level message bus or change topic broadcast direction to NONE or TO_PARENT");
    }
    else {
      // warn as there is quite a lot such violations
      LOG.warn("Broadcast direction TO_CHILDREN  is not allowed for module level message bus. Please change to NONE or TO_PARENT");
      return new MessagePublisher<>(topic, this);
    }
  }

  static class MessagePublisher<L> implements InvocationHandler {
    protected final @NotNull Topic<L> topic;
    protected final @NotNull MessageBusImpl bus;

    MessagePublisher(@NotNull Topic<L> topic, @NotNull MessageBusImpl bus) {
      this.topic = topic;
      this.bus = bus;
    }

    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) {
      if (method.getDeclaringClass() == Object.class) {
        return EventDispatcher.handleObjectMethod(proxy, args, method.getName());
      }

      bus.checkNotDisposed();

      boolean isImmediateDelivery = topic.isImmediateDelivery();
      Set<MessageBusImpl> busQueue;
      JobQueue jobQueue;
      if (isImmediateDelivery) {
        busQueue = null;
        jobQueue = null;
      }
      else {
        busQueue = bus.rootBus.myWaitingBuses.get();
        jobQueue = bus.messageQueue.get();
        pumpMessages(busQueue);
      }

      if (publish(method, args, jobQueue) && !isImmediateDelivery) {
        busQueue.add(bus);
        // we must deliver messages now even if currently processing message queue, because if published as part of handler invocation,
        // handler code expects that message will be delivered immediately after publishing
        pumpMessages(busQueue);
      }
      return NA;
    }

    boolean publish(@NotNull Method method, Object[] args, @Nullable JobQueue jobQueue) {
      //noinspection unchecked
      List<L> handlers = (List<L>)bus.subscriberCache.computeIfAbsent(topic, topic1 -> bus.computeSubscribers((Topic<L>)topic1));
      if (handlers.isEmpty()) {
        return false;
      }

      List<Throwable> exceptions = executeOrAddToQueue(topic, method, args, handlers, jobQueue, bus.messageDeliveryListener, null);
      if (exceptions != null) {
        EventDispatcher.throwExceptions(exceptions);
      }
      return true;
    }

    // args not null
    List<Throwable> executeOrAddToQueue(@NotNull Topic<L> topic,
                                        @NotNull Method method,
                                        Object[] args,
                                        @NotNull List<L> handlers,
                                        @Nullable JobQueue jobQueue,
                                        @Nullable MessageDeliveryListener messageDeliveryListener,
                                        @Nullable List<Throwable> exceptions) {
      MethodHandle methodHandle = MethodHandleCache.compute(method, args);
      if (jobQueue == null) {
        for (L handler : handlers) {
          exceptions = invokeListener(methodHandle, method.getName(), args, topic, handler, messageDeliveryListener, exceptions);
        }
      }
      else {
        jobQueue.queue.offerLast(new Message<>(topic, methodHandle, method.getName(), args, handlers));
      }
      return exceptions;
    }
  }

  static final class ToParentMessagePublisher<L> extends MessagePublisher<L> implements InvocationHandler {
    ToParentMessagePublisher(@NotNull Topic<L> topic, @NotNull MessageBusImpl bus) {
      super(topic, bus);
    }

    // args not-null
    @Override
    boolean publish(@NotNull Method method, Object[] args, @Nullable JobQueue jobQueue) {
      List<Throwable> exceptions = null;
      MessageBusImpl parentBus = bus;
      boolean hasHandlers = false;
      do {
        // computeIfAbsent cannot be used here: https://youtrack.jetbrains.com/issue/IDEA-250464
        @SuppressWarnings("unchecked")
        List<L> handlers = (List<L>)parentBus.subscriberCache.get(topic);
        if (handlers == null) {
          handlers = parentBus.computeSubscribers(topic);
          @SuppressWarnings("unchecked")
          List<L> existing = (List<L>)parentBus.subscriberCache.putIfAbsent(topic, handlers);
          if (existing != null) {
            handlers = existing;
          }
        }

        if (handlers.isEmpty()) {
          continue;
        }

        hasHandlers = true;
        exceptions = executeOrAddToQueue(topic, method, args, handlers, jobQueue, bus.messageDeliveryListener, exceptions);
      }
      while ((parentBus = parentBus.parentBus) != null);

      if (exceptions != null) {
        EventDispatcher.throwExceptions(exceptions);
      }
      return hasHandlers;
    }
  }

  public final void disposeConnectionChildren() {
    // avoid any work on notifyConnectionTerminated
    disposeState = DISPOSE_IN_PROGRESS;
    Disposer.disposeChildren(connectionDisposable, null);
  }

  public final void disposeConnection() {
    Disposer.dispose(connectionDisposable);
    connectionDisposable = null;
  }

  @Override
  public void dispose() {
    if (disposeState == DISPOSED_STATE) {
      LOG.error("Already disposed: " + this);
    }

    disposeState = DISPOSED_STATE;

    disposeChildren();

    if (connectionDisposable != null) {
      Disposer.dispose(connectionDisposable);
    }

    JobQueue jobs = messageQueue.get();
    messageQueue.remove();
    if (!jobs.queue.isEmpty()) {
      LOG.error("Not delivered events in the queue: " + jobs);
    }

    if (parentBus == null) {
      rootBus.myWaitingBuses.remove();
    }
    else {
      parentBus.onChildBusDisposed(this);
    }
  }

  void disposeChildren() {
  }

  @Override
  public final boolean isDisposed() {
    return disposeState == DISPOSED_STATE || owner.isDisposed();
  }

  @Override
  public final boolean hasUndeliveredEvents(@NotNull Topic<?> topic) {
    if (isDisposed()) {
      return false;
    }

    Set<MessageBusImpl> waitingBuses = rootBus.myWaitingBuses.get();
    if (waitingBuses == null || waitingBuses.isEmpty()) {
      return false;
    }

    for (MessageBusImpl bus : waitingBuses) {
      JobQueue jobQueue = bus.messageQueue.get();
      Message<?> current = jobQueue.current;
      if (current != null && current.topic == topic) {
        return true;
      }

      for (Message<?> message : jobQueue.queue) {
        if (message.topic == topic) {
          return true;
        }
      }
    }
    return false;
  }

  private void checkNotDisposed() {
    if (isDisposed()) {
      LOG.error("Already disposed: " + this);
    }
  }

  <L> void doComputeSubscribers(@NotNull Topic<L> topic, @NotNull List<? super L> result, boolean subscribeLazyListeners) {
    // todo check that handler implements method (not a default implementation)
    for (MessageHandlerHolder subscriber : subscribers) {
      if (!subscriber.isDisposed()) {
        subscriber.collectHandlers(topic, result);
      }
    }
  }

  @NotNull <L> List<L> computeSubscribers(@NotNull Topic<L> topic) {
    List<L> result = new ArrayList<>();
    doComputeSubscribers(topic, result, true);
    return result.isEmpty() ? Collections.emptyList() : result;
  }

  private void jobRemoved(@NotNull JobQueue jobQueue) {
    if (jobQueue.current == null && jobQueue.queue.isEmpty()) {
      rootBus.myWaitingBuses.get().remove(this);
    }
  }

  private static void pumpMessages(@NotNull Set<? extends MessageBusImpl> waitingBuses) {
    List<MessageBusImpl> liveBuses = new ArrayList<>(waitingBuses.size());
    for (MessageBusImpl bus : waitingBuses) {
      if (bus.isDisposed()) {
        waitingBuses.remove(bus);
        LOG.error("Accessing disposed message bus " + bus);
        continue;
      }

      liveBuses.add(bus);
    }

    if (!liveBuses.isEmpty()) {
      pumpWaitingBuses(liveBuses);
    }
  }

  private static void pumpWaitingBuses(@NotNull List<? extends MessageBusImpl> buses) {
    List<Throwable> exceptions = null;
    for (MessageBusImpl bus : buses) {
      if (bus.isDisposed()) {
        continue;
      }

      JobQueue jobQueue = bus.messageQueue.get();
      Message<?> job = jobQueue.current;
      if (job != null) {
        exceptions = bus.deliverMessage(job, jobQueue, bus.messageDeliveryListener, exceptions);
      }

      while ((job = jobQueue.queue.pollFirst()) != null) {
        exceptions = bus.deliverMessage(job, jobQueue, bus.messageDeliveryListener, exceptions);
      }
    }

    if (exceptions != null) {
      EventDispatcher.throwExceptions(exceptions);
    }
  }

  private @Nullable <L> List<Throwable> deliverMessage(@NotNull Message<L> job,
                                                       @NotNull JobQueue jobQueue,
                                                       @Nullable MessageDeliveryListener messageDeliveryListener,
                                                       @Nullable List<Throwable> exceptions) {
    ClientId oldClientId = ClientId.getCurrentOrNull();
    try {
      ClientId.trySetCurrentClientId(job.clientId);
      jobQueue.current = job;
      List<L> handlers = job.handlers;
      for (int index = job.currentHandlerIndex, size = handlers.size(), lastIndex = size - 1; index < size; ) {
        if (index == lastIndex) {
          jobQueue.current = null;
          jobRemoved(jobQueue);
        }

        job.currentHandlerIndex++;
        exceptions = invokeListener(job.method, job.methodName, job.args, job.topic, handlers.get(index), messageDeliveryListener, exceptions);
        if (++index != job.currentHandlerIndex) {
          // handler published some event and message queue including current job was processed as result, so, stop processing
          return exceptions;
        }
      }
      return exceptions;
    }
    finally {
      ClientId.trySetCurrentClientId(oldClientId);
    }
  }

  boolean hasChildren() {
    return false;
  }

  final void notifyOnSubscription(@NotNull Topic<?> topic) {
    subscriberCache.remove(topic);
    if (topic.getBroadcastDirection() != BroadcastDirection.TO_CHILDREN) {
      return;
    }

    // Clear parents because parent caches subscribers for TO_CHILDREN direction on it's level and child levels.
    // So, on subscription to child bus (this instance) parent cache must be invalidated.
    MessageBusImpl parentBus = this;
    while ((parentBus = parentBus.parentBus) != null) {
      parentBus.subscriberCache.remove(topic);
    }

    if (hasChildren()) {
      notifyOnSubscriptionToTopicToChildren(topic);
    }
  }

  void notifyOnSubscriptionToTopicToChildren(@NotNull Topic<?> topic) {
  }

  // return false if no subscription
  protected static boolean clearSubscriberCache(@NotNull MessageBusImpl bus,
                                                @Nullable Map<Topic<?>, Object> handlers,
                                                @Nullable Topic<?> singleTopic) {
    if (handlers == null) {
      if (singleTopic == null) {
        bus.subscriberCache.clear();
        return true;
      }
      else {
        return bus.subscriberCache.remove(singleTopic) != null;
      }
    }
    // forEach must be used here as map here it is SmartFMap - avoid temporary map entries creation
    ToChildrenTopicSubscriberCleaner cleaner = new ToChildrenTopicSubscriberCleaner(bus);
    handlers.forEach(cleaner);
    return cleaner.removed;
  }

  private static final class ToChildrenTopicSubscriberCleaner implements BiConsumer<Topic<?>, Object> {
    private final MessageBusImpl bus;
    boolean removed;

    ToChildrenTopicSubscriberCleaner(@NotNull MessageBusImpl bus) {
      this.bus = bus;
    }

    @Override
    public void accept(Topic<?> topic, Object __) {
      // other directions are already removed
      BroadcastDirection direction = topic.getBroadcastDirection();
      if (direction == BroadcastDirection.TO_CHILDREN) {
        if (bus.subscriberCache.remove(topic) != null) {
          removed = true;
        }
      }
    }
  }

  void removeEmptyConnectionsRecursively() {
    subscribers.removeIf(MessageHandlerHolder::isDisposed);
  }

  boolean notifyConnectionTerminated(Object @NotNull [] topicAndHandlerPairs) {
    if (disposeState != 0) {
      return false;
    }

    rootBus.scheduleEmptyConnectionRemoving();

    return clearSubscriberCacheOnConnectionTerminated(topicAndHandlerPairs, this);
  }

  private static boolean clearSubscriberCacheOnConnectionTerminated(Object @NotNull [] topicAndHandlerPairs, @NotNull MessageBusImpl bus) {
    boolean isChildClearingNeeded = false;

    for (int i = 0; i < topicAndHandlerPairs.length; i += 2) {
      Topic<?> topic = (Topic<?>)topicAndHandlerPairs[i];
      if (bus.subscriberCache.remove(topic) != null) {
        bus.removeDisposedHandlers(topic, topicAndHandlerPairs[i + 1]);
      }

      BroadcastDirection direction = topic.getBroadcastDirection();
      if (direction != BroadcastDirection.TO_CHILDREN) {
        continue;
      }

      // clear parents
      MessageBusImpl parentBus = bus;
      while ((parentBus = parentBus.parentBus) != null) {
        if (parentBus.subscriberCache.remove(topic) != null) {
          parentBus.removeDisposedHandlers(topic, topicAndHandlerPairs[i + 1]);
        }
      }

      if (bus.hasChildren()) {
        // clear children
        isChildClearingNeeded = true;
      }
    }
    return isChildClearingNeeded;
  }

  // this method is used only in CompositeMessageBus.notifyConnectionTerminated to clear subscriber cache in children
  void clearSubscriberCache(Object @NotNull [] topicAndHandlerPairs) {
    for (int i = 0; i < topicAndHandlerPairs.length; i += 2) {
      subscriberCache.remove(topicAndHandlerPairs[i]);
    }
  }

  final void deliverImmediately(@NotNull MessageBusConnectionImpl connection) {
    if (disposeState == DISPOSED_STATE) {
      LOG.error("Already disposed: " + this);
    }

    // light project is not disposed in tests properly, so, connection is not removed
    if (owner.isDisposed()) {
      return;
    }

    JobQueue jobQueue = messageQueue.get();
    //noinspection rawtypes
    @SuppressWarnings("unchecked")
    Deque<Message<Object>> jobs = (Deque)jobQueue.queue;
    if (jobs.isEmpty()) {
      return;
    }

    List<Message<Object>> newJobs = deliverImmediately(connection, jobs);

    if (newJobs == null) {
      return;
    }

    // add to queue to ensure that hasUndeliveredEvents works correctly
    for (int i = newJobs.size() - 1; i >= 0; i--) {
      jobs.addFirst(newJobs.get(i));
    }

    List<Throwable> exceptions = null;
    for (Message<?> job : newJobs) {
      // remove here will be not linear as job should be head (first element) in normal conditions
      jobs.removeFirstOccurrence(job);
      exceptions = deliverMessage(job, jobQueue, messageDeliveryListener, exceptions);
    }

    if (exceptions != null) {
      EventDispatcher.throwExceptions(exceptions);
    }
  }

  private static @Nullable <L> List<Message<L>> deliverImmediately(@NotNull MessageBusConnectionImpl connection, @NotNull Deque<Message<L>> jobs) {
    List<Message<L>> newJobs = null;
    // do not deliver messages as part of iteration because during delivery another messages maybe posted
    for (Iterator<Message<L>> jobIterator = jobs.iterator(); jobIterator.hasNext(); ) {
      Message<L> job = jobIterator.next();
      List<L> connectionHandlers = null;
      for (Iterator<L> handlerIterator = job.handlers.iterator(); handlerIterator.hasNext(); ) {
        L handler = handlerIterator.next();
        if (connection.isMyHandler(job.topic, handler)) {
          handlerIterator.remove();
          if (connectionHandlers == null) {
            connectionHandlers = new SmartList<>();
          }
          connectionHandlers.add(handler);
        }
      }

      if (connectionHandlers == null) {
        continue;
      }

      Message<L> filteredJob;
      if (job.handlers.isEmpty()) {
        jobIterator.remove();
        filteredJob = job;
        job.handlers.addAll(connectionHandlers);
      }
      else {
        filteredJob = new Message<>(job.topic, job.method, job.methodName, job.args, connectionHandlers);
      }
      if (newJobs == null) {
        newJobs = new SmartList<>();
      }
      newJobs.add(filteredJob);
    }
    return newJobs;
  }

  public final void setMessageDeliveryListener(@Nullable MessageDeliveryListener listener) {
    if (messageDeliveryListener != null && listener != null) {
      throw new IllegalStateException("Already set: " + messageDeliveryListener);
    }
    messageDeliveryListener = listener;
  }

  // args is not null
  private static @Nullable <L> List<Throwable> invokeListener(@NotNull MethodHandle methodHandle,
                                                              @NotNull String methodName,
                                                              Object[] args,
                                                              @NotNull Topic<L> topic,
                                                              @NotNull L handler,
                                                              @Nullable MessageDeliveryListener messageDeliveryListener,
                                                              @Nullable List<Throwable> exceptions) {
    try {
      if (handler instanceof MessageHandler) {
        ((MessageHandler)handler).handle(methodHandle, args);
      }
      else if (messageDeliveryListener == null) {
        invokeMethod(handler, args, methodHandle);
      }
      else {
        long startTime = System.nanoTime();
        invokeMethod(handler, args, methodHandle);
        messageDeliveryListener.messageDelivered(topic, methodName, handler, System.nanoTime() - startTime);
      }
    }
    catch (AbstractMethodError e) {
      // do nothing for AbstractMethodError. This listener just does not implement something newly added yet.
    }
    catch (Throwable e) {
      if (exceptions == null) {
        exceptions = new ArrayList<>();
      }
      exceptions.add(e);
    }
    return exceptions;
  }

  private static void invokeMethod(@NotNull Object handler, Object[] args, MethodHandle methodHandle) throws Throwable {
    if (args == null) {
      methodHandle.invoke(handler);
    }
    else {
      methodHandle.bindTo(handler).invokeExact(args);
    }
  }

  void disconnectPluginConnections(@NotNull Predicate<? super Class<?>> predicate) {
    for (MessageHandlerHolder holder : subscribers) {
      holder.disconnectIfNeeded(predicate);
    }
    subscriberCache.clear();
  }

  static final class RootBus extends CompositeMessageBus {
    private final AtomicReference<CompletableFuture<?>> compactionFutureRef = new AtomicReference<>();
    private final AtomicInteger emptyConnectionCounter = new AtomicInteger();

    /**
     * Pending message buses in the hierarchy.
     * The map's keys are sorted by {@link #order}
     * <p>
     * Used to avoid traversing the whole hierarchy when there are no messages to be sent in most of it.
     */
    @SuppressWarnings("SSBasedInspection")
    final ThreadLocal<Set<MessageBusImpl>> myWaitingBuses = ThreadLocal.withInitial(() -> new TreeSet<>((bus1, bus2) -> ArrayUtil.lexicographicCompare(bus1.order, bus2.order)));

    RootBus(@NotNull MessageBusOwner owner) {
      super(owner);
    }

    void scheduleEmptyConnectionRemoving() {
      int counter = emptyConnectionCounter.incrementAndGet();
      if (counter < 128 || !emptyConnectionCounter.compareAndSet(counter, 0)) {
        return;
      }

      CompletableFuture<?> oldFuture = compactionFutureRef.get();
      if (oldFuture == null) {
        CompletableFuture<?> future = CompletableFuture.runAsync(() -> {
          removeEmptyConnectionsRecursively();
          compactionFutureRef.set(null);
        }, AppExecutorUtil.getAppExecutorService());
        if (!compactionFutureRef.compareAndSet(null, future)) {
          future.cancel(false);
        }
      }
    }

    @Override
    public void dispose() {
      CompletableFuture<?> compactionFuture = compactionFutureRef.getAndSet(null);
      if (compactionFuture != null) {
        compactionFuture.cancel(false);
      }
      super.dispose();
    }
  }

  private void removeDisposedHandlers(@NotNull Topic<?> topic, @NotNull Object handler) {
    JobQueue jobQueue = messageQueue.get();
    if (!jobQueue.queue.isEmpty() &&
        jobQueue.queue.removeIf(job -> job.topic == topic && job.handlers.removeIf(it -> it == handler) && job.handlers.isEmpty())) {
      jobRemoved(jobQueue);
    }
  }
}

final class JobQueue {
  final Deque<Message<?>> queue = new ArrayDeque<>();
  @Nullable Message<?> current;
}