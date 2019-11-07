// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.messages.LazyListenerCreator;
import com.intellij.util.messages.ListenerDescriptor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author max
 */
public class MessageBusImpl implements MessageBus {
  private static final Logger LOG = Logger.getInstance(MessageBusImpl.class);
  private static final Comparator<MessageBusImpl> MESSAGE_BUS_COMPARATOR = (bus1, bus2) -> ArrayUtil.lexicographicCompare(bus1.myOrder, bus2.myOrder);
  @SuppressWarnings("SSBasedInspection") private final ThreadLocal<Queue<DeliveryJob>> myMessageQueue = createThreadLocalQueue();

  /**
   * Root's order is empty
   * Child bus's order is its parent order plus one more element, an int that's bigger than that of all sibling buses that come before
   * Sorting by these vectors lexicographically gives DFS order
   */
  private final int[] myOrder;

  private final ConcurrentMap<Topic<?>, Object> myPublishers = ContainerUtil.newConcurrentMap();

  /**
   * This bus's subscribers
   */
  private final ConcurrentMap<Topic<?>, List<MessageBusConnectionImpl>> mySubscribers = ContainerUtil.newConcurrentMap();

  /**
   * Caches subscribers for this bus and its children or parent, depending on the topic's broadcast policy
   */
  private final Map<Topic<?>, List<MessageBusConnectionImpl>> mySubscriberCache = ContainerUtil.newConcurrentMap();
  private final List<MessageBusImpl> myChildBuses = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull
  private volatile Map<String, List<ListenerDescriptor>> myTopicClassToListenerClass = Collections.emptyMap();

  private static final Object NA = new Object();
  private MessageBusImpl myParentBus;

  private final RootBus myRootBus;

  //is used for debugging purposes
  private final Object myOwner;
  private boolean myDisposed;
  private final Disposable myConnectionDisposable;
  private MessageDeliveryListener myMessageDeliveryListener;

  private final MessageBusConnectionImpl myLazyConnection;

  public MessageBusImpl(@NotNull Object owner, @NotNull MessageBusImpl parentBus) {
    myOwner = owner;
    myConnectionDisposable = Disposer.newDisposable(myOwner.toString());
    myParentBus = parentBus;
    myRootBus = parentBus.myRootBus;
    synchronized (parentBus.myChildBuses) {
      myOrder = parentBus.nextOrder();
      parentBus.myChildBuses.add(this);
    }
    LOG.assertTrue(parentBus.myChildBuses.contains(this));
    myRootBus.clearSubscriberCache();
    // only for project
    myLazyConnection = parentBus.myParentBus == null ? connect() : null;
  }

  // root message bus constructor
  private MessageBusImpl(@NotNull Object owner) {
    myOwner = owner;
    myConnectionDisposable = Disposer.newDisposable(myOwner.toString());
    myOrder = ArrayUtil.EMPTY_INT_ARRAY;
    myRootBus = (RootBus)this;
    myLazyConnection = connect();
  }

  /**
   * Must be a concurrent map, because remove operation may be concurrently performed (synchronized only per topic).
   */
  @ApiStatus.Internal
  public void setLazyListeners(@NotNull ConcurrentMap<String, List<ListenerDescriptor>> map) {
    if (myTopicClassToListenerClass != Collections.<String, List<ListenerDescriptor>>emptyMap()) {
      throw new IllegalStateException("Already set: "+myTopicClassToListenerClass);
    }
    myTopicClassToListenerClass = map;
  }

  @Override
  public MessageBus getParent() {
    return myParentBus;
  }

  @Override
  public String toString() {
    return super.toString() + "; owner=" + myOwner + (myDisposed ? "; disposed" : "");
  }

  /**
   * calculates {@link #myOrder} for the given child bus
   */
  @NotNull
  private int[] nextOrder() {
    MessageBusImpl lastChild = ContainerUtil.getLastItem(myChildBuses);

    int lastChildIndex = lastChild == null ? 0 : ArrayUtil.getLastElement(lastChild.myOrder, 0);
    if (lastChildIndex == Integer.MAX_VALUE) {
      LOG.error("Too many child buses");
    }

    return ArrayUtil.append(myOrder, lastChildIndex + 1);
  }

  private void onChildBusDisposed(@NotNull MessageBusImpl childBus) {
    boolean removed = myChildBuses.remove(childBus);
    Map<MessageBusImpl, Integer> map = myRootBus.myWaitingBuses.get();
    if (map != null) map.remove(childBus);
    myRootBus.clearSubscriberCache();
    LOG.assertTrue(removed);
  }

