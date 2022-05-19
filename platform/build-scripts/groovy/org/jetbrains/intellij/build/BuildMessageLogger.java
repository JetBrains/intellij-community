// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

public abstract class BuildMessageLogger {
  public abstract void processMessage(LogMessage message);

  /**
   * Called for a logger of a forked task when the task is completed (i.e. {@link #processMessage} method won't be called anymore.
   */
  public void dispose() {
  }
}
