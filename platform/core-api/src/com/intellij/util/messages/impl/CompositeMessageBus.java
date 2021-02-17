// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ArrayUtilRt;
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
import java.util.function.Predicate;

class CompositeMessageBus extends MessageBusImpl implements MessageBusEx {
  private final List<MessageBusImpl> childBuses = ContainerUtil.createLockFreeCopyOnWriteList();
  private volatile @NotNull Map<String, List<ListenerDescriptor>> topicClassToListenerDescriptor = Collections.emptyMap();

  CompositeMessageBus(@NotNull MessageBusOwner owner, @NotNull CompositeMessageBus parentBus) {
    super(owner, parentBus);
  }

  // root message bus constructor
  CompositeMessageBus(@NotNull MessageBusOwner owner) {
    super(owner);
  }

  /**
   * Must be a concurrent map, because remove operation may be concurrently performed (synchronized only per topic).
   */
  @Override
  public final void setLazyListeners(@NotNull Map<String, List<ListenerDescriptor>> map) {
    if (topicClassToListenerDescriptor == Collections.<String, List<ListenerDescriptor>>emptyMap()) {
      topicClassToListenerDescriptor = map;
    }
    else {
      topicClassToListenerDescriptor.putAll(map);
      // adding project level listener for app level topic is not recommended, but supported
      if (rootBus != this) {
        rootBus.subscriberCache.clear();
      }
      subscriberCache.clear();
    }
  }

  @Override
  final boolean hasChildren() {
    return !childBuses.isEmpty();
  }

  final void addChild(@NotNull MessageBusImpl bus) {
    childBuses.add(bus);
  }

  final void onChildBusDisposed(@NotNull MessageBusImpl childBus) {
    boolean removed = childBuses.remove(childBus);
    rootBus.waitingBuses.get().remove(childBus);

    MessageBusImpl parentBus = this;
    do {
      parentBus.subscriberCache.clear();
    }
    while ((parentBus = parentBus.parentBus) != null);
    LOG.assertTrue(removed);
  }

  @Override
  final <L> @NotNull MessagePublisher<L> createPublisher(@NotNull Topic<L> topic, @NotNull BroadcastDirection direction) {
    if (direction == BroadcastDirection.TO_PARENT) {
      return new ToParentMessagePublisher<>(topic, this);
    }
    if (direction == BroadcastDirection.TO_DIRECT_CHILDREN) {
      if (parentBus != null) {
        throw new IllegalArgumentException("Broadcast direction TO_DIRECT_CHILDREN is allowed only for app level message bus. " +
                                           "Please publish to app level message bus or change topic " + topic.getListenerClass() +
                                           " broadcast direction to NONE or TO_PARENT");
      }
      return new ToDirectChildrenMessagePublisher<>(topic, this);
    }
    return new MessagePublisher<>(topic, this);
  }

  private static final class ToDirectChildrenMessagePublisher<L> extends MessagePublisher<L>  implements InvocationHandler {
    ToDirectChildrenMessagePublisher(@NotNull Topic<L> topic, @NotNull CompositeMessageBus bus) {
      super(topic, bus);
    }

