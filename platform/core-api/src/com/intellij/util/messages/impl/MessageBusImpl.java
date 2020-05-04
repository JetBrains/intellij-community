// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.messages.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@ApiStatus.Internal
public class MessageBusImpl implements MessageBus {
  interface MessageHandlerHolder {
    void collectHandlers(@NotNull Topic<?> topic, @NotNull List<Object> result);
  }

  private static final Logger LOG = Logger.getInstance(MessageBusImpl.class);
  private final ThreadLocal<JobQueue> myMessageQueue = ThreadLocal.withInitial(JobQueue::new);

  /**
   * Root's order is empty
   * Child bus's order is its parent order plus one more element, an int that's bigger than that of all sibling buses that come before
   * Sorting by these vectors lexicographically gives DFS order
   */
  private final int[] myOrder;

  private final ConcurrentMap<Topic<?>, Object> publisherCache = new ConcurrentHashMap<>();

  private final Collection<MessageHandlerHolder> mySubscribers = new ConcurrentLinkedQueue<>();

  /**
   * Caches subscribers for this bus and its children or parent, depending on the topic's broadcast policy
   */
  private final Map<Topic<?>, List<Object>> mySubscriberCache = new ConcurrentHashMap<>();
  private final List<MessageBusImpl> myChildBuses = ContainerUtil.createLockFreeCopyOnWriteList();

  private volatile @NotNull Map<String, List<ListenerDescriptor>> topicClassToListenerDescriptor = Collections.emptyMap();

  private static final Object NA = new Object();
  private final MessageBusImpl myParentBus;

  private final RootBus myRootBus;

  private final MessageBusOwner myOwner;
  private boolean myDisposed;
  private Disposable myConnectionDisposable;
  private MessageDeliveryListener myMessageDeliveryListener;

  private boolean myIgnoreParentLazyListeners;

  public MessageBusImpl(@NotNull MessageBusOwner owner, @NotNull MessageBusImpl parentBus) {
    myOwner = owner;
    myConnectionDisposable = createConnectionDisposable(owner);
    myParentBus = parentBus;
    myRootBus = parentBus.myRootBus;
    myOrder = parentBus.addChild(this);
    myRootBus.clearSubscriberCache();
  }

  private static @NotNull Disposable createConnectionDisposable(@NotNull MessageBusOwner owner) {
    // separate disposable must be used, because container will dispose bus connections in a separate step
    return Disposer.newDisposable(owner.toString());
  }

  // root message bus constructor
  private MessageBusImpl(@NotNull MessageBusOwner owner) {
    myOwner = owner;
    myConnectionDisposable = createConnectionDisposable(owner);
    myOrder = ArrayUtil.EMPTY_INT_ARRAY;
    myRootBus = (RootBus)this;
    myParentBus = null;
  }

  public final void setIgnoreParentLazyListeners(boolean ignoreParentLazyListeners) {
    myIgnoreParentLazyListeners = ignoreParentLazyListeners;
  }

  /**
   * Must be a concurrent map, because remove operation may be concurrently performed (synchronized only per topic).
   */
  public final void setLazyListeners(@NotNull ConcurrentMap<String, List<ListenerDescriptor>> map) {
    if (topicClassToListenerDescriptor == Collections.<String, List<ListenerDescriptor>>emptyMap()) {
      topicClassToListenerDescriptor = map;
    }
    else {
      topicClassToListenerDescriptor.putAll(map);
      clearSubscriberCache();
    }
  }

  @Override
  public final MessageBus getParent() {
    return myParentBus;
  }

  @Override
  public final String toString() {
    return super.toString() + "; owner=" + myOwner + (isDisposed() ? "; disposed" : "");
  }

  /**
   * calculates {@link #myOrder} for the given child bus
   */
  private synchronized int @NotNull [] addChild(@NotNull MessageBusImpl bus) {
    List<MessageBusImpl> children = myChildBuses;
    int lastChildIndex = children.isEmpty() ? 0 : ArrayUtil.getLastElement(children.get(children.size() - 1).myOrder, 0);
    if (lastChildIndex == Integer.MAX_VALUE) {
      LOG.error("Too many child buses");
    }
    children.add(bus);
    return ArrayUtil.append(myOrder, lastChildIndex + 1);
  }

