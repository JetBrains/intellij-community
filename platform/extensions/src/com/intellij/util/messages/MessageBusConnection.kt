// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Aggregates multiple topic subscriptions for particular {@link MessageBus message bus}. I.e. every time a client wants to
 * listen for messages it should grab appropriate connection (or create a new one) and {@link #subscribe(Topic, Object) subscribe}
 * to particular endpoint.
 */
public interface MessageBusConnection extends SimpleMessageBusConnection, Disposable {
  /**
   * Subscribes to the target topic within the current connection using {@link #setDefaultHandler(Runnable) default handler}.
   *
   * @param topic  target endpoint
   * @param <L>    interface for working with the target topic
   * @throws IllegalStateException    if {@link #setDefaultHandler(Runnable) default handler} hasn't been defined or
   *                                  has incompatible type with the {@link Topic#getListenerClass() topic's business interface}
   *                                  or if target topic is already subscribed within the current connection
   */
  <L> void subscribe(@NotNull Topic<L> topic) throws IllegalStateException;

  /**
   * Allows specifying default handler to use during {@link #subscribe(Topic) anonymous subscriptions}.
   *
   * @param handler  handler to use
   */
  void setDefaultHandler(@Nullable MessageHandler handler);

  default void setDefaultHandler(@NotNull Runnable runnable) {
    setDefaultHandler((event, params) -> runnable.run());
  }

  /**
   * Forces to process any queued but not delivered events.
   *
   * @see MessageBus#syncPublisher(Topic)
   */
  void deliverImmediately();
}
