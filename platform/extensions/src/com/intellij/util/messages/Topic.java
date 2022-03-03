// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.messages;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines messaging endpoint within particular {@link MessageBus bus}.
 *
 * @param <L>  type of the interface that defines contract for working with the particular topic instance
 */
@ApiStatus.NonExtendable
public class Topic<L> {
  /**
   * Indicates that messages the of annotated topic are published to a application level message bus.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.FIELD)
  public @interface AppLevel {}

  /**
   * Indicates that messages the of annotated topic are published to a project level message bus.
   */
  @Retention(RetentionPolicy.SOURCE)
  @Target(ElementType.FIELD)
  public @interface ProjectLevel {}

  private final String myDisplayName;
  private final Class<L> myListenerClass;
  private final BroadcastDirection myBroadcastDirection;
  private final boolean myImmediateDelivery;

  public Topic(@NonNls @NotNull String name, @NotNull Class<L> listenerClass) {
    this(name, listenerClass, BroadcastDirection.TO_CHILDREN);
  }

  /**
   * Consider using {@link #Topic(Class, BroadcastDirection)} and {@link BroadcastDirection#NONE}.
   */
  public Topic(@NotNull Class<L> listenerClass) {
    this(listenerClass.getSimpleName(), listenerClass, BroadcastDirection.TO_CHILDREN);
  }

  public Topic(@NotNull Class<L> listenerClass, @NotNull BroadcastDirection broadcastDirection) {
    this(listenerClass.getSimpleName(), listenerClass, broadcastDirection);
  }

  @ApiStatus.Experimental
  public Topic(@NotNull Class<L> listenerClass, @NotNull BroadcastDirection broadcastDirection, boolean immediateDelivery) {
    myDisplayName = listenerClass.getSimpleName();
    myListenerClass = listenerClass;
    myBroadcastDirection = broadcastDirection;
    myImmediateDelivery = immediateDelivery;
  }

  public Topic(@NonNls @NotNull String name, @NotNull Class<L> listenerClass, @NotNull BroadcastDirection broadcastDirection) {
    myDisplayName = name;
    myListenerClass = listenerClass;
    myBroadcastDirection = broadcastDirection;
    myImmediateDelivery = false;
  }

  /**
   * @return human-readable name of the current topic. Is intended to be used in informational/logging purposes only
   */
  @NonNls
  public @NotNull String getDisplayName() {
    return myDisplayName;
  }

  /**
   * Allows to retrieve class that defines contract for working with the current topic. Either publishers or subscribers use it:
   * <ul>
   *   <li>
   *     publisher {@link MessageBus#syncPublisher(Topic) receives} object that IS-A target interface from the messaging infrastructure.
   *     It calls target method with the target arguments on it then (method of the interface returned by the current method);
   *   </li>
   *   <li>
   *     the same method is called on handlers of all {@link MessageBusConnection#subscribe(Topic, Object) subscribers} that
   *     should receive the message;
   *   </li>
   * </ul>
   *
   * @return    class of the interface that defines contract for working with the current topic
   */
  public @NotNull Class<L> getListenerClass() {
    return myListenerClass;
  }

  @Override
  public String toString() {
    return "Topic(" +
           "name='" + myDisplayName + '\'' +
           ", listenerClass=" + myListenerClass +
           ", broadcastDirection=" + myBroadcastDirection +
           ", immediateDelivery=" + myImmediateDelivery +
           ')';
  }

  public static @NotNull <L> Topic<L> create(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass) {
    return new Topic<>(displayName, listenerClass);
  }

  public static @NotNull <L> Topic<L> create(@NonNls @NotNull String displayName, @NotNull Class<L> listenerClass, BroadcastDirection direction) {
    return new Topic<>(displayName, listenerClass, direction);
  }

  /**
   * @return    broadcasting strategy configured for the current topic. Default value is {@link BroadcastDirection#TO_CHILDREN}
   * @see BroadcastDirection
   */
  public @NotNull BroadcastDirection getBroadcastDirection() {
    return myBroadcastDirection;
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public boolean isImmediateDelivery() {
    return myImmediateDelivery;
  }

  /**
   * {@link MessageBus Message buses} may be organised into {@link MessageBus#getParent() hierarchies}. That allows to provide
   * additional messaging features like {@code 'broadcasting'}. Here it means that messages sent to particular topic within
   * particular message bus may be dispatched to subscribers of the same topic within another message buses.
   * <p/>
   * Current enum holds available broadcasting options.
   */
  public enum BroadcastDirection {
    /**
     * The message is dispatched to all subscribers of the target topic registered within the child message buses.
     * <p/>
     * Example:
     * <pre>
     *                         parent-bus &lt;--- topic1
     *                          /       \
     *                         /         \
     *    topic1 ---&gt; child-bus1     child-bus2 &lt;--- topic1
     * </pre>
     * <p/>
     * Here subscribers of the {@code 'topic1'} registered within the {@code 'child-bus1'} and {@code 'child-bus2'}
     * will receive the message sent to the {@code 'topic1'} topic at the {@code 'parent-bus'}.
     */
    TO_CHILDREN,

    /**
     * Use only for application level publishers. To avoid collection subscribers from modules.
     */
    @ApiStatus.Experimental
    TO_DIRECT_CHILDREN,

    /**
     * No broadcasting is performed for the
     */
    NONE,

    /**
     * The message send to particular topic at particular bus is dispatched to all subscribers of the same topic within the parent bus.
     * <p/>
     * Example:
     * <pre>
     *           parent-bus &lt;--- topic1
     *                |
     *            child-bus &lt;--- topic1
     * </pre>
     * <p/>
     * Here subscribers of the {@code topic1} registered within {@code 'parent-bus'} will receive messages posted
     * to the same topic within {@code 'child-bus'}.
     */
    TO_PARENT
  }
}
