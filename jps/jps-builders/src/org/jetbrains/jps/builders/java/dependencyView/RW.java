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

import com.intellij.util.io.DataExternalizer;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntProcedure;

import java.io.*;
import java.util.Collection;

/**
 * @author: db
 * Date: 29.01.11
 */
public class RW {
  private RW() {

  }

  public interface Savable {
    void save(DataOutput out);
  }

  public static <X extends Savable> void save(final X[] x, final DataOutput out) {
    try {
      out.writeInt(x.length);
      for (Savable s : x) {
        s.save(out);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <X> void save(final TIntHashSet x, final DataOutput out) {
    try {
      out.writeInt(x.size());
      x.forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          try {
            out.writeInt(value);
            return true;
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
    catch (IOException c) {
      throw new RuntimeException(c);
    }
  }

  public static <X> void save(final Collection<X> x, final DataExternalizer<X> e, final DataOutput out) {
    try {
      out.writeInt(x.size());

      for (X y : x) {
        e.save(out, y);
      }
    }
    catch (IOException c) {
      throw new RuntimeException(c);
    }
  }

  public static <X extends Savable> void save(final Collection<X> x, final DataOutput out) {
    try {
      final int size = x.size();

      out.writeInt(size);

      for (X s : x) {
        s.save(out);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <X> X[] read(final DataExternalizer<X> e, final DataInput in, final X[] result) {
    try {
      for (int i = 0; i < result.length; i++) {
        result[i] = e.read(in);
      }

      return result;
    }
    catch (IOException x) {
      throw new RuntimeException(x);
    }
  }

  public static TIntHashSet read(final TIntHashSet acc, final DataInput in) {
    try {
      final int size = in.readInt();

      for (int i = 0; i<size; i++) {
        acc.add(in.readInt());
      }

      return acc;
    }
    catch (IOException x) {
      throw new RuntimeException(x);
    }
  }

  public static <X> Collection<X> read(final DataExternalizer<X> e, final Collection<X> acc, final DataInput in) {
    try {
      final int size = in.readInt();

      for (int i = 0; i<size; i++) {
        acc.add(e.read(in));
      }

      return acc;
    }
    catch (IOException x) {
      throw new RuntimeException(x);
    }
  }

  public interface Writable {
    void write(BufferedWriter w);
  }

  public static <T extends Comparable> void writeln(final BufferedWriter w, final Collection<T> c, final ToWritable<T> t) {
    if (c == null) {
      writeln(w, "0");
      return;
    }

    writeln(w, Integer.toString(c.size()));

    for (T e : c) {
      t.convert(e).write(w);
    }
  }

  public static void writeln(final BufferedWriter w, final Collection<? extends Writable> c) {
    if (c == null) {
      writeln(w, "0");
      return;
    }

    writeln(w, Integer.toString(c.size()));

    for (Writable e : c) {
      e.write(w);
    }
  }

  public interface ToWritable<T> {
    Writable convert(T x);
  }

  public static void writeln(final BufferedWriter w, final String s) {
    try {
      if (s == null) {
        w.write("");
      }
      else {
        w.write(s);
      }
      w.newLine();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public interface Reader<T> {
    T read(BufferedReader r);
  }

  public static ToWritable<String> fromString = new ToWritable<String>() {
    public Writable convert(final String s) {
      return new Writable() {
        public void write(BufferedWriter w) {
          writeln(w, s);
        }
      };
    }
  };

  public static Reader<String> myStringReader = new Reader<String>() {
    public String read(final BufferedReader r) {
      try {
        return r.readLine();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public static <T> Collection<T> readMany(final BufferedReader r, final Reader<T> c, final Collection<T> acc) {
    final int size = readInt(r);

    for (int i = 0; i < size; i++) {
      acc.add(c.read(r));
    }

    return acc;
  }

  public static String lookString(final BufferedReader r) {
    try {
      r.mark(256);
      final String s = r.readLine();
      r.reset();

      return s;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void readTag(final BufferedReader r, final String tag) {
    try {
      final String s = r.readLine();

      if (!s.equals(tag)) System.err.println("Parsing error: expected \"" + tag + "\", but found \"" + s + "\"");
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static String readString(final BufferedReader r) {
    try {
      return r.readLine();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static long readLong(final BufferedReader r) {
    final String s = readString(r);

    try {
      return Long.parseLong(s);
    }
    catch (Exception n) {
      System.err.println("Parsing error: expected long, but found \"" + s + "\"");
      return 0;
    }
  }

  public static int readInt(final BufferedReader r) {
    final String s = readString(r);

    try {
      return Integer.parseInt(s);
    }
    catch (Exception n) {
      System.err.println("Parsing error: expected integer, but found \"" + s + "\"");
      return 0;
    }
  }

  public static String readStringAttribute(final BufferedReader r, final String tag) {
    try {
      final String s = r.readLine();

      if (s.startsWith(tag)) return s.substring(tag.length());

      System.err.println("Parsing error: expected \"" + tag + "\", but found \"" + s + "\"");

      return null;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
