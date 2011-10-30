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
  
  private final static Map<String, Integer> map = new HashMap<String, Integer>();
  private final static Map<Integer, String> imap = new HashMap<Integer, String>();

  private static int index = 0;

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

  public class S implements Comparable<S>, RW.Writable, KeyDescriptor<S> {
    public final int index;

    @Override
    public void save(final DataOutput out, final S value) throws IOException {
      out.writeUTF(value.getValue());
    }

    @Override
    public S read(final DataInput in) throws IOException {
      return get(in.readUTF());
    }

    @Override
    public int getHashCode(final S value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(final S val1, final S val2) {
      return val1.equals(val2);
    }

    private S(final int i) {
      index = i;
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
      RW.writeln(w, getValue());
    }

    public final RW.Reader<S> reader = new RW.Reader<S>() {
      public S read(final BufferedReader r) {
        final String s = RW.readString(r);
        return get(s);
      }
    };

    public String toString() {
      return getValue();
    }

    public String getValue() {
      return imap.get(index);
      
      /*
      try {
        return enumerator.valueOf(index);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      */
    }
  }

  public S get(final String s) {
    final Integer i = map.get(s);
    
    if (i == null) {
      map.put(s, index++);
      imap.put(index-1, s);
      
      return new S(index-1);
    }
    
    return new S(i);

    /*
   try {
      final int i = enumerator.enumerate(s);

      return new S(i);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    */
  }

  public RW.Reader<S> reader = new RW.Reader<S>() {
    public S read(final BufferedReader r) {
      try {
        return get(r.readLine());
      }
      catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }
  };
}
