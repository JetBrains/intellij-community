package com.intellij.util;

public interface Generator<T> {
  void generate(Processor<T> processor);
}
