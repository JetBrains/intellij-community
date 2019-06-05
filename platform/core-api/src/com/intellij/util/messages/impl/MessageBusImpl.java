// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.messages.ListenerDescriptor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @author max
 */
public class MessageBusImpl implements MessageBus {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.messages.impl.MessageBusImpl");
  private static final Comparator<MessageBusImpl> MESSAGE_BUS_COMPARATOR =
    (bus1, bus2) -> ContainerUtil.compareLexicographically(bus1.myOrder, bus2.myOrder);
  @SuppressWarnings("SSBasedInspection") private final ThreadLocal<Queue<DeliveryJob>> myMessageQueue = createThreadLocalQueue();

  /**
   * Root's order is empty
   * Child bus's order is its parent order plus one more element, an int that's bigger than that of all sibling buses that come before
   * Sorting by these vectors lexicographically gives DFS order
   */
  private List<Integer> myOrder;

  private final ConcurrentMap<Topic, Object> myPublishers = ContainerUtil.newConcurrentMap();

  /**
   * This bus's subscribers
   */
  private final ConcurrentMap<Topic, List<MessageBusConnectionImpl>> mySubscribers = ContainerUtil.newConcurrentMap();

  /**
   * Caches subscribers for this bus and its children or parent, depending on the topic's broadcast policy
   */
  private final ConcurrentMap<Topic, List<MessageBusConnectionImpl>> mySubscriberCache = ContainerUtil.newConcurrentMap();
  private final List<MessageBusImpl> myChildBuses = ContainerUtil.createLockFreeCopyOnWriteList();

  private volatile ConcurrentMap<String, List<ListenerDescriptor>> myTopicClassToListenerClass;

  private static final Object NA = new Object();
  private MessageBusImpl myParentBus;

  RootBus myRootBus;

  //is used for debugging purposes
  private final String myOwner;
  private boolean myDisposed;
  private final Disposable myConnectionDisposable;
  private MessageDeliveryListener myListener;

  public MessageBusImpl(@NotNull Object owner, @NotNull MessageBus parentBus) {
    this(owner);

    myParentBus = (MessageBusImpl)parentBus;
    myRootBus = myParentBus.myRootBus;
    myParentBus.onChildBusCreated(this);
    LOG.assertTrue(myParentBus.myChildBuses.contains(this));
    LOG.assertTrue(myOrder != null);
  }

  @ApiStatus.Internal
  public void setLazyListeners(@NotNull ConcurrentMap<String, List<ListenerDescriptor>> map) {
    if (myTopicClassToListenerClass != null) {
      throw new IllegalStateException("Already set: "+myTopicClassToListenerClass);
    }
    myTopicClassToListenerClass = map;
  }

