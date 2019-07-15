// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util.messages;

import com.intellij.util.messages.impl.MessageBusImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public final class MessageBusFactory {
  private static final AtomicReference<Impl> ourImpl = new AtomicReference<>(Impl.DEFAULT);

  private MessageBusFactory() {}

  @NotNull
  public static MessageBus newMessageBus(@NotNull Object owner) {
    return ourImpl.get().newMessageBus(owner);
  }

  @NotNull
  public static MessageBus newMessageBus(@NotNull Object owner, @Nullable MessageBus parentBus) {
    return ourImpl.get().newMessageBus(owner, parentBus);
  }

  public static void setImpl(@NotNull Impl impl) {
    ourImpl.set(impl);
  }

  public interface Impl {
    Impl DEFAULT = new Impl() {
      @NotNull
      @Override
      public MessageBus newMessageBus(@NotNull Object owner) {
        return new MessageBusImpl.RootBus(owner);
      }

      @NotNull
      @Override
      public MessageBus newMessageBus(@NotNull Object owner, @Nullable MessageBus parentBus) {
        return parentBus == null ? newMessageBus(owner) : new MessageBusImpl(owner, parentBus);
      }
    };

    @NotNull
    MessageBus newMessageBus(@NotNull Object owner);

    @NotNull
    MessageBus newMessageBus(@NotNull Object owner, @Nullable MessageBus parentBus);
  }
}