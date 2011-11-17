package org.jetbrains.ether.dependencyView;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.InlineKeyDescriptor;
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
  private final PersistentStringEnumerator myEnumerator;

  private final Map<TypeRepr.AbstractType, TypeRepr.AbstractType> myTypeMap = new HashMap<TypeRepr.AbstractType, TypeRepr.AbstractType>();
  private final Map<UsageRepr.Usage, UsageRepr.Usage> myUsageMap = new HashMap<UsageRepr.Usage, UsageRepr.Usage>();

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
    final File file = getTableFile(rootDir, stringTableName);

    myEnumerator = new PersistentStringEnumerator(file, true);
  }

  static KeyDescriptor<S> descriptorS = new InlineKeyDescriptor<S>() {
    @Override
    public S fromInt(int n) {
      return new S(n);
    }

    @Override
    public int toInt(S s) {
      return s.index;
    }
  };

  static class S implements Comparable<S>, RW.Writable {
    public final int index;

    S(final DataInput in){
      try{
      index = in.readInt();
      }
      catch (IOException e){
        throw new RuntimeException(e);
      }
    }
          
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

    public void save(final DataOutput out){
      try{
        out.writeInt(index);
      }
      catch (IOException e){
        throw new RuntimeException(e);
      }
    }
    
    @Override
    public String toString() {
      return Integer.toString(index);
    }
  }

  public String getValue(final S s) {
    try {
      return myEnumerator.valueOf(s.index);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public S get(final String s) {
    try {
      final int i = s == null ? myEnumerator.enumerate("") : myEnumerator.enumerate(s);

      return new S(i);
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
}
