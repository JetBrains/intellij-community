// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link Topic} that a client can subscribe to, but must not publish on.
 * <p>
 * The owner of the topic keeps a private {@link Topic} for its publishing, and exposes only this wrapper.
 * A subscription to the wrapper goes to the wrapped topic.
 * {@link MessageBus#syncPublisher} logs an error once for each plugin that publishes on the wrapper,
 * and then publishes on the wrapped topic. The error names the plugin of the publisher.
 * <p>
 * The wrapper is a {@link Topic}, so a public {@code Topic} constant can hold it and stay binary compatible.
 * The owner then publishes on the private topic, and clients that publish on the constant get the error.
 *
 * @param <L> the listener interface of the wrapped topic
 */
@ApiStatus.Internal
public final class ReadOnlyTopic<L> extends Topic<L> {
  private static final Object NO_PLUGIN = new Object();

  private final @NotNull Topic<L> myWrappedTopic;
  private final Set<Object> myReportedPublishers = ConcurrentHashMap.newKeySet();

  public ReadOnlyTopic(@NotNull Topic<L> wrappedTopic) {
    super(wrappedTopic.getDisplayName(), wrappedTopic.getListenerClass(), wrappedTopic.getBroadcastDirection(),
          wrappedTopic.isImmediateDelivery());
    if (wrappedTopic instanceof ReadOnlyTopic) {
      throw new IllegalArgumentException("The topic is read-only already: " + wrappedTopic);
    }
    myWrappedTopic = wrappedTopic;
  }

  /**
   * @return the topic that the message bus uses for this wrapper
   */
  public @NotNull Topic<L> getWrappedTopic() {
    return myWrappedTopic;
  }

  /**
   * Marks that a message bus has reported a publish on this wrapper by a plugin.
   *
   * @param pluginId the plugin of the publisher, or {@code null} when the publisher is in no plugin
   * @return {@code true} for the first call with this plugin only
   */
  public boolean markPublishReported(@Nullable PluginId pluginId) {
    return myReportedPublishers.add(pluginId == null ? NO_PLUGIN : pluginId);
  }

  @Override
  public String toString() {
    return "ReadOnly" + myWrappedTopic;
  }
}
