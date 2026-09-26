// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.profiling;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * @author Max Medvedev
 */
public final class ResolveProfiler {
  private static final @NonNls String PATH = "../../resolve_info/";
  private static final boolean DISABLED = true;

  private static final ThreadLocal<ThreadInfo> threadMap = new ThreadLocal<>();
  private static volatile int fileCount;

  private static final class ThreadInfo {
    private final String myFileName;
    private final Deque<Long> myTimeStack = new ArrayDeque<>();

    private String myPrefix = "";

    private ThreadInfo(@NotNull @NonNls String name) {
      myFileName = name;
    }

    public @NotNull String getName() {
      return myFileName;
    }

    public void start() {
      myTimeStack.push(System.nanoTime());
      myPrefix += "  ";
    }

    public long finish() {
      myPrefix = myPrefix.substring(2);

      final Long time = myTimeStack.pop();
      return (System.nanoTime() - time) / 1000;
    }

    private String getPrefix() {
      return myPrefix;
    }
  }

  public static void start() {
    if (DISABLED) return;
    getThreadInfo().start();
  }

  public static long finish() {
    if (DISABLED) return -1;
    return getThreadInfo().finish();
  }

  public static void write(@NonNls String prefix, @NotNull PsiElement expression, long time) {
    if (DISABLED) return;

    write(getInfo(prefix, expression, time));
  }

  public static void write(@NotNull String s) {
    if (DISABLED) return;

    final ThreadInfo threadInfo = getThreadInfo();
    try (FileWriter writer = new FileWriter(threadInfo.getName(), true)) {
      writer.write(threadInfo.getPrefix());
      writer.write(s);
      writer.write('\n');
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    if (threadInfo.getPrefix().isEmpty()) {
      threadMap.remove();
    }
  }

  private static @NotNull ThreadInfo getThreadInfo() {
    ThreadInfo info = threadMap.get();
    if (info == null) {
      synchronized (ResolveProfiler.class) {
        info = new ThreadInfo(PATH + "out" + fileCount + ".txt");
        fileCount++;
      }
      threadMap.set(info);
    }
    return info;
  }

  public static @NonNls String getInfo(String prefix, @NotNull PsiElement expression, long time) {
    PsiFile file = expression.getContainingFile();
    String text = expression.getText();
    String textInfo = text != null ? StringUtil.escapeLineBreak(text) : "<null>";
    return prefix + " :: " + (file != null ? file.getName() : "<no file>") + " :: " + textInfo + " :: " + expression.hashCode()+ " :: " + time;
  }

}
