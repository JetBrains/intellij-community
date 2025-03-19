// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

interface LoggerWrapper<T> {
  boolean isDebugEnabled();
  void debug(String comment, T t);
  void debug(String comment, String t);
  void debug(String comment, boolean t);
}
