package com.intellij.util.concurrency.readwrite;

public interface ActiveRunnable {

  Object run() throws Throwable;
}