    @Override
    final boolean publish(@NotNull Method method, Object[] args, @Nullable MessageQueue jobQueue) {
      List<Throwable> exceptions = null;
      boolean hasHandlers = false;

      @Nullable Object @NotNull [] handlers = bus.subscriberCache.computeIfAbsent(topic, topic1 -> bus.computeSubscribers(topic1));
      if (handlers.length != 0) {
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
          return result.isEmpty() ? ArrayUtilRt.EMPTY_OBJECT_ARRAY : result.toArray();
        });
        if (handlers.length == 0) {
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
  final @Nullable Object @NotNull [] computeSubscribers(@NotNull Topic<?> topic) {
    // light project
    if (owner.isDisposed()) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    return super.computeSubscribers(topic);
  }

  @Override
  final void doComputeSubscribers(@NotNull Topic<?> topic, @NotNull List<Object> result, boolean subscribeLazyListeners) {
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

  private <L> void subscribeLazyListeners(@NotNull Topic<L> topic) {
    if (topic.getListenerClass() == Runnable.class) {
      return;
    }

    List<ListenerDescriptor> listenerDescriptors = topicClassToListenerDescriptor.remove(topic.getListenerClass().getName());
    if (listenerDescriptors == null) {
      return;
    }

    // use linked hash map for repeatable results
    Map<PluginId, List<L>> listenerMap = new LinkedHashMap<>();
    for (ListenerDescriptor listenerDescriptor : listenerDescriptors) {
      try {
        //noinspection unchecked
        listenerMap.computeIfAbsent(listenerDescriptor.pluginDescriptor.getPluginId(), __ -> new ArrayList<>())
          .add((L)owner.createListener(listenerDescriptor));
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

    listenerMap.forEach((key, listeners) -> subscribers.add(new DescriptorBasedMessageBusConnection<>(key, topic, listeners)));
  }

  @Override
  final void notifyOnSubscriptionToTopicToChildren(@NotNull Topic<?> topic) {
    for (MessageBusImpl childBus : childBuses) {
      childBus.subscriberCache.remove(topic);
      childBus.notifyOnSubscriptionToTopicToChildren(topic);
    }
  }

  @Override
  final boolean notifyConnectionTerminated(Object @NotNull [] topicAndHandlerPairs) {
    boolean isChildClearingNeeded = super.notifyConnectionTerminated(topicAndHandlerPairs);
    if (!isChildClearingNeeded) {
      return false;
    }

    childBuses.forEach(childBus -> childBus.clearSubscriberCache(topicAndHandlerPairs));

    // disposed handlers are not removed for TO_CHILDREN topics in the same way as for others directions
    // because it is not wise to check each child bus - waitingBuses list can be used instead of checking each child bus message queue
    Set<MessageBusImpl> waitingBuses = rootBus.waitingBuses.get();
    if (!waitingBuses.isEmpty()) {
      waitingBuses.removeIf(bus -> {
        MessageQueue messageQueue = bus.messageQueue.get();
        Deque<Message> queue = messageQueue.queue;
        return !queue.isEmpty() &&
               queue.removeIf(message -> MessageBusConnectionImpl.nullizeHandlersFromMessage(message, topicAndHandlerPairs)) &&
               messageQueue.current == null &&
               queue.isEmpty();
      });
    }
    return false;
  }

  @Override
  final void clearSubscriberCache(Object @NotNull [] topicAndHandlerPairs) {
    super.clearSubscriberCache(topicAndHandlerPairs);
    childBuses.forEach(childBus -> childBus.clearSubscriberCache(topicAndHandlerPairs));
  }

  @Override
  final void removeEmptyConnectionsRecursively() {
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
    topicClassToListenerDescriptor.values().removeIf(descriptors -> {
      if (descriptors.removeIf(descriptor -> descriptor.pluginDescriptor.getPluginId().equals(pluginId))) {
        return descriptors.isEmpty();
      }
      return false;
    });

    if (listenerDescriptors.isEmpty() || subscribers.isEmpty()) {
      return;
    }

    Map<String, Set<String>> topicToDescriptors = new HashMap<>();
    for (ListenerDescriptor descriptor : listenerDescriptors) {
      topicToDescriptors.computeIfAbsent(descriptor.topicClassName, __ -> new HashSet<>()).add(descriptor.listenerClassName);
    }

    boolean isChanged = false;
    List<DescriptorBasedMessageBusConnection<?>> newSubscribers = null;
    for (Iterator<MessageHandlerHolder> connectionIterator = subscribers.iterator(); connectionIterator.hasNext(); ) {
      MessageHandlerHolder holder = connectionIterator.next();
      if (!(holder instanceof DescriptorBasedMessageBusConnection)) {
        continue;
      }

      //noinspection unchecked
      DescriptorBasedMessageBusConnection<Object> connection = (DescriptorBasedMessageBusConnection<Object>)holder;
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
        newSubscribers.add(new DescriptorBasedMessageBusConnection<>(pluginId, connection.topic, newHandlers));
      }
    }

    // todo it means that order of subscribers is not preserved
    // it is very minor requirement, but still, makes sense to comply it
    if (newSubscribers != null) {
      subscribers.addAll(newSubscribers);
    }
    if (isChanged) {
      // we can check it more precisely, but for simplicity, just clear all
      // adding project level listener for app level topic is not recommended, but supported
      if (rootBus != this) {
        rootBus.subscriberCache.clear();
      }
      subscriberCache.clear();
    }
  }

  @Override
  public void disconnectPluginConnections(@NotNull Predicate<? super Class<?>> predicate) {
    super.disconnectPluginConnections(predicate);
    childBuses.forEach(bus -> bus.disconnectPluginConnections(predicate));
  }

  @Override
  @TestOnly
  public final void clearAllSubscriberCache() {
    LOG.assertTrue(rootBus != this);
    rootBus.subscriberCache.clear();
    subscriberCache.clear();
    childBuses.forEach(bus -> bus.subscriberCache.clear());
  }

  @Override
  protected final void disposeChildren() {
    childBuses.forEach(Disposer::dispose);
  }
}
