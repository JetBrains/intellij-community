/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: db
 */
class DependencyContext implements NamingContext {
  private final static String STRING_TABLE_NAME = "strings.tab";
  private final PersistentStringEnumerator myEnumerator;

  private final Map<TypeRepr.AbstractType, TypeRepr.AbstractType> myTypeMap = new HashMap<>();
  private final Map<UsageRepr.Usage, UsageRepr.Usage> myUsageMap = new HashMap<>();
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

  DependencyContext(final File rootDir) throws IOException {
    final File file = getTableFile(rootDir, STRING_TABLE_NAME);
    myEnumerator = new PersistentStringEnumerator(file, true);
    myEmptyName = myEnumerator.enumerate("");
  }

  @Nullable
  public String getValue(final int s) {
    try {
      return myEnumerator.valueOf(s);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public int get(final String s) {
    try {
      return StringUtil.isEmpty(s) ? myEmptyName : myEnumerator.enumerate(s);
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

  public LoggerWrapper<Integer> getLogger(final com.intellij.openapi.diagnostic.Logger log) {
    return new LoggerWrapper<Integer>() {
      public boolean isDebugEnabled() {
        return log.isDebugEnabled();
      }

      @Override
      public void debug(String comment, Integer s) {
        if (log.isDebugEnabled()) {
          log.debug(comment + getValue(s));
        }
      }

      @Override
      public void debug(String comment, String t) {
        if (log.isDebugEnabled()){
          log.debug(comment + t);
        }
      }

      @Override
      public void debug(String comment, boolean t) {
        if (log.isDebugEnabled()) {
          log.debug(comment + Boolean.toString(t));
        }
      }
    };
  }
}
