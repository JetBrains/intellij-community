package com.intellij.database.util;

import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public final class DataSourceUtilBase {

  @SuppressWarnings("unchecked")
  public static @NotNull <T extends EventListener, U extends T> EventDispatcher<U> eventDispatcher(@NotNull Class<T> listenerClass) {
    return (EventDispatcher)EventDispatcher.create(listenerClass);
  }

  private DataSourceUtilBase() {
  }
}
