package org.jetbrains.concurrency;

import com.intellij.util.Consumer;
import com.intellij.util.Function;

public abstract class ConsumerFunction<T> implements Function<T, Void>, Consumer<T> {
  @Override
  public Void fun(T t) {
    consume(t);
    return null;
  }
}