  private MessageBusImpl(@NotNull Object owner) {
    myOwner = owner + " of " + owner.getClass();
    myConnectionDisposable = Disposer.newDisposable(myOwner);
    myOrder = new ArrayList<>();
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
   * Notifies current bus that a child bus is created. Has two responsibilities:
   * <ul>
   * <li>stores given child bus in {@link #myChildBuses} collection</li>
   * <li>
   * calculates {@link #myOrder} for the given child bus
   * </li>
   * </ul>
   * <p/>
   * Thread-safe.
   *
   * @param childBus newly created child bus
   */
  private void onChildBusCreated(@NotNull MessageBusImpl childBus) {
    LOG.assertTrue(childBus.myParentBus == this);

    synchronized (myChildBuses) {
      MessageBusImpl lastChild = myChildBuses.isEmpty() ? null : myChildBuses.get(myChildBuses.size() - 1);
      myChildBuses.add(childBus);

      int lastChildIndex = lastChild == null ? 0 : lastChild.myOrder.get(lastChild.myOrder.size() - 1);
      if (lastChildIndex == Integer.MAX_VALUE) {
        LOG.error("Too many child buses");
      }
      List<Integer> childOrder = new ArrayList<>(myOrder.size() + 1);
      childOrder.addAll(myOrder);
      childOrder.add(lastChildIndex + 1);
      childBus.myOrder = childOrder;
    }

    myRootBus.clearSubscriberCache();
  }

  private void onChildBusDisposed(@NotNull MessageBusImpl childBus) {
    boolean removed;
    synchronized (myChildBuses) {
      removed = myChildBuses.remove(childBus);
    }
    Map<MessageBusImpl, Integer> map = myRootBus.myWaitingBuses.get();
    if (map != null) map.remove(childBus);
    myRootBus.clearSubscriberCache();
    LOG.assertTrue(removed);
  }

  private static class DeliveryJob {
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
  public MessageBusConnection connect() {
    return connect(myConnectionDisposable);
  }

  @Override
  @NotNull
  public MessageBusConnection connect(@NotNull Disposable parentDisposable) {
    checkNotDisposed();
    final MessageBusConnection connection = new MessageBusConnectionImpl(this);
    Disposer.register(parentDisposable, connection);
    return connection;
  }

  @NotNull
  protected MessageBusConnection createConnectionForLazyListeners() {
    return connect();
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

    if (myTopicClassToListenerClass == null) {
      Object newInstance = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, createTopicHandler(topic));
      Object prev = myPublishers.putIfAbsent(topic, newInstance);
      //noinspection unchecked
      return (L)(prev == null ? newInstance : prev);
    }

    // remove is atomic operation, so, even if topic concurrently created and our topic instance will be not used, still, listeners will be added,
    // but problem is that if another topic will be returned earlier, then these listeners will not get fired event
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (topic) {
      //noinspection unchecked
      publisher = (L)myPublishers.get(topic);
      if (publisher != null) {
        return publisher;
      }

      List<ListenerDescriptor> listenerDescriptors = myTopicClassToListenerClass.remove(listenerClass.getName());
      if (listenerDescriptors != null) {
        MessageBusConnection connection = createConnectionForLazyListeners();
        for (ListenerDescriptor listenerDescriptor : listenerDescriptors) {
          ClassLoader classLoader = listenerDescriptor.pluginDescriptor.getPluginClassLoader();
          try {
            @SuppressWarnings("unchecked")
            L listener = (L)ReflectionUtil.newInstance(Class.forName(listenerDescriptor.listenerClassName, true, classLoader), false);
            connection.subscribe(topic, listener);
          }
          catch (ClassNotFoundException e) {
            LOG.error(e);
          }
        }
      }

      //noinspection unchecked
      publisher = (L)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[]{listenerClass}, createTopicHandler(topic));
      myPublishers.put(topic, publisher);
      return publisher;
    }
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

    myRootBus = null;
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
    SortedMap<MessageBusImpl, Integer> waitingBuses = myRootBus.myWaitingBuses.get();
    return waitingBuses != null && !waitingBuses.isEmpty();
  }

  private void checkNotDisposed() {
    if (myDisposed) {
      LOG.error("Already disposed: " + this);
    }
  }

  private void calcSubscribers(@NotNull Topic topic, @NotNull List<? super MessageBusConnectionImpl> result) {
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
  private List<MessageBusConnectionImpl> getTopicSubscribers(@NotNull Topic topic) {
    List<MessageBusConnectionImpl> topicSubscribers = mySubscriberCache.get(topic);
    if (topicSubscribers == null) {
      topicSubscribers = new SmartList<>();
      calcSubscribers(topic, topicSubscribers);
      mySubscriberCache.put(topic, topicSubscribers);

      if (myRootBus.myClearedSubscribersCache) {
        myRootBus.myClearedSubscribersCache = false;
      }
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
    if (myParentBus != null) {
      LOG.assertTrue(myParentBus.myChildBuses.contains(this));
      myParentBus.pumpMessages();
    }
    else {
      final Map<MessageBusImpl, Integer> map = myRootBus.myWaitingBuses.get();
      if (map != null && !map.isEmpty()) {
        List<MessageBusImpl> liveBuses = null;
        for (MessageBusImpl bus : map.keySet()) {
          if (ensureAlive(map, bus)) {
            if (liveBuses == null) {
              liveBuses = new SmartList<>();
            }
            liveBuses.add(bus);
          }
        }

        if (liveBuses != null) {
          pumpWaitingBuses(liveBuses);
        }
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
      if (exceptions == null) exceptions = new SmartList<>();
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
    List<Throwable> exceptions = null;
    do {
      DeliveryJob job = queue.poll();
      if (job == null) break;
      notifyPendingJobChange(-1);
      try {
        job.connection.deliverMessage(job.message);
      }
      catch (Throwable e) {
        if (exceptions == null) {
          exceptions = new SmartList<>();
        }
        exceptions.add(e);
      }
    }
    while (true);
    return exceptions == null ? Collections.emptyList() : exceptions;
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
  public void setMessageDeliveryListener(@NotNull MessageDeliveryListener listener) {
    if (myListener != null) {
      throw new IllegalStateException("Already set: "+myListener);
    }
    myListener = listener;
  }

  void notifyMessageDeliveryListener(@NotNull Topic topic, @NotNull String messageName, @NotNull Object handler, long durationNanos) {
    if (myListener != null) {
      myListener.messageDelivered(topic, messageName, handler, durationNanos);
    }
  }

  public static final class RootBus extends MessageBusImpl {
    /**
     * Holds the counts of pending messages for all message buses in the hierarchy
     * This field is null for non-root buses
     * The map's keys are sorted by {@link #myOrder}
     * <p>
     * Used to avoid traversing the whole hierarchy when there are no messages to be sent in most of it
     */
    private final ThreadLocal<SortedMap<MessageBusImpl, Integer>> myWaitingBuses = new ThreadLocal<>();

    private final MessageBusConnection myLazyConnection = connect();

    volatile boolean myClearedSubscribersCache;

    @NotNull
    @Override
    protected MessageBusConnection createConnectionForLazyListeners() {
      return myLazyConnection;
    }

    @Override
    void clearSubscriberCache() {
      if (myClearedSubscribersCache) return;
      super.clearSubscriberCache();
      myClearedSubscribersCache = true;
    }

    public RootBus(@NotNull Object owner) {
      super(owner);
      myRootBus = this;
    }
  }
}