  private void onChildBusDisposed(@NotNull MessageBusImpl childBus) {
    boolean removed = myChildBuses.remove(childBus);
    myRootBus.myWaitingBuses.get().remove(childBus);
    myRootBus.clearSubscriberCache();
    LOG.assertTrue(removed);
  }

  @Override
  public final  @NotNull MessageBusConnectionImpl connect() {
    return connect(myConnectionDisposable);
  }

  @Override
  public final @NotNull MessageBusConnectionImpl connect(@NotNull Disposable parentDisposable) {
    checkNotDisposed();
    MessageBusConnectionImpl connection = new MessageBusConnectionImpl(this);
    mySubscribers.add(connection);
    Disposer.register(parentDisposable, connection);
    return connection;
  }

  @Override
  public final @NotNull <L> L syncPublisher(@NotNull Topic<L> topic) {
    checkNotDisposed();
    //noinspection unchecked
    return (L)publisherCache.computeIfAbsent(topic, this::createPublisher);
  }

  /**
   * Clear publisher cache, including child buses.
   */
  public final void clearPublisherCache() {
    // keep it simple - we can infer plugin id from topic.getListenerClass(), but granular clearing not worth the code complication
    publisherCache.clear();
    for (MessageBusImpl childBus : myChildBuses) {
      childBus.clearPublisherCache();
    }
  }

  private <L> void subscribeLazyListeners(@NotNull Topic<L> topic) {
    List<ListenerDescriptor> listenerDescriptors = topicClassToListenerDescriptor.remove(topic.getListenerClass().getName());
    if (listenerDescriptors == null) {
      return;
    }

    // use linked hash map for repeatable results
    LinkedHashMap<PluginId, List<Object>> listenerMap = new LinkedHashMap<>();
    for (ListenerDescriptor listenerDescriptor : listenerDescriptors) {
      try {
        listenerMap.computeIfAbsent(listenerDescriptor.pluginDescriptor.getPluginId(), __ -> new ArrayList<>()).add(myOwner.createListener(listenerDescriptor));
      }
      catch (ExtensionNotApplicableException ignore) {
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error("Cannot create listener", e);
      }
    }

    listenerMap.forEach((key, listeners) -> {
      mySubscribers.add(new DescriptorBasedMessageBusConnection(key, topic, listeners));
    });
  }

  public final void unsubscribeLazyListeners(@NotNull PluginId pluginId, @NotNull List<ListenerDescriptor> listenerDescriptors) {
    if (listenerDescriptors.isEmpty() || mySubscribers.isEmpty()) {
      return;
    }

    Map<String, Set<String>> topicToDescriptors = new HashMap<>();
    for (ListenerDescriptor descriptor : listenerDescriptors) {
      topicToDescriptors.computeIfAbsent(descriptor.topicClassName, __ -> new HashSet<>()).add(descriptor.listenerClassName);
    }

    boolean isChanged = false;
    List<DescriptorBasedMessageBusConnection> newSubscribers = null;
    for (Iterator<MessageHandlerHolder> connectionIterator = mySubscribers.iterator(); connectionIterator.hasNext(); ) {
      MessageHandlerHolder holder = connectionIterator.next();
      if (!(holder instanceof DescriptorBasedMessageBusConnection)) {
        continue;
      }

      DescriptorBasedMessageBusConnection connection = (DescriptorBasedMessageBusConnection)holder;
      if (connection.pluginId != pluginId) {
        continue;
      }

      Set<String> listenerClassNames = topicToDescriptors.get(connection.topic.getListenerClass().getName());
      if (listenerClassNames == null) {
        continue;
      }

      List<Object> newHandlers = DescriptorBasedMessageBusConnection.computeNewHandlers(connection.handlers, listenerClassNames);
      if (newHandlers == null) {
        continue;
      }

      isChanged = true;
      connectionIterator.remove();
      if (!newHandlers.isEmpty()) {
        if (newSubscribers == null) {
          newSubscribers = new ArrayList<>();
        }
        newSubscribers.add(new DescriptorBasedMessageBusConnection(pluginId, connection.topic, newHandlers));
      }
    }

    if (newSubscribers != null) {
      mySubscribers.addAll(newSubscribers);
    }
    if (isChanged) {
      clearSubscriberCache();
    }
  }

