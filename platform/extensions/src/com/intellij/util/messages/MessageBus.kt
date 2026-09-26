// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages

import com.intellij.openapi.Disposable
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Core of IntelliJ Platform messaging infrastructure. Basic functions:
 *  * allows to [push messages][syncPublisher];
 *  * allows to [create connections][connect] for further [subscriptions][MessageBusConnection.subscribe];
 *
 * Use [com.intellij.openapi.application.Application.getMessageBus] or [com.intellij.openapi.project.Project.getMessageBus] to obtain one.
 *
 * Please see [Messaging Infrastructure](https://plugins.jetbrains.com/docs/intellij/messaging-infrastructure.html) and
 * [Listeners](https://plugins.jetbrains.com/docs/intellij/plugin-listeners.html).
 */
interface MessageBus : Disposable {
  /**
   * Message buses can be organised into hierarchies. That allows facilities [broadcasting][Topic.getBroadcastDirection].
   * The current method exposes parent bus (if any is defined).
   */
  val parent: MessageBus?

  /**
   * Create a new [Disposable] connection that is disconnected on message bus dispose, or on explicitly dispose.
   */
  fun connect(): MessageBusConnection

  /**
   * Create a new connection that is disconnected on message bus dispose, or on explicit [SimpleMessageBusConnection.disconnect].
   */
  @Internal
  fun simpleConnect(): SimpleMessageBusConnection

  /**
   * Allows creating new connection that is bound to the given [Disposable].
   * That means that returned connection
   * will be automatically [released][MessageBusConnection.dispose] if given [disposable parent][Disposable] is collected.
   *
   * @param parentDisposable target parent disposable to which life cycle newly created connection shall be bound
   */
  fun connect(parentDisposable: Disposable): MessageBusConnection

  fun connect(coroutineScope: CoroutineScope): SimpleMessageBusConnection

  /**
   * Allows retrieving an interface for publishing messages to the target topic.
   *
   * Basically, the whole processing looks as follows:
   *
   *  1. Messaging clients create new [connections][MessageBusConnection] within the target message bus and
   * [subscribe][MessageBusConnection.subscribe] to the target [topics][Topic];
   *
   *  1. Every time somebody wants to send a message for a particular topic, he or she calls current method and receives an object
   * that conforms to the [business interface][Topic.getListenerClass] of the target topic. Every method call on that
   * object is dispatched by the messaging infrastructure to the subscribers.
   * [broadcasting][Topic.getBroadcastDirection] is performed if necessary as well;
   *
   * It's also very important to understand message processing strategy in case of **nested dispatches**.
   * Consider the following situation:
   *
   *  1. `Subscriber<sub>1</sub>` and `subscriber<sub>2</sub>` are registered for the same topic within
   * the same message bus;
   *
   *  1. `Message<sub>1</sub>` is sent to that topic within the same message bus;
   *  1. Queued message delivery starts;
   *  1. Queued message delivery ends as there are no messages queued but not dispatched;
   *  1. `Message<sub>1</sub>` is queued for delivery to both subscribers;
   *  1. Queued messages delivery starts;
   *  1. `Message<sub>1</sub>` is being delivered to the `subscriber<sub>1</sub>`;
   *  1. `Subscriber<sub>1</sub>` sends `message<sub>2</sub>` to the same topic within the same bus;
   *  1. Queued messages delivery starts;
   *
   * **Important:** `subscriber<sub>2</sub>` is being notified about all queued but not delivered messages,
   * i.e., its callback is invoked for the message<sub>1</sub>;
   *
   *  1. Queued messages delivery ends because all subscribers have been notified on the `message<sub>1</sub>`;
   *  1. `Message<sub>2</sub>` is queued for delivery to both subscribers;
   *  1. Queued messages delivery starts;
   *  1. `Subscriber<sub>1</sub>` is notified on `message<sub>2</sub>`
   *  1. `Subscriber<sub>2</sub>` is notified on `message<sub>2</sub>`
   *
   * **Thread-safety.**
   * All subscribers are notified sequentially from the calling thread.
   *
   * **Memory management.**
   * Returned objects are very light-weight and stateless, so, they are cached by the message bus in `'per-topic'` manner.
   * That means that caller of this method is not obliged to keep returned reference along with the reference to the message for
   * further publishing. It's enough to keep reference to the message bus only and publish
   * like `'messageBus.syncPublisher(targetTopic).targetMethod()'`.
   *
   * @param topic target topic
   * @param <L> [business interface][Topic.getListenerClass] of the target topic
   * @return publisher for a target topic
   */
  fun <L : Any> syncPublisher(topic: Topic<L>): L

  @Internal
  fun <L : Any> syncAndPreloadPublisher(topic: Topic<L>): L

  /**
   * Disposes current bus, i.e., drops all queued but not delivered messages (if any) and disallows further [connections](.connect).
   */
  override fun dispose()

  /**
   * Returns true if this bus is disposed.
   */
  val isDisposed: Boolean

  /**
   * @return true, when events in the given topic are being dispatched in the current thread,
   * and not all listeners have received the events yet.
   */
  fun hasUndeliveredEvents(topic: Topic<*>): Boolean
}