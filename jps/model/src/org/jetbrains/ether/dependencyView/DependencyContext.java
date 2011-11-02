package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 14.02.11
 * Time: 2:03
 * To change this template use File | Settings | File Templates.
 */
class DependencyContext {
  private final static String stringTableName = "strings.tab";
  private final PersistentStringEnumerator enumerator;
  private final Map<TypeRepr.AbstractType, TypeRepr.AbstractType> typeMap = new HashMap<TypeRepr.AbstractType, TypeRepr.AbstractType>();

  TypeRepr.AbstractType getType(final TypeRepr.AbstractType t) {
    final TypeRepr.AbstractType r = typeMap.get(t);

    if (r != null) {
      return r;
    }

    typeMap.put(t, t);

    return t;
  }

  DependencyContext(final File rootDir) {
    final File file = new File(FileUtil.toSystemIndependentName(rootDir.getAbsoluteFile() + File.separator + stringTableName));

    try {
      FileUtil.createIfDoesntExist(file);

      enumerator = new PersistentStringEnumerator(file, true);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class S implements Comparable<S>, RW.Writable {
    public final int index;

    private S(final int i) {
      index = i;
    }
    
    public S(final BufferedReader r) {
      index = RW.readInt(r);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      S s = (S)o;

      if (index != s.index) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return index;
    }

    public int compareTo(S o) {
      return index - o.index;
    }

    public void write(BufferedWriter w) {
      RW.writeln(w, Integer.toString(index));
    }

    public static final RW.Reader<S> reader = new RW.Reader<S>() {
      public S read(final BufferedReader r) {
        return new S(RW.readInt(r));
      }
    };

    @Override
    public String toString() {
      return Integer.toString(index);
    }
  }

  public String getValue(final S s) {
    try {
      return enumerator.valueOf(s.index);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public S get(final String s) {
    try {
      final int i = enumerator.enumerate(s);

      return new S(i);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  public RW.Reader<S> reader = new RW.Reader<S>() {
    public S read(final BufferedReader r) {
      return new S(RW.readInt(r));
    }
  };

  public void close() {
    try {
      enumerator.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
