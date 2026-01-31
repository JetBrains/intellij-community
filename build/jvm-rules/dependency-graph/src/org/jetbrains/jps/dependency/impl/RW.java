// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.Iterators;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RW {
  private static final int STRING_HEADER_SIZE = 1;
  private static final int STRING_LENGTH_THRESHOLD = 255;
  private static final String LONGER_THAN_64K_MARKER = "LONGER_THAN_64K";
  private static final ThreadLocal<SoftReference<byte[]>> ourBuffersCache = new ThreadLocal<>();

  private RW() {
  }

  public interface Writer<T> {
    void write(T obj) throws IOException;
  }
  
  public interface Reader<T> {
    T read() throws IOException;
  }

  public static <T> void writeCollection(DataOutput out, Iterable<? extends T> seq, Writer<? super T> writable) throws IOException {
    Collection<? extends T> col = seq instanceof Collection? (Collection<? extends T>)seq : Iterators.collect(seq, new ArrayList<>());
    out.writeInt(col.size());
    for (T t : col) {
      writable.write(t);
    }
  }

  public static <T> List<T> readCollection(DataInput in, Reader<? extends T> reader) throws IOException {
    return readCollection(in, reader, new ArrayList<>());
  }

  public static <T, C extends Collection<? super T>> C readCollection(DataInput in, Reader<? extends T> reader, C acc) throws IOException {
    int size = in.readInt();
    while (size-- > 0) {
      acc.add(reader.read());
    }
    return acc;
  }

  public static void writeUTF(@NotNull DataOutput storage, @NotNull CharSequence value) throws IOException {
    writeUTFFast(getBuffer(), storage, value);
  }

  public static String readUTF(@NotNull DataInput storage) throws IOException {
    return readUTFFast(getBuffer(), storage);
  }

  private static void writeUTFFast(byte @NotNull [] buffer, @NotNull DataOutput storage, @NotNull CharSequence value) throws IOException {
    int len = value.length();
    if (len < STRING_LENGTH_THRESHOLD) {
      buffer[0] = (byte)len;
      boolean isAscii = true;
      for (int i = 0; i < len; i++) {
        char c = value.charAt(i);
        if (c >= 128) {
          isAscii = false;
          break;
        }
        buffer[i + STRING_HEADER_SIZE] = (byte)c;
      }
      if (isAscii) {
        storage.write(buffer, 0, len + STRING_HEADER_SIZE);
        return;
      }
    }
    storage.writeByte((byte)0xFF);

    try {
      storage.writeUTF(value.toString());
    }
    catch (UTFDataFormatException e) {
      storage.writeUTF(LONGER_THAN_64K_MARKER);
      writeCharSequence(value, storage);
    }
  }

  private static void writeCharSequence(@Nullable CharSequence s, @NotNull DataOutput stream) throws IOException {
    if (s == null) {
      stream.writeInt(-1);
      return;
    }

    stream.writeInt(s.length());
    if (s.length() == 0) {
      return;
    }

    byte[] bytes = new byte[s.length() * 2];

    for (int i = 0, i2 = 0; i < s.length(); i++, i2 += 2) {
      char aChar = s.charAt(i);
      bytes[i2] = (byte)(aChar >>> 8 & 0xFF);
      bytes[i2 + 1] = (byte)(aChar & 0xFF);
    }

    stream.write(bytes);
  }

  private static String readUTFFast(byte @NotNull [] buffer, @NotNull DataInput storage) throws IOException {
    int len = 0xFF & (int)storage.readByte();
    if (len == 0xFF) {
      // read long string
      String result = storage.readUTF();
      if (LONGER_THAN_64K_MARKER.equals(result)) {
        return readString(storage);
      }
      return result;
    }

    if (len == 0) return "";
    storage.readFully(buffer, 0, len);
    return new String(buffer, 0, len, StandardCharsets.ISO_8859_1);
  }

  private static String readString(@NotNull DataInput stream) throws IOException {
    try {
      int length = stream.readInt();
      if (length == -1) return null;
      if (length == 0) return "";

      byte[] bytes = new byte[length * 2];
      stream.readFully(bytes);
      return new String(bytes, 0, length * 2, StandardCharsets.UTF_16BE);
    }
    catch (IOException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new IOException(e);
    }
  }

  private static byte[] getBuffer() {
    SoftReference<byte[]> ref = ourBuffersCache.get();
    byte[] buf = ref != null? ref.get() : null;
    if (buf == null) {
      buf = new byte[STRING_LENGTH_THRESHOLD + STRING_HEADER_SIZE];
      ourBuffersCache.set(new SoftReference<>(buf));
    }
    return buf;
  }
}
