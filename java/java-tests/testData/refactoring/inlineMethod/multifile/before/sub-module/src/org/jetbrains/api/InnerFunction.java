package org.jetbrains.api;

public interface InnerFunction<T, R>  {
  R apply(T t);
}