  private static final class DeliveryJob {
    DeliveryJob(@NotNull MessageBusConnectionImpl connection, @NotNull Message message) {
      this.connection = connection;
      this.message = message;
    }

    public final MessageBusConnectionImpl connection;
    public final Message message;

    @NonNls
    @Override
    public String toString() {
      return "{ DJob connection:" + connection + "; message: " + message + " }";
    }
  }

  @Override
  @NotNull
  public MessageBusConnectionImpl connect() {
    return connect(myConnectionDisposable);
  }

  @Override
  @NotNull
  public MessageBusConnectionImpl connect(@NotNull Disposable parentDisposable) {
    checkNotDisposed();
    MessageBusConnectionImpl connection = new MessageBusConnectionImpl(this);
    Disposer.register(parentDisposable, connection);
    return connection;
  }

  @Override
  @NotNull
  public <L> L syncPublisher(@NotNull Topic<L> topic) {
    checkNotDisposed();
    @SuppressWarnings("unchecked")
    L publisher = (L)myPublishers.get(topic);
    if (publisher != null) {
      return publisher;
    }

    Class<L> listenerClass = topic.getListenerClass();

    if (myTopicClassToListenerClass.isEmpty()) {
      Object newInstance = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, createTopicHandler(topic));
      Object prev = myPublishers.putIfAbsent(topic, newInstance);
      //noinspection unchecked
      return (L)(prev == null ? newInstance : prev);
    }
    else {
      // remove is atomic operation, so, even if topic concurrently created and our topic instance will be not used, still, listeners will be added,
      // but problem is that if another topic will be returned earlier, then these listeners will not get fired event
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (topic) {
        return subscribeLazyListeners(topic, listenerClass);
      }
    }
  }

  @NotNull
  private <L> L subscribeLazyListeners(@NotNull Topic<L> topic, @NotNull Class<L> listenerClass) {
    //noinspection unchecked
    L publisher = (L)myPublishers.get(topic);
    if (publisher != null) {
      return publisher;
    }

    List<ListenerDescriptor> listenerDescriptors = myTopicClassToListenerClass.remove(listenerClass.getName());
    if (listenerDescriptors != null) {
      LazyListenerCreator listenerCreator = (LazyListenerCreator)myOwner;
      List<Object> listeners = new ArrayList<>(listenerDescriptors.size());
      for (ListenerDescriptor listenerDescriptor : listenerDescriptors) {
        try {
          listeners.add(listenerCreator.createListener(listenerDescriptor));
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

      if (!listeners.isEmpty()) {
        myLazyConnection.subscribe(topic, listeners);
      }
    }

    //noinspection unchecked
    publisher = (L)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, createTopicHandler(topic));
    myPublishers.put(topic, publisher);
    return publisher;
  }

  @NotNull
  private <L> InvocationHandler createTopicHandler(@NotNull Topic<L> topic) {
    return (proxy, method, args) -> {
      if (method.getDeclaringClass().getName().equals("java.lang.Object")) {
        return EventDispatcher.handleObjectMethod(proxy, args, method.getName());
      }
      sendMessage(new Message(topic, method, args));
      return NA;
    };
  }

  @Override
  public void dispose() {
    checkNotDisposed();
    myDisposed = true;

    for (MessageBusImpl childBus : myChildBuses) {
      Disposer.dispose(childBus);
    }

    Disposer.dispose(myConnectionDisposable);
    Queue<DeliveryJob> jobs = myMessageQueue.get();
    if (!jobs.isEmpty()) {
      LOG.error("Not delivered events in the queue: " + jobs);
    }
    myMessageQueue.remove();
    if (myParentBus != null) {
      myParentBus.onChildBusDisposed(this);
      myParentBus = null;
    }
    else {
      myRootBus.myWaitingBuses.remove();
    }
  }

  @Override
  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public boolean hasUndeliveredEvents(@NotNull Topic<?> topic) {
    if (myDisposed) return false;
    if (!isDispatchingAnything()) return false;

    for (MessageBusConnectionImpl connection : getTopicSubscribers(topic)) {
      if (connection.containsMessage(topic)) {
        return true;
      }
    }
    return false;
  }

  private boolean isDispatchingAnything() {
    Map<MessageBusImpl, Integer> waitingBuses = myRootBus.myWaitingBuses.get();
    return waitingBuses != null && !waitingBuses.isEmpty();
  }

  private void checkNotDisposed() {
    if (myDisposed) {
      LOG.error("Already disposed: " + this);
    }
  }

  @NotNull
  @TestOnly
  String getOwner() {
    return myOwner.toString();
  }

  private void calcSubscribers(@NotNull Topic<?> topic, @NotNull List<? super MessageBusConnectionImpl> result) {
    final List<MessageBusConnectionImpl> topicSubscribers = mySubscribers.get(topic);
    if (topicSubscribers != null) {
      result.addAll(topicSubscribers);
    }

    Topic.BroadcastDirection direction = topic.getBroadcastDirection();

    if (direction == Topic.BroadcastDirection.TO_CHILDREN) {
      for (MessageBusImpl childBus : myChildBuses) {
        childBus.calcSubscribers(topic, result);
      }
    }

    if (direction == Topic.BroadcastDirection.TO_PARENT && myParentBus != null) {
      myParentBus.calcSubscribers(topic, result);
    }
  }

  private void postMessage(@NotNull Message message) {
    checkNotDisposed();
    List<MessageBusConnectionImpl> topicSubscribers = getTopicSubscribers(message.getTopic());
    if (!topicSubscribers.isEmpty()) {
      for (MessageBusConnectionImpl subscriber : topicSubscribers) {
        subscriber.getBus().myMessageQueue.get().offer(new DeliveryJob(subscriber, message));
        subscriber.getBus().notifyPendingJobChange(1);
        subscriber.scheduleMessageDelivery(message);
      }
    }
  }

  @NotNull
  private List<MessageBusConnectionImpl> getTopicSubscribers(@NotNull Topic<?> topic) {
    List<MessageBusConnectionImpl> topicSubscribers = mySubscriberCache.get(topic);
    if (topicSubscribers == null) {
      topicSubscribers = new ArrayList<>();
      calcSubscribers(topic, topicSubscribers);
      mySubscriberCache.put(topic, topicSubscribers);
      myRootBus.myClearedSubscribersCache = false;
    }
    return topicSubscribers;
  }

  private void notifyPendingJobChange(int delta) {
    ThreadLocal<SortedMap<MessageBusImpl, Integer>> ref = myRootBus.myWaitingBuses;
    SortedMap<MessageBusImpl, Integer> map = ref.get();
    if (map == null) {
      ref.set(map = new TreeMap<>(MESSAGE_BUS_COMPARATOR));
    }
    Integer countObject = map.get(this);
    int count = countObject == null ? 0 : countObject;
    int newCount = count + delta;
    if (newCount > 0) {
      checkNotDisposed();
      map.put(this, newCount);
    }
    else if (newCount == 0) {
      map.remove(this);
    }
    else {
      LOG.error("Negative job count: " + this);
    }
  }

  private void sendMessage(@NotNull Message message) {
    pumpMessages();
    postMessage(message);
    pumpMessages();
  }

  private void pumpMessages() {
    checkNotDisposed();
    Map<MessageBusImpl, Integer> map = myRootBus.myWaitingBuses.get();
    if (map != null && !map.isEmpty()) {
      List<MessageBusImpl> liveBuses = new ArrayList<>(map.size());
      for (MessageBusImpl bus : map.keySet()) {
        if (ensureAlive(map, bus)) {

          liveBuses.add(bus);
        }
      }

      if (!liveBuses.isEmpty()) {
        pumpWaitingBuses(liveBuses);
      }
    }
  }

  private static void pumpWaitingBuses(@NotNull List<? extends MessageBusImpl> buses) {
    List<Throwable> exceptions = null;
    for (MessageBusImpl bus : buses) {
      if (bus.myDisposed) continue;

      exceptions = appendExceptions(exceptions, bus.doPumpMessages());
    }
    rethrowExceptions(exceptions);
  }

  private static List<Throwable> appendExceptions(@Nullable List<Throwable> exceptions, @NotNull List<? extends Throwable> busExceptions) {
    if (!busExceptions.isEmpty()) {
      if (exceptions == null) exceptions = new ArrayList<>(busExceptions.size());
      exceptions.addAll(busExceptions);
    }
    return exceptions;
  }

  private static void rethrowExceptions(@Nullable List<? extends Throwable> exceptions) {
    if (exceptions == null) return;

    ProcessCanceledException pce = ContainerUtil.findInstance(exceptions, ProcessCanceledException.class);
    if (pce != null) throw pce;

    CompoundRuntimeException.throwIfNotEmpty(exceptions);
  }

  private static boolean ensureAlive(@NotNull Map<MessageBusImpl, Integer> map, @NotNull MessageBusImpl bus) {
    if (bus.myDisposed) {
      map.remove(bus);
      LOG.error("Accessing disposed message bus " + bus);
      return false;
    }
    return true;
  }

  @NotNull
  private List<Throwable> doPumpMessages() {
    Queue<DeliveryJob> queue = myMessageQueue.get();
    List<Throwable> exceptions = Collections.emptyList();
    do {
      DeliveryJob job = queue.poll();
      if (job == null) break;
      notifyPendingJobChange(-1);
      try {
        job.connection.deliverMessage(job.message);
      }
      catch (Throwable e) {
        if (exceptions == Collections.<Throwable>emptyList()) {
          exceptions = new ArrayList<>();
        }
        exceptions.add(e);
      }
    }
    while (true);
    return exceptions;
  }

  void notifyOnSubscription(@NotNull MessageBusConnectionImpl connection, @NotNull Topic<?> topic) {
    checkNotDisposed();
    List<MessageBusConnectionImpl> topicSubscribers = mySubscribers.get(topic);
    if (topicSubscribers == null) {
      topicSubscribers = ContainerUtil.createLockFreeCopyOnWriteList();
      topicSubscribers = ConcurrencyUtil.cacheOrGet(mySubscribers, topic, topicSubscribers);
    }

    topicSubscribers.add(connection);

    myRootBus.clearSubscriberCache();
  }

  void clearSubscriberCache() {
    mySubscriberCache.clear();
    for (MessageBusImpl bus : myChildBuses) {
      bus.clearSubscriberCache();
    }
  }

  void notifyConnectionTerminated(@NotNull MessageBusConnectionImpl connection) {
    for (List<MessageBusConnectionImpl> topicSubscribers : mySubscribers.values()) {
      topicSubscribers.remove(connection);
    }
    if (myDisposed) return;
    myRootBus.clearSubscriberCache();

    final Iterator<DeliveryJob> i = myMessageQueue.get().iterator();
    while (i.hasNext()) {
      final DeliveryJob job = i.next();
      if (job.connection == connection) {
        i.remove();
        notifyPendingJobChange(-1);
      }
    }
  }

  void deliverSingleMessage() {
    checkNotDisposed();
    final DeliveryJob job = myMessageQueue.get().poll();
    if (job == null) return;
    notifyPendingJobChange(-1);
    job.connection.deliverMessage(job.message);
  }

  @NotNull
  static <T> ThreadLocal<Queue<T>> createThreadLocalQueue() {
    return ThreadLocal.withInitial(ArrayDeque::new);
  }

  @ApiStatus.Internal
  public void setMessageDeliveryListener(@Nullable MessageDeliveryListener listener) {
    if (myMessageDeliveryListener != null && listener != null) {
      throw new IllegalStateException("Already set: " + myMessageDeliveryListener);
    }
    myMessageDeliveryListener = listener;
  }

  void invokeListener(@NotNull Message message, Object handler) throws IllegalAccessException, InvocationTargetException {
    Method method = message.getListenerMethod();
    MessageDeliveryListener listener = myMessageDeliveryListener;
    if (listener == null) {
      method.invoke(handler, message.getArgs());
      return;
    }

    long startTime = System.nanoTime();
    method.invoke(handler, message.getArgs());
    listener.messageDelivered(message.getTopic(), method.getName(), handler, System.nanoTime() - startTime);
  }

  static final class RootBus extends MessageBusImpl {
    /**
     * Holds the counts of pending messages for all message buses in the hierarchy
     * This field is null for non-root buses
     * The map's keys are sorted by {@link #myOrder}
     * <p>
     * Used to avoid traversing the whole hierarchy when there are no messages to be sent in most of it
     */
    private final ThreadLocal<SortedMap<MessageBusImpl, Integer>> myWaitingBuses = new ThreadLocal<>();

    private volatile boolean myClearedSubscribersCache;

    @Override
    void clearSubscriberCache() {
      if (myClearedSubscribersCache) return;
      super.clearSubscriberCache();
      myClearedSubscribersCache = true;
    }

    RootBus(@NotNull Object owner) {
      super(owner);
    }
  }
}
