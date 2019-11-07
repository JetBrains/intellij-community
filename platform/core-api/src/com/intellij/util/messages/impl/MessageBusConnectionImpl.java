// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.messages.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.SmartFMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

final class MessageBusConnectionImpl implements MessageBusConnection {
  private static final Logger LOG = Logger.getInstance(MessageBusConnectionImpl.class);

  private final MessageBusImpl myBus;
  @SuppressWarnings("SSBasedInspection")
  private final ThreadLocal<Queue<Message>> myPendingMessages = MessageBusImpl.createThreadLocalQueue();

  private MessageHandler myDefaultHandler;
  private volatile SmartFMap<Topic<?>, Object> mySubscriptions = SmartFMap.emptyMap();

  MessageBusConnectionImpl(@NotNull MessageBusImpl bus) {
    myBus = bus;
  }

  @Override
  public <L> void subscribe(@NotNull Topic<L> topic, @NotNull L handler) {
    boolean notifyBusAboutTopic = false;
    synchronized (myPendingMessages) {
      Object currentHandler = mySubscriptions.get(topic);
      if (currentHandler == null) {
        mySubscriptions = mySubscriptions.plus(topic, handler);
        notifyBusAboutTopic = true;
      }
      else if (currentHandler instanceof List<?>) {
        //noinspection unchecked
        ((List<L>)currentHandler).add(handler);
      }
      else {
        List<Object> newList = new ArrayList<>();
        newList.add(currentHandler);
        newList.add(handler);
        mySubscriptions = mySubscriptions.plus(topic, newList);
      }
    }

    if (notifyBusAboutTopic) {
      myBus.notifyOnSubscription(this, topic);
    }
  }

  // avoid notifyOnSubscription and map modification for each handler
  <L> void subscribe(@NotNull Topic<L> topic, @NotNull List<Object> handlers) {
    boolean notifyBusAboutTopic = false;
    synchronized (myPendingMessages) {
      Object currentHandler = mySubscriptions.get(topic);
      if (currentHandler == null) {
        mySubscriptions = mySubscriptions.plus(topic, handlers);
        notifyBusAboutTopic = true;
      }
      else if (currentHandler instanceof List<?>) {
        //noinspection unchecked
        ((List<Object>)currentHandler).addAll(handlers);
      }
      else {
        List<Object> newList = new ArrayList<>(handlers.size() + 1);
        newList.add(currentHandler);
        newList.addAll(handlers);
        mySubscriptions = mySubscriptions.plus(topic, newList);
      }
    }

    if (notifyBusAboutTopic) {
      myBus.notifyOnSubscription(this, topic);
    }
  }

  @Override
  public <L> void subscribe(@NotNull Topic<L> topic) throws IllegalStateException {
    MessageHandler defaultHandler = myDefaultHandler;
    if (defaultHandler == null) {
      throw new IllegalStateException("Connection must have default handler installed prior to any anonymous subscriptions. "
                                      + "Target topic: " + topic);
    }
    if (topic.getListenerClass().isInstance(defaultHandler)) {
      throw new IllegalStateException("Can't subscribe to the topic '" + topic + "'. Default handler has incompatible type - expected: '" +
                                      topic.getListenerClass() + "', actual: '" + defaultHandler.getClass() + "'");
    }

    //noinspection unchecked
    subscribe(topic, (L)defaultHandler);
  }

  @Override
  public void setDefaultHandler(MessageHandler handler) {
    myDefaultHandler = handler;
  }

  @Override
  public void dispose() {
    myPendingMessages.get();
    myPendingMessages.remove();
    myBus.notifyConnectionTerminated(this);
  }

  @Override
  public void disconnect() {
    Disposer.dispose(this);
  }

  @Override
  public void deliverImmediately() {
    Queue<Message> messages = myPendingMessages.get();
    while (!messages.isEmpty()) {
      myBus.deliverSingleMessage();
    }
  }

  void deliverMessage(@NotNull Message message) {
    final Message messageOnLocalQueue = myPendingMessages.get().poll();
    assert messageOnLocalQueue == message;

    Topic<?> topic = message.getTopic();
    Object handler = mySubscriptions.get(topic);
    try {
      if (handler == myDefaultHandler) {
        myDefaultHandler.handle(message.getListenerMethod(), message.getArgs());
      }
      else {
        if (handler instanceof List<?>) {
          for (Object o : (List<?>)handler) {
            myBus.invokeListener(message, o);
          }
        }
        else {
          myBus.invokeListener(message, handler);
        }
      }
    }
    catch (AbstractMethodError e) {
      //Do nothing. This listener just does not implement something newly added yet.
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (InvocationTargetException e) {
      if (e.getCause() instanceof ProcessCanceledException) {
        throw (ProcessCanceledException)e.getCause();
      }
      LOG.error(e.getCause() == null ? e : e.getCause());
    }
    catch (Throwable e) {
      LOG.error(e.getCause() == null ? e : e.getCause());
    }
  }

  void scheduleMessageDelivery(@NotNull Message message) {
    myPendingMessages.get().offer(message);
  }

  boolean containsMessage(@NotNull Topic<?> topic) {
    Queue<Message> pendingMessages = myPendingMessages.get();
    if (pendingMessages.isEmpty()) return false;

    for (Message message : pendingMessages) {
      if (message.getTopic() == topic) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return mySubscriptions.toString();
  }

  @NotNull
  MessageBusImpl getBus() {
    return myBus;
  }
}
