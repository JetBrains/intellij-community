/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class ResolveProfiler {
  @NonNls private static final String PATH = "../../resolve_info/";
  private static final boolean DISABLED = true;

  private static final ThreadLocal<ThreadInfo> threadMap = new ThreadLocal<ThreadInfo>();
  private static volatile int fileCount = 0;

  private static class ThreadInfo {
    private final String myFileName;
    private final Deque<Long> myTimeStack = new ArrayDeque<Long>();

    private String myPrefix = "";

    private ThreadInfo(@NotNull String name) {
      myFileName = name;
    }

    @NotNull
    public String getName() {
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

  public static void write(String prefix, @NotNull PsiElement expression, long time) {
    write(getInfo(prefix, expression, time));
  }

  public static void write(@NotNull String s) {
    if (DISABLED) return;

    final ThreadInfo threadInfo = getThreadInfo();
    try {
      final FileWriter writer = new FileWriter(threadInfo.getName(), true);
      try {
        writer.write(threadInfo.getPrefix());
        writer.write(s);
        writer.write('\n');
      }
      finally {
        writer.close();
      }
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    if (threadInfo.getPrefix().isEmpty()) {
      threadMap.set(null);
    }
  }

  @NotNull
  private static ThreadInfo getThreadInfo() {
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

  public static String getInfo(String prefix, @NotNull PsiElement expression, long time) {
    PsiFile file = expression.getContainingFile();
    String text = expression.getText();
    String textInfo = text != null ? StringUtil.escapeLineBreak(text) : "<null>";
    return prefix + " :: " + (file != null ? file.getName() : "<no file>") + " :: " + textInfo + " :: " + expression.hashCode()+ " :: " + time;
  }

}
