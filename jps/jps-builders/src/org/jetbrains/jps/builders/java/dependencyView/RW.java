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
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;
import java.util.Collection;

/**
 * @author: db
 */
public class RW {
  private RW() {

  }

  protected static String readUTF(DataInput in) throws IOException {
    return IOUtil.readUTF(in);
  }

  protected static void writeUTF(DataOutput out, String value) throws IOException {
    IOUtil.writeUTF(out, value);
  }

  public interface Savable {
    void save(DataOutput out);
  }

  public static <X extends Savable> void save(final X[] x, final DataOutput out) {
    try {
      DataInputOutputUtil.writeINT(out, x.length);
      for (Savable s : x) {
        s.save(out);
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  public static <X> void save(final TIntHashSet x, final DataOutput out) {
    try {
      DataInputOutputUtil.writeINT(out, x.size());
      x.forEach(value -> {
        try {
          DataInputOutputUtil.writeINT(out, value);
          return true;
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
      });
    }
    catch (IOException c) {
      throw new BuildDataCorruptedException(c);
    }
  }

  public static <X> void save(final Collection<X> x, final DataExternalizer<X> e, final DataOutput out) {
    try {
      DataInputOutputUtil.writeINT(out, x.size());

      for (X y : x) {
        e.save(out, y);
      }
    }
    catch (IOException c) {
      throw new BuildDataCorruptedException(c);
    }
  }

  public static <X extends Savable> void save(final Collection<X> x, final DataOutput out) {
    try {
      final int size = x.size();

      DataInputOutputUtil.writeINT(out, size);

      for (X s : x) {
        s.save(out);
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
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
      throw new BuildDataCorruptedException(x);
    }
  }

  public static TIntHashSet read(final TIntHashSet acc, final DataInput in) {
    try {
      final int size = DataInputOutputUtil.readINT(in);

      for (int i = 0; i<size; i++) {
        acc.add(DataInputOutputUtil.readINT(in));
      }

      return acc;
    }
    catch (IOException x) {
      throw new BuildDataCorruptedException(x);
    }
  }

  public static <X,C extends Collection<X>> C read(final DataExternalizer<X> e, final C acc, final DataInput in) {
    try {
      final int size = DataInputOutputUtil.readINT(in);

      for (int i = 0; i<size; i++) {
        acc.add(e.read(in));
      }

      return acc;
    }
    catch (IOException x) {
      throw new BuildDataCorruptedException(x);
    }
  }

  public interface Writable {
    void write(BufferedWriter w);
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
      throw new BuildDataCorruptedException(e);
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

  public static String readString(final BufferedReader r) {
    try {
      return r.readLine();
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
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
}
