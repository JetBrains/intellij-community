// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.ListenerDescriptor;
import com.intellij.util.messages.MessageBusOwner;
import com.intellij.util.messages.Topic;
import com.intellij.util.messages.Topic.BroadcastDirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

class CompositeMessageBus extends MessageBusImpl implements MessageBusEx {
  private final List<MessageBusImpl> childBuses = ContainerUtil.createLockFreeCopyOnWriteList();
  private volatile @NotNull Map<String, List<ListenerDescriptor>> topicClassToListenerDescriptor = Collections.emptyMap();

  CompositeMessageBus(@NotNull MessageBusOwner owner, @NotNull CompositeMessageBus parentBus) {
    super(owner, parentBus);
  }

  // root message bus constructor
  protected CompositeMessageBus(@NotNull MessageBusOwner owner) {
    super(owner);
  }

  /**
   * Must be a concurrent map, because remove operation may be concurrently performed (synchronized only per topic).
   */
  @Override
  public final void setLazyListeners(@NotNull ConcurrentMap<String, List<ListenerDescriptor>> map) {
    if (topicClassToListenerDescriptor == Collections.<String, List<ListenerDescriptor>>emptyMap()) {
      topicClassToListenerDescriptor = map;
    }
    else {
      topicClassToListenerDescriptor.putAll(map);
      // adding project level listener for app level topic is not recommended, but supported
      if (myRootBus != this) {
        myRootBus.subscriberCache.clear();
      }
      subscriberCache.clear();
    }
  }

  @Override
  protected final boolean hasChildren() {
    return !childBuses.isEmpty();
  }

  /**
   * calculates {@link #myOrder} for the given child bus
   */
  final synchronized int @NotNull [] addChild(@NotNull MessageBusImpl bus) {
    List<MessageBusImpl> children = childBuses;
    int lastChildIndex = children.isEmpty() ? 0 : ArrayUtil.getLastElement(children.get(children.size() - 1).myOrder, 0);
    if (lastChildIndex == Integer.MAX_VALUE) {
      LOG.error("Too many child buses");
    }
    children.add(bus);
    return ArrayUtil.append(myOrder, lastChildIndex + 1);
  }

  final void onChildBusDisposed(@NotNull MessageBusImpl childBus) {
    boolean removed = childBuses.remove(childBus);
    myRootBus.myWaitingBuses.get().remove(childBus);

    MessageBusImpl parentBus = this;
    do {
      parentBus.subscriberCache.clear();
    }
    while ((parentBus = parentBus.myParentBus) != null);
    LOG.assertTrue(removed);
  }

  @Override
  protected final @NotNull MessageBusImpl.MessagePublisher createPublisher(@NotNull Topic<?> topic, BroadcastDirection direction) {
    if (direction == BroadcastDirection.TO_PARENT) {
      return new ToParentMessagePublisher(topic, this);
    }
    else if (direction == BroadcastDirection.TO_DIRECT_CHILDREN) {
      if (myParentBus != null) {
        throw new IllegalArgumentException("Broadcast direction TO_DIRECT_CHILDREN is allowed only for app level message bus. " +
                                           "Please publish to app level message bus or change topic broadcast direction to NONE or TO_PARENT");
      }
      return new ToDirectChildrenMessagePublisher(topic, this);
    }
    else {
      return new MessagePublisher(topic, this);
    }
  }

  private static final class ToDirectChildrenMessagePublisher extends MessagePublisher implements InvocationHandler {
    ToDirectChildrenMessagePublisher(@NotNull Topic<?> topic, @NotNull CompositeMessageBus bus) {
      super(topic, bus);
    }

    @Override
    protected final boolean publish(@NotNull Method method, Object[] args, @Nullable JobQueue jobQueue) {
      List<Throwable> exceptions = null;
      boolean hasHandlers = false;

      List<Object> handlers = bus.subscriberCache.computeIfAbsent(topic, bus::computeSubscribers);
      if (!handlers.isEmpty()) {
        exceptions = executeOrAddToQueue(topic, method, args, handlers, jobQueue, bus.messageDeliveryListener, null);
        hasHandlers = true;
      }

      for (MessageBusImpl childBus : ((CompositeMessageBus)bus).childBuses) {
        // light project in tests is not disposed correctly
        if (childBus.owner.isDisposed()) {
          continue;
        }

        handlers = childBus.subscriberCache.computeIfAbsent(topic, topic1 -> {
          List<Object> result = new ArrayList<>();
          childBus.doComputeSubscribers(topic1, result, /* subscribeLazyListeners = */ !childBus.owner.isParentLazyListenersIgnored());
          return result.isEmpty() ? Collections.emptyList() : result;
        });
        if (handlers.isEmpty()) {
          continue;
        }

        hasHandlers = true;
        exceptions = executeOrAddToQueue(topic, method, args, handlers, jobQueue, bus.messageDeliveryListener, exceptions);
      }

      if (exceptions != null) {
        EventDispatcher.throwExceptions(exceptions);
      }
      return hasHandlers;
    }
  }

  @Override
  protected final @NotNull List<Object> computeSubscribers(@NotNull Topic<?> topic) {
    // light project
    if (owner.isDisposed()) {
      return Collections.emptyList();
    }
    return super.computeSubscribers(topic);
  }

