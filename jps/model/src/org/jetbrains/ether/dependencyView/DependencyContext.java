package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.ether.RW;

import java.io.*;
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
  private final Map<UsageRepr.Usage, UsageRepr.Usage> usageMap = new HashMap<UsageRepr.Usage, UsageRepr.Usage>();

   UsageRepr.Usage getUsage(final UsageRepr.Usage u) {
     final UsageRepr.Usage r = usageMap.get(u);

     if (r == null) {
       usageMap.put(u, u);
       return u;
     }

     return r;
   }

  TypeRepr.AbstractType getType(final TypeRepr.AbstractType t) {
    final TypeRepr.AbstractType r = typeMap.get(t);

    if (r != null) {
      return r;
    }

    typeMap.put(t, t);

    return t;
  }

  static File getTableFile (final File rootDir, final String name) {
    final File file = new File(FileUtil.toSystemIndependentName(rootDir.getAbsoluteFile() + File.separator + name));
    FileUtil.createIfDoesntExist(file);
    return file;
  }

  DependencyContext(final File rootDir) throws IOException {
    final File file = getTableFile(rootDir, stringTableName);

    enumerator = new PersistentStringEnumerator(file, true);
  }

  static KeyDescriptor<S> descriptorS = new KeyDescriptor<S>() {
    @Override
    public void save(final DataOutput out, final S value) throws IOException {
      out.writeInt(value.index);
    }

    @Override
    public S read(final DataInput in) throws IOException {
      return new S(in.readInt());
    }

    @Override
    public int getHashCode(final S value) {
      return value.index;
    }

    @Override
    public boolean isEqual(final S val1, final S val2) {
      return val1.index == val2.index;
    }
  };

  static class S implements Comparable<S>, RW.Writable {
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
