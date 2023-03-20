// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class DependencyContext implements NamingContext {
  private final static String STRING_TABLE_NAME = "strings.tab";
  private final PersistentStringEnumerator myEnumerator;

  private final Map<TypeRepr.AbstractType, TypeRepr.AbstractType> myTypeMap = new HashMap<>();
  private final Map<UsageRepr.Usage, UsageRepr.Usage> myUsageMap = new HashMap<>();
  private final PathRelativizerService myRelativizer;
  private final int myEmptyName;

  UsageRepr.Usage getUsage(final UsageRepr.Usage u) {
     final UsageRepr.Usage r = myUsageMap.get(u);

     if (r == null) {
       myUsageMap.put(u, u);
       return u;
     }

     return r;
   }

  TypeRepr.AbstractType getType(final TypeRepr.AbstractType t) {
    final TypeRepr.AbstractType r = myTypeMap.get(t);

    if (r != null) {
      return r;
    }

    myTypeMap.put(t, t);

    return t;
  }

  void clearMemoryCaches() {
    myTypeMap.clear();
    myUsageMap.clear();
  }

  static File getTableFile (final File rootDir, final String name) {
    final File file = new File(FileUtil.toSystemIndependentName(rootDir.getAbsoluteFile() + File.separator + name));
    FileUtil.createIfDoesntExist(file);
    return file;
  }

  DependencyContext(final File rootDir, PathRelativizerService relativizer) throws IOException {
    final File file = getTableFile(rootDir, STRING_TABLE_NAME);
    myEnumerator = new PersistentStringEnumerator(file.toPath(), true);
    myEmptyName = myEnumerator.enumerate("");
    myRelativizer = relativizer;
  }

  @Override
  @Nullable
  public String getValue(final int s) {
    try {
      String value = myEnumerator.valueOf(s);
      return value == null ? null : myRelativizer.toFull(value);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public int get(final String s) {
    try {
      return StringUtil.isEmpty(s) ? myEmptyName : myEnumerator.enumerate(myRelativizer.toRelative(s));
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public void close() {
    try {
      myEnumerator.close();
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public void flush() {
    myEnumerator.force();
  }

  public LoggerWrapper<Integer> getLogger(final Logger log) {
    return new LoggerWrapper<Integer>() {
      @Override
      public boolean isDebugEnabled() {
        return log.isDebugEnabled();
      }

      @Override
      public void debug(String comment, Integer s) {
        if (isDebugEnabled()) {
          log.debug(comment + getValue(s));
        }
      }

      @Override
      public void debug(String comment, String t) {
        if (isDebugEnabled()){
          log.debug(comment + t);
        }
      }

      @Override
      public void debug(String comment, boolean t) {
        if (isDebugEnabled()) {
          log.debug(comment + t);
        }
      }
    };
  }
}
