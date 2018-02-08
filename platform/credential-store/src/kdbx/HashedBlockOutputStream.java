/*
 * Copyright 2015 Jo Rabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.credentialStore.kdbx;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

/**
 * Takes a stream of data and formats as Hashed Blocks to the underlying output stream.
 * <p>
 * <p>A Hashed block consists of:
 * <p>
 * <ol>
 * <li>A 4 byte block sequence number, increments from 0
 * <li>A 32 byte MD5 hash of the content
 * <li>A 4 byte length field
 * <li>Content
 * </ol>
 * <p>
 * <p>The stream of blocks is terminated with a 0 length 0 hash block.
 * <p>
 * <p>Originally developed for KeePass. A KeePass hash block
 * stream is little endian, i.e. the sequence
 * number and length fields are low order byte first.
 *
 * @author Jo
 */
public class HashedBlockOutputStream extends OutputStream {
  private static final int BLOCK_SIZE = 8 * 1024;
  private static final int HASH_SIZE = 32;
  private static final byte[] ZERO_HASH = new byte[HASH_SIZE];

  private int nextSequenceNumber = 0;
  private final OutputStream outputStream;
  private final ByteArrayOutputStream blockOutputStream = new ByteArrayOutputStream();
  private boolean isClosed = false;

  private final MessageDigest md = KdbxHeaderKt.sha256MessageDigest();

  public HashedBlockOutputStream(OutputStream outputStream) {
    this.outputStream = outputStream;
  }

  @Override
  public void write(int i) throws IOException {
    byte[] buf = new byte[1];
    buf[0] = (byte)i;
    put(buf, 0, 1);
  }

  @Override
  public void write(@NotNull byte[] b, int offset, int count) throws IOException {
    put(b, offset, count);
  }


  @Override
  public void flush() throws IOException {
    save();
  }

  @Override
  public void close() throws IOException {
    if (isClosed) {
      throw new EOFException();
    }
    flush();
    writeInt(nextSequenceNumber);
    outputStream.write(ZERO_HASH);
    writeInt(0);
    isClosed = true;
    outputStream.flush();
    outputStream.close();
  }

  /**
   * Writes to the internal buffer, and writes to the underlying output stream
   * as necessary as {@link #BLOCK_SIZE} blocks
   *
   * @param b      the byte array to write
   * @param offset offset in the byte array
   * @param length number of bytes to write
   * @throws IOException
   */
  protected void put(byte[] b, int offset, int length) throws IOException {
    if (isClosed) {
      throw new EOFException();
    }
    while (length > 0) {
      int bytesToWrite = Math.min(BLOCK_SIZE - blockOutputStream.size(), length);
      blockOutputStream.write(b, offset, bytesToWrite);
      if (blockOutputStream.size() >= BLOCK_SIZE) {
        save();
      }
      offset += bytesToWrite;
      length -= bytesToWrite;
    }
  }

  /**
   * Save the internal buffer to the underlying stream as a hash block
   */
  protected void save() throws IOException {
    // if there's nothing to save don't do anything
    if (blockOutputStream.size() == 0) {
      return;
    }
    // write and increment the block sequence no
    writeInt(nextSequenceNumber++);

    // calculate the hash of the buffer
    byte[] buffer = blockOutputStream.toByteArray();
    md.update(buffer);
    outputStream.write(md.digest());

    // write the buffer's length
    writeInt(buffer.length);

    // write the buffer
    outputStream.write(buffer);

    // push the contents to disk etc.
    outputStream.flush();

    // reset the internal output buffer for reuse
    blockOutputStream.reset();
  }

  /**
   * Write a 4 byte int value to the underlying stream in appropriate endian format
   *
   * @param value the value to write
   * @throws IOException
   */
  protected void writeInt(int value) throws IOException {
    int output = Integer.reverseBytes(value);
    outputStream.write(new byte[]{(byte)(output >> 24), (byte)(output >> 16), (byte)(output >> 8), (byte)output});
  }
}
