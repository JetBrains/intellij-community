package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 2:03
 * To change this template use File | Settings | File Templates.
 */
class DependencyContext {
  private final static String STRING_TABLE_NAME = "strings.tab";
  private final PersistentStringEnumerator myEnumerator;

  private final Map<TypeRepr.AbstractType, TypeRepr.AbstractType> myTypeMap = new HashMap<TypeRepr.AbstractType, TypeRepr.AbstractType>();
  private final Map<UsageRepr.Usage, UsageRepr.Usage> myUsageMap = new HashMap<UsageRepr.Usage, UsageRepr.Usage>();
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
      throw new RuntimeException(e);
    }
  }

  public int get(final String s) {
    try {
      return StringUtil.isEmpty(s) ? myEmptyName : myEnumerator.enumerate(s);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public int getFilePath(final String path) {
    try {
      if (StringUtil.isEmpty(path)) {
        return myEmptyName;
      }
      final String _path = FileUtil.toSystemIndependentName(path);
      return myEnumerator.enumerate(SystemInfo.isFileSystemCaseSensitive ? _path : _path.toLowerCase(Locale.US));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void close() {
    try {
      myEnumerator.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void flush() {
    myEnumerator.force();
  }

  public Logger<Integer> getLogger(final com.intellij.openapi.diagnostic.Logger log) {
    return new Logger<Integer>() {
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
