/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * <p><code>SmartEncodingInputStream</code> extends an <code>InputStream</code> with a special constructor
 * and a special method for dealing with text files encoded within different charsets.</p>
 *
 * <p>It surrounds a normal <code>InputStream</code> whatever it may be (<code>FileInputStream</code>...).
 * It reads a buffer of a defined length. Then with this byte buffer,
 * it uses the class <code>CharsetToolkit</code> to parse this buffer and guess what the encoding is.
 * All this steps are done within the constructor.
 * At this time, you can call the method <code>getReader()</code> to retrieve a <code>Reader</code> created
 * with the good charset, as guessed while parsing the first bytes of the file. This <code>Reader</code>
 * reads inside the <code>SmartEncodingInputStream</code>. It reads first in the internal buffer, then
 * when we reach the end of the buffer, the underlying InputStream is read with the default read method.</p>
 *
 * <p>Usage:</p>
 *
 * <pre>
 * FileInputStream fis = new FileInputStream("utf-8.txt");
 * SmartEncodingInputStream smartIS = new SmartEncodingInputStream(fis);
 * Reader reader = smartIS.getReader();
 * BufferedReader bufReader = new BufferedReader(reader);
 *
 * String line;
 * while ((line = bufReader.readLine()) != null)
 * {
 *     System.out.println(line);
 * }
 * </pre>
 *
 * Date: 23 juil. 2002
 *
 * @author Guillaume Laforge
 */
public class SmartEncodingInputStream extends InputStream {
  private InputStream is;
  private final int bufferLength;
  private byte[] buffer;
  private int counter;
  private Charset charset;

  public static final int BUFFER_LENGTH_2KB = 2048;
  public static final int BUFFER_LENGTH_4KB = 4096;
  public static final int BUFFER_LENGTH_8KB = 8192;

  public SmartEncodingInputStream(InputStream is, byte[] buffer, Charset defaultCharset, boolean enforce8Bit) throws IOException {
    this.is = is;
    this.buffer = buffer;
    this.counter = 0;

    this.bufferLength = is != null ? is.read(buffer) : buffer.length;
    CharsetToolkit charsetToolkit = new CharsetToolkit(buffer, defaultCharset);
    charsetToolkit.setEnforce8Bit(enforce8Bit);
    this.charset = charsetToolkit.guessEncoding(bufferLength);
  }

  /**
   * <p>Constructor of the <code>SmartEncodingInputStream</code> class.
   * The wider the buffer is, the most sure you are to have guessed the encoding of the
   * <code>InputStream</code> you wished to get a <code>Reader</code> from.</p>
   *
   * <p>It is possible to defined</p>
   *
   * @param is the <code>InputStream</code> of which we want to create a <code>Reader</code>
   * with the encoding guessed from the first buffer of the file.
   * @param bufferLength the length of the buffer that is used to guess the encoding.
   * @param defaultCharset specifies the default <code>Charset</code> to use
   * when an 8-bit <code>Charset</code> is guessed. This parameter may be null, in this case
   * the default system charset is used as definied in the system property "file.encoding"
   * read by the method <code>getDefaultSystemCharset()</code> from the class <code>CharsetToolkit</code>.
   * @param enforce8Bit enforce the use of the specified default <code>Charset</code> in case
   * the encoding US-ASCII is recognized.
   *
   * @throws java.io.IOException
   */
  public SmartEncodingInputStream(InputStream is, int bufferLength, Charset defaultCharset, boolean enforce8Bit) throws IOException {
    this( is, new byte[bufferLength], defaultCharset, enforce8Bit );
  }

  /**
   * Constructor of the <code>SmartEncodingInputStream</code>.
   * With this constructor, the default <code>Charset</code> used when an 8-bit encoding is guessed
   * does not need to be specified. The default system charset will be used instead.
   *
   * @param is is the <code>InputStream</code> of which we want to create a <code>Reader</code>
   * with the encoding guessed from the first buffer of the file.
   * @param bufferLength the length of the buffer that is used to guess the encoding.
   * @param defaultCharset specifies the default <code>Charset</code> to use
   * when an 8-bit <code>Charset</code> is guessed. This parameter may be null, in this case
   * the default system charset is used as definied in the system property "file.encoding"
   * read by the method <code>getDefaultSystemCharset()</code> from the class <code>CharsetToolkit</code>.
   *
   * @throws java.io.IOException
   */
  public SmartEncodingInputStream(InputStream is, int bufferLength, Charset defaultCharset) throws IOException {
    this(is, bufferLength, defaultCharset, true);
  }

  /**
   * Constructor of the <code>SmartEncodingInputStream</code>.
   * With this constructor, the default <code>Charset</code> used when an 8-bit encoding is guessed
   * does not need to be specified. The default system charset will be used instead.
   *
   * @param is is the <code>InputStream</code> of which we want to create a <code>Reader</code>
   * with the encoding guessed from the first buffer of the file.
   * @param bufferLength the length of the buffer that is used to guess the encoding.
   *
   * @throws java.io.IOException
   */
  public SmartEncodingInputStream(InputStream is, int bufferLength) throws IOException {
    this(is, bufferLength, null, true);
  }

  /**
   * Constructor of the <code>SmartEncodingInputStream</code>.
   * With this constructor, the default <code>Charset</code> used when an 8-bit encoding is guessed
   * does not need to be specified. The default system charset will be used instead.
   * The buffer length does not need to be specified either. A default buffer length of 4 KB is used.
   *
   * @param is is the <code>InputStream</code> of which we want to create a <code>Reader</code>
   * with the encoding guessed from the first buffer of the file.
   *
   * @throws java.io.IOException
   */
  public SmartEncodingInputStream(InputStream is) throws IOException {
    this(is, SmartEncodingInputStream.BUFFER_LENGTH_4KB, null, true);
  }

  /**
   * Implements the method <code>read()</code> as defined in the <code>InputStream</code> interface.
   * As a certain number of bytes has already been read from the underlying <code>InputStream</code>,
   * we first read the bytes of this buffer, otherwise, we directly read the rest of the stream from
   * the underlying <code>InputStream</code>.
   *
   * @return the total number of bytes read into the buffer, or <code>-1</code> is there is no more data
   * because the end of the stream has been reached.
   * @throws java.io.IOException
   */
  public int read() throws IOException {
    if (counter < bufferLength) {
      byte b = buffer[counter++];
      return 0xFF & ((int)b);
    }
    else {
      return is != null ? is.read() : -1;
    }
  }

  public int read(byte b[], int off, int len) throws IOException {
    int read = 0;

    if (counter < bufferLength) {
      read = Math.min(bufferLength - counter, len);
      System.arraycopy(buffer, counter, b, off, read);
      off += read;
      len -= read;
      counter += read;
    }

    if (len > 0) {
      int r = is != null ? is.read(b, off, len) : -1;
      if (read == 0 && r < 0) return -1;
      if (r >= 0) read += r;
    }

    return read;
  }

  /**
   * Gets a <code>Reader</code> with the right <code>Charset</code> as guessed by reading the beginning
   * of the underlying <code>InputStream</code>.
   *
   * @return a <code>Reader</code> defined with the right encoding.
   */
  public Reader getReader() {
    if (this.charset == null) return new InputStreamReader(this);
    return new InputStreamReader(this, this.charset);
  }

  /**
   * Retrieves the <code>Charset</code> as guessed from the underlying <code>InputStream</code>.
   *
   * @return the <code>Charset</code> guessed.
   */
  public Charset getEncoding() {
    return this.charset;
  }

  public void close() throws IOException {
    if( is != null ) this.is.close();
    buffer = null;
  }
}