  private @NotNull Object createPublisher(@NotNull Topic<?> topic) {
    Class<?> listenerClass = topic.getListenerClass();
    return Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, (proxy, method, args) -> {
      if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
        return EventDispatcher.handleObjectMethod(proxy, args, method.getName());
      }

      checkNotDisposed();

      Set<MessageBusImpl> busQueue = myRootBus.myWaitingBuses.get();
      pumpMessages(busQueue);

      List<Object> handlers = getTopicSubscribers(topic);
      if (handlers.isEmpty()) {
        return NA;
      }

      JobQueue jobQueue = myMessageQueue.get();
      jobQueue.queue.offerLast(new Message(topic, method, args, handlers));

      busQueue.add(this);
      // we must deliver messages now even if currently processing message queue, because if published as part of handler invocation,
      // handler code expects that message will be delivered immediately after publishing
      pumpMessages(busQueue);
      return NA;
    });
  }

  public final void disposeConnectionChildren() {
    Disposer.disposeChildren(myConnectionDisposable);
  }

  public final void disposeConnection() {
    Disposer.dispose(myConnectionDisposable);
    myConnectionDisposable = null;
  }

  @Override
  public final void dispose() {
    if (myDisposed) {
      LOG.error("Already disposed: " + this);
    }

    myDisposed = true;

    for (MessageBusImpl childBus : myChildBuses) {
      Disposer.dispose(childBus);
    }

    if (myConnectionDisposable != null) {
      Disposer.dispose(myConnectionDisposable);
    }

    JobQueue jobs = myMessageQueue.get();
    myMessageQueue.remove();
    if (!jobs.queue.isEmpty()) {
      LOG.error("Not delivered events in the queue: " + jobs);
    }

    if (myParentBus != null) {
      myParentBus.onChildBusDisposed(this);
    }
    else {
      myRootBus.myWaitingBuses.remove();
    }
  }

  @Override
  public final boolean isDisposed() {
    return myDisposed || myOwner.isDisposed();
  }

  @Override
  public final boolean hasUndeliveredEvents(@NotNull Topic<?> topic) {
    if (isDisposed()) {
      return false;
    }

    Set<MessageBusImpl> waitingBuses = myRootBus.myWaitingBuses.get();
    if (waitingBuses == null || waitingBuses.isEmpty()) {
      return false;
    }

    for (MessageBusImpl bus : waitingBuses) {
      JobQueue jobQueue = bus.myMessageQueue.get();
      Message current = jobQueue.current;
      if (current != null && current.topic == topic) {
        return true;
      }

      for (Message message : jobQueue.queue) {
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

  private void calcSubscribers(@NotNull Topic<?> topic, @NotNull List<Object> result, boolean subscribeLazyListeners) {
    if (subscribeLazyListeners) {
      subscribeLazyListeners(topic);
    }

    for (MessageHandlerHolder subscriber : mySubscribers) {
      subscriber.collectHandlers(topic, result);
    }

    Topic.BroadcastDirection direction = topic.getBroadcastDirection();

    if (direction == Topic.BroadcastDirection.TO_CHILDREN) {
      for (MessageBusImpl childBus : myChildBuses) {
        if (!childBus.isDisposed()) {
          childBus.calcSubscribers(topic, result, !childBus.myIgnoreParentLazyListeners);
        }
      }
    }

    if (direction == Topic.BroadcastDirection.TO_PARENT && myParentBus != null) {
      myParentBus.calcSubscribers(topic, result, true);
    }
  }

  private @NotNull List<Object> getTopicSubscribers(@NotNull Topic<?> topic) {
    return mySubscriberCache.computeIfAbsent(topic, topic1 -> {
      // light project
      if (myOwner.isDisposed()) {
        return Collections.emptyList();
      }

      List<Object> result = new ArrayList<>();
      calcSubscribers(topic1, result, true);
      myRootBus.isSubscriberCacheCleared = false;
      return result.isEmpty() ? Collections.emptyList() : result;
    });
  }

  private void jobRemoved(@NotNull JobQueue jobQueue) {
    if (jobQueue.current == null && jobQueue.queue.isEmpty()) {
      myRootBus.myWaitingBuses.get().remove(this);
    }
  }

  private static void pumpMessages(@NotNull Set<MessageBusImpl> waitingBuses) {
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

  private static void pumpWaitingBuses(@NotNull List<MessageBusImpl> buses) {
    List<Throwable> exceptions = null;
    for (MessageBusImpl bus : buses) {
      if (bus.isDisposed()) {
        continue;
      }

      JobQueue jobQueue = bus.myMessageQueue.get();
      Message job = jobQueue.current;
      if (job != null) {
        exceptions = bus.deliverMessage(job, jobQueue, bus.myMessageDeliveryListener, exceptions);
      }

      while ((job = jobQueue.queue.pollFirst()) != null) {
        exceptions = bus.deliverMessage(job, jobQueue, bus.myMessageDeliveryListener, exceptions);
      }
    }

    CompoundRuntimeException.throwIfNotEmpty(exceptions);
  }

  private @Nullable List<Throwable> deliverMessage(@NotNull Message job,
                                                   @NotNull JobQueue jobQueue,
                                                   @Nullable MessageDeliveryListener messageDeliveryListener,
                                                   @Nullable List<Throwable> exceptions) {
    jobQueue.current = job;
    List<Object> handlers = job.handlers;
    for (int index = job.currentHandlerIndex, size = handlers.size(), lastIndex = size - 1; index < size;) {
      if (index == lastIndex) {
        jobQueue.current = null;
        jobRemoved(jobQueue);
      }

      job.currentHandlerIndex++;
      try {
        invokeListener(job, handlers.get(index), messageDeliveryListener);
      }
      catch (Throwable e) {
        //noinspection InstanceofCatchParameter
        Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
        // Do nothing for AbstractMethodError. This listener just does not implement something newly added yet.
        // AbstractMethodError is normally wrapped in InvocationTargetException,
        // but some Java versions didn't do it in some cases (see http://bugs.java.com/bugdatabase/view_bug.do?bug_id=6531596)
        if (cause instanceof AbstractMethodError) continue;
        if (exceptions == null) exceptions = new SmartList<>();
        exceptions.add(cause);
      }

      if (++index != job.currentHandlerIndex) {
        // handler published some event and message queue including current job was processed as result, so, stop processing
        return exceptions;
      }
    }
    return exceptions;
  }

  final void notifyOnSubscription(@NotNull Topic<?> topic) {
    if (topic.getBroadcastDirection() == Topic.BroadcastDirection.NONE) {
      mySubscriberCache.clear();
    }
    else {
      myRootBus.clearSubscriberCache();
    }
  }

  @TestOnly
  public final void clearAllSubscriberCache() {
    myRootBus.clearSubscriberCache();
  }

  void clearSubscriberCache() {
    mySubscriberCache.clear();
    for (MessageBusImpl bus : myChildBuses) {
      bus.clearSubscriberCache();
    }
  }

  final void notifyConnectionTerminated(@NotNull MessageBusConnectionImpl connection) {
    mySubscribers.remove(connection);
    if (isDisposed()) {
      return;
    }

    if (connection.isEmpty()) {
      return;
    }

    if (connection.isBroadCastDisabled()) {
      mySubscriberCache.clear();
    }
    else {
      myRootBus.clearSubscriberCache();
    }

    JobQueue jobQueue = myMessageQueue.get();
    if (jobQueue.queue.isEmpty()) {
      return;
    }

    for (Iterator<Message> iterator = jobQueue.queue.iterator(); iterator.hasNext(); ) {
      Message job = iterator.next();
      connection.removeMyHandlers(job);
      if (job.handlers.isEmpty()) {
        iterator.remove();
      }
    }
    jobRemoved(jobQueue);
  }

  final void deliverImmediately(@NotNull MessageBusConnectionImpl connection) {
    if (myDisposed) {
      LOG.error("Already disposed: " + this);
    }
    // light project is not disposed in tests properly, so, connection is not removed
    if (myOwner.isDisposed()) {
      return;
    }

    JobQueue jobQueue = myMessageQueue.get();
    Deque<Message> jobs = jobQueue.queue;
    if (jobs.isEmpty()) {
      return;
    }

    List<Message> newJobs = null;
    // do not deliver messages as part of iteration because during delivery another messages maybe posted
    for (Iterator<Message> jobIterator = jobs.iterator(); jobIterator.hasNext(); ) {
      Message job = jobIterator.next();
      List<Object> connectionHandlers = null;
      for (Iterator<Object> handlerIterator = job.handlers.iterator(); handlerIterator.hasNext(); ) {
        Object handler = handlerIterator.next();
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

      Message filteredJob;
      if (job.handlers.isEmpty()) {
        jobIterator.remove();
        filteredJob = job;
        job.handlers.addAll(connectionHandlers);
      }
      else {
        filteredJob = new Message(job.topic, job.listenerMethod, job.args, connectionHandlers);
      }

      if (newJobs == null) {
        newJobs = new SmartList<>();
      }
      newJobs.add(filteredJob);
    }

    if (newJobs == null) {
      return;
    }

    // add to queue to ensure that hasUndeliveredEvents works correctly
    for (int i = newJobs.size() - 1; i >= 0; i--) {
      jobs.addFirst(newJobs.get(i));
    }

    List<Throwable> exceptions = null;
    for (Message job : newJobs) {
      // remove here will be not linear as job should be head (first element) in normal conditions
      jobs.removeFirstOccurrence(job);
      exceptions = deliverMessage(job, jobQueue, myMessageDeliveryListener, exceptions);
    }

    CompoundRuntimeException.throwIfNotEmpty(exceptions);
  }

  public final void setMessageDeliveryListener(@Nullable MessageDeliveryListener listener) {
    if (myMessageDeliveryListener != null && listener != null) {
      throw new IllegalStateException("Already set: " + myMessageDeliveryListener);
    }
    myMessageDeliveryListener = listener;
  }

  private static void invokeListener(@NotNull Message message,
                                     @NotNull Object handler,
                                     @Nullable MessageDeliveryListener messageDeliveryListener) throws IllegalAccessException, InvocationTargetException {
    if (handler instanceof MessageHandler) {
      ((MessageHandler)handler).handle(message.listenerMethod, message.args);
      return;
    }

    Method method = message.listenerMethod;
    if (messageDeliveryListener == null) {
      method.invoke(handler, message.args);
      return;
    }

    long startTime = System.nanoTime();
    method.invoke(handler, message.args);
    messageDeliveryListener.messageDelivered(message.topic, method.getName(), handler, System.nanoTime() - startTime);
  }

  static final class RootBus extends MessageBusImpl {
    /**
     * Pending message buses in the hierarchy.
     * The map's keys are sorted by {@link #myOrder}
     * <p>
     * Used to avoid traversing the whole hierarchy when there are no messages to be sent in most of it.
     */
    private final ThreadLocal<SortedSet<MessageBusImpl>> myWaitingBuses = ThreadLocal.withInitial(() -> {
      return new TreeSet<>((bus1, bus2) -> ArrayUtil.lexicographicCompare(bus1.myOrder, bus2.myOrder));
    });

    // to avoid traversing child buses if already cleared, as
    // clearSubscriberCache uses O(numberOfModules) time to clear caches
    private volatile boolean isSubscriberCacheCleared = true;

    @Override
    void clearSubscriberCache() {
      if (isSubscriberCacheCleared) {
        return;
      }

      super.clearSubscriberCache();
      isSubscriberCacheCleared = true;
    }

    RootBus(@NotNull MessageBusOwner owner) {
      super(owner);
    }
  }
}

final class JobQueue {
  final Deque<Message> queue = new ArrayDeque<>();
  @Nullable Message current;
}

final class DescriptorBasedMessageBusConnection implements MessageBusImpl.MessageHandlerHolder {
  final PluginId pluginId;
  final Topic<?> topic;
  final List<Object> handlers;

  DescriptorBasedMessageBusConnection(@NotNull PluginId pluginId, @NotNull Topic<?> topic, @NotNull List<Object> handlers) {
    this.pluginId = pluginId;
    this.topic = topic;
    this.handlers = handlers;
  }

  @Override
  public void collectHandlers(@NotNull Topic<?> topic, @NotNull List<Object> result) {
    if (this.topic == topic) {
      result.addAll(handlers);
    }
  }

  @Override
  public String toString() {
    return "DescriptorBasedMessageBusConnection(" +
           "handlers=" + handlers +
           ')';
  }

  static @Nullable List<Object> computeNewHandlers(@NotNull List<Object> handlers, @NotNull Set<String> excludeClassNames) {
    List<Object> newHandlers = null;
    for (int i = 0, size = handlers.size(); i < size; i++) {
      Object handler = handlers.get(i);
      if (excludeClassNames.contains(handler.getClass().getName())) {
        if (newHandlers == null) {
          newHandlers = i == 0 ? new ArrayList<>() : new ArrayList<>(handlers.subList(0, i));
        }
      }
      else if (newHandlers != null) {
        newHandlers.add(handler);
      }
    }
    return newHandlers;
  }
}