  @Override
  protected final void doComputeSubscribers(@NotNull Topic<?> topic, @NotNull List<Object> result, boolean subscribeLazyListeners) {
    if (subscribeLazyListeners) {
      subscribeLazyListeners(topic);
    }

    super.doComputeSubscribers(topic, result, subscribeLazyListeners);

    if (topic.getBroadcastDirection() == BroadcastDirection.TO_CHILDREN) {
      for (MessageBusImpl childBus : childBuses) {
        if (!childBus.isDisposed()) {
          childBus.doComputeSubscribers(topic, result, !childBus.owner.isParentLazyListenersIgnored());
        }
      }
    }
  }

  private void subscribeLazyListeners(@NotNull Topic<?> topic) {
    if (topic.getListenerClass() == Runnable.class) {
      return;
    }

    List<ListenerDescriptor> listenerDescriptors = topicClassToListenerDescriptor.remove(topic.getListenerClass().getName());
    if (listenerDescriptors == null) {
      return;
    }

    // use linked hash map for repeatable results
    Map<PluginId, List<Object>> listenerMap = new LinkedHashMap<>();
    for (ListenerDescriptor listenerDescriptor : listenerDescriptors) {
      try {
        listenerMap.computeIfAbsent(listenerDescriptor.pluginDescriptor.getPluginId(), __ -> new ArrayList<>()).add(
          owner.createListener(listenerDescriptor));
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

  @Override
  protected final void notifyOnSubscriptionToTopicToChildren(@NotNull Topic<?> topic) {
    for (MessageBusImpl childBus : childBuses) {
      childBus.subscriberCache.remove(topic);
      childBus.notifyOnSubscriptionToTopicToChildren(topic);
    }
  }

  @Override
  protected final void clearSubscriberCacheRecursively(@Nullable Map<Topic<?>, Object> handlers, @Nullable Topic<?> topic) {
    clearSubscriberCache(this, handlers, topic);
    childBuses.forEach(childBus -> childBus.clearSubscriberCacheRecursively(handlers, topic));
  }

  @Override
  final boolean notifyConnectionTerminated(Object[] topicAndHandlerPairs) {
    boolean isChildClearingNeeded = super.notifyConnectionTerminated(topicAndHandlerPairs);
    if (!isChildClearingNeeded) {
      return false;
    }

    childBuses.forEach(childBus -> childBus.clearSubscriberCache(topicAndHandlerPairs));

    // disposed handlers are not removed for TO_CHILDREN topics in the same way as for others directions because it is not wise to check each child bus -
    // waitingBuses list can be used instead of checking each child bus message queue
    SortedSet<MessageBusImpl> waitingBuses = myRootBus.myWaitingBuses.get();
    if (!waitingBuses.isEmpty()) {
      waitingBuses.removeIf(bus -> {
        JobQueue jobQueue = bus.myMessageQueue.get();
        return !jobQueue.queue.isEmpty() &&
               jobQueue.queue.removeIf(job -> MessageBusConnectionImpl.removeHandlersFromJob(job, topicAndHandlerPairs) && job.handlers.isEmpty()) &&
               jobQueue.current == null &&
               jobQueue.queue.isEmpty();
      });
    }
    return false;
  }

  @Override
  protected final void clearSubscriberCache(Object[] topicAndHandlerPairs) {
    super.clearSubscriberCache(topicAndHandlerPairs);
    childBuses.forEach(childBus -> childBus.clearSubscriberCache(topicAndHandlerPairs));
  }

  @Override
  protected final void removeChildConnectionsRecursively(@NotNull Topic<?> topic, @Nullable Object handlers) {
    childBuses.forEach(childBus -> childBus.removeChildConnectionsRecursively(topic, handlers));
  }

  @Override
  protected final void removeEmptyConnectionsRecursively() {
    super.removeEmptyConnectionsRecursively();

    childBuses.forEach(MessageBusImpl::removeEmptyConnectionsRecursively);
  }

  /**
   * Clear publisher cache, including child buses.
   */
  @Override
  public final void clearPublisherCache() {
    // keep it simple - we can infer plugin id from topic.getListenerClass(), but granular clearing not worth the code complication
    publisherCache.clear();
    childBuses.forEach(childBus -> {
      if (childBus instanceof CompositeMessageBus) {
        ((CompositeMessageBus)childBus).clearPublisherCache();
      }
      else {
        childBus.publisherCache.clear();
      }
    });
  }

  @Override
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

    // todo it means that order of subscribers is not preserved
    // it is very minor requirement, but still, makes sense to comply it
    if (newSubscribers != null) {
      mySubscribers.addAll(newSubscribers);
    }
    if (isChanged) {
      // we can check it more precisely, but for simplicity, just clear all
      // adding project level listener for app level topic is not recommended, but supported
      if (myRootBus != this) {
        myRootBus.subscriberCache.clear();
      }
      subscriberCache.clear();
    }
  }

  @Override
  public void disconnectPluginConnections(@NotNull Predicate<Class<?>> predicate) {
    super.disconnectPluginConnections(predicate);
    childBuses.forEach(bus -> bus.disconnectPluginConnections(predicate));
  }

  @Override
  @TestOnly
  public final void clearAllSubscriberCache() {
    LOG.assertTrue(myRootBus != this);
    myRootBus.subscriberCache.clear();
    subscriberCache.clear();
    childBuses.forEach(bus -> bus.subscriberCache.clear());
  }

  @Override
  protected final void disposeChildren() {
    childBuses.forEach(Disposer::dispose);
  }
}
