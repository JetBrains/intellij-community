// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon;

import com.sun.jna.Library;
import com.sun.jna.Native;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

@SuppressWarnings("SpellCheckingInspection")
public class UnixDaemonizer {
  private static final @Nullable LibC LIBC = tryLoadLibC();

  private static @Nullable LibC tryLoadLibC() {
    try {
      return Native.load("c", LibC.class);
    }
    catch (UnsatisfiedLinkError e) {
      return null;
    }
  }

  public static void daemonize() {
    if (LIBC == null) {
      throw new IllegalStateException("Unable to load libc");
    }

    int sid = LIBC.setsid();
    if (sid == -1) {
      throw new IllegalStateException("setsid: " + getLastErrorString());
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void closeStdStreams() {
    try {
      System.in.close();
    }
    catch (IOException e) {
      System.err.println("Unable to close daemon stdin: " + e.getMessage());
    }
    System.out.close();
    System.err.close();
  }

  private static String getLastErrorString() {
    return Objects.requireNonNull(LIBC).strerror(Native.getLastError());
  }

  private interface LibC extends Library {
    int setsid();

    String strerror(int errno);
  }
}
