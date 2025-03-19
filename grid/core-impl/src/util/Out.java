package com.intellij.database.util;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public interface Out {

  OutputStream toOutputStream();

  Appendable toAppendable();

  @NotNull Out append(byte @NotNull[] bytes);

  @NotNull Out appendText(@NotNull CharSequence seq);

  long sizeInBytes();
  long length();

  default void close() throws IOException { }

  abstract class BaseOut implements Out {
    private final OutputStream meAsOutputStream = new OutputStream() {
      @Override
      public void write(@NotNull byte[] b) {
        append(b);
      }

      @Override
      public void write(@NotNull byte[] b, int off, int len) {
        append(Arrays.copyOfRange(b, off, off + len));
      }

      @Override
      public void write(int b) {
        append(new byte[]{(byte)b});
      }
    };
    private final AppendableWithRead meAsAppendable = new AppendableWithRead() {
      @Override
      public Appendable append(CharSequence csq) {
        appendText(csq);
        return this;
      }

      @Override
      public Appendable append(CharSequence csq, int start, int end) {
        return append(csq.subSequence(start, end));
      }

      @Override
      public Appendable append(char c) {
        return append(String.valueOf(c));
      }

      @Override
      public @NotNull String getString() {
        return BaseOut.this.getString();
      }
    };

    private boolean writtenRawBytes = false;
    private long length = 0;

    @Override
    public OutputStream toOutputStream() {
      return meAsOutputStream;
    }

    @Override
    public Appendable toAppendable() {
      return meAsAppendable;
    }

    protected Out append(byte @NotNull [] bytes, boolean rawBytes) {
      writtenRawBytes |= rawBytes;
      return appendImpl(bytes);
    }

    protected Out appendImpl(byte @NotNull [] bytes) {
      return this;
    }

    @Override
    public @NotNull Out append(byte @NotNull [] bytes) {
      return append(bytes, true);
    }

    @Override
    public @NotNull Out appendText(@NotNull CharSequence seq) {
      length += seq.length();
      return append(seq.toString().getBytes(StandardCharsets.UTF_8), false);
    }

    @Override
    public long length() {
      if (writtenRawBytes)
        throw new AssertionError("Raw bytes has already been written in this Out. Impossible to calculate length");
      return length;
    }

    public @NotNull String getString() {
      return toString();
    }
  }

  final class Readable extends BaseOut {
    private final ByteArrayOutputStream stream;

    public Readable() {
      stream = new ByteArrayOutputStream();
    }

    public Readable(int capacity) {
      stream = new ByteArrayOutputStream(capacity);
    }

    @Override
    protected Out appendImpl(byte @NotNull [] bytes) {
      try {
        stream.write(bytes);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public long sizeInBytes() {
      return stream.size();
    }

    @Override
    public @NotNull String getString() {
      return stream.toString(StandardCharsets.UTF_8);
    }

    public byte[] toBytes() {
      return stream.toByteArray();
    }
  }

  abstract class Wrapper extends BaseOut {

    private final Out myDelegate;

    public Wrapper(Out delegate) {
      myDelegate = delegate;
    }

    public Out getDelegate() {
      return myDelegate;
    }

    @Override
    public @NotNull Out append(byte @NotNull [] bytes) {
      return myDelegate.append(bytes);
    }

    @Override
    public @NotNull Out appendText(@NotNull CharSequence seq) {
      return myDelegate.appendText(seq);
    }

    @Override
    public long sizeInBytes() {
      return myDelegate.sizeInBytes();
    }

    @Override
    public long length() {
      return myDelegate.length();
    }

    @Override
    public void close() throws IOException {
      myDelegate.close();
    }
  }

  final class FromStream extends BaseOut {

    private final @NotNull OutputStream myStream;
    private long size;

    public FromStream(@NotNull OutputStream stream) {
      myStream = stream;
    }

    @Override
    protected Out appendImpl(byte @NotNull [] bytes) {
      try {
        myStream.write(bytes);
        size += bytes.length;
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @Override
    public long sizeInBytes() {
      return size;
    }

    @Override
    public void close() throws IOException {
      myStream.close();
    }
  }

}
