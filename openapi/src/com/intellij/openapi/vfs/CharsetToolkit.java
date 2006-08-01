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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Collection;

/**
 * <p>Utility class to guess the encoding of a given byte array.
 * The guess is unfortunately not 100% sure. Especially for 8-bit charsets.
 * It's not possible to know which 8-bit charset is used.
 * We will then infer that the charset encountered is the same as the default standard charset.</p>
 *
 * <p>On the other hand, unicode files encoded in UTF-16 (low or big endian) or UTF-8 files
 * with a Byte Order Marker are easy to find. For UTF-8 files with no BOM, if the buffer
 * is wide enough, it's easy to guess.</p>
 *
 * <p>Tested against a complicated UTF-8 file, Sun's implementation does not render bad UTF-8
 * constructs as expected by the specification. But with a buffer wide enough, the method guessEncoding()
 * did behave correctly and recognized the UTF-8 charset.</p>
 *
 * <p>A byte buffer of 4KB or 8KB is sufficient to be able to guessEncoding the encoding.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * // guess the encoding
 * Charset guessedCharset = CharsetToolkit.guessEncoding(file, 4096);
 *
 * // create a reader with the charset we've just discovered
 * FileInputStream fis = new FileInputStream(file);
 * InputStreamReader isr = new InputStreamReader(fis, guessedCharset);
 * BufferedReader br = new BufferedReader(isr);
 *
 * // read the file content
 * String line;
 * while ((line = br.readLine())!= null)
 * {
 *     System.out.println(line);
 * }
 * </pre>
 * <p>An interesting improvement would be to create a custom <code>InputStream</code> that has a
 * method discovering the <code>Charset</code> of the underlying file. Thus, we would not have to
 * read the beginning of the file twice: once for guessing the encoding, the second time for reading
 * its content. Therefore, we could englobe this stream within an <code>InputStreamReader</code>.</p>
 *
 * <p>Date: 18 juil. 2002</p>
 * @author Guillaume LAFORGE
 */
public class CharsetToolkit {
  public static final @NonNls String UTF8 = "UTF-8";

  private byte[] buffer;
  private Charset defaultCharset;
  private boolean enforce8Bit = false;

  public static final byte[] UTF8_BOM = new byte[]{-17, -69, -65, };
  public static final byte[] UTF16LE_BOM = new byte[]{-1, -2, };
  public static final byte[] UTF16BE_BOM = new byte[]{-2, -1, };
  @NonNls public static final String FILE_ENCODING_PROPERTY = "file.encoding";

  /**
   * Constructor of the <code>CharsetToolkit</code> utility class.
   *
   * @param buffer the byte buffer of which we want to know the encoding.
   */
  public CharsetToolkit(byte[] buffer) {
    this.buffer = buffer;
    defaultCharset = getDefaultSystemCharset();
  }

  /**
   * Constructor of the <code>CharsetToolkit</code> utility class.
   *
   * @param buffer the byte buffer of which we want to know the encoding.
   * @param defaultCharset the default Charset to use in case an 8-bit charset is recognized.
   */
  public CharsetToolkit(byte[] buffer, Charset defaultCharset) {
    this.buffer = buffer;
    setDefaultCharset(defaultCharset);
  }

  /**
   * Defines the default <code>Charset</code> used in case the buffer represents
   * an 8-bit <code>Charset</code>.
   *
   * @param defaultCharset the default <code>Charset</code> to be returned by <code>guessEncoding()</code>
   * if an 8-bit <code>Charset</code> is encountered.
   */
  public void setDefaultCharset(Charset defaultCharset) {
    if (defaultCharset != null)
      this.defaultCharset = defaultCharset;
    else
      this.defaultCharset = getDefaultSystemCharset();
  }

  /**
   * If US-ASCII is recognized, enforce to return the default encoding, rather than US-ASCII.
   * It might be a file without any special character in the range 128-255, but that may be or become
   * a file encoded with the default <code>charset</code> rather than US-ASCII.
   *
   * @param enforce a boolean specifying the use or not of US-ASCII.
   */
  public void setEnforce8Bit(boolean enforce) {
    enforce8Bit = enforce;
  }

  /**
   * Gets the enforce8Bit flag, in case we do not want to ever get a US-ASCII encoding.
   *
   * @return a boolean representing the flag of use of US-ASCII.
   */
  public boolean getEnforce8Bit() {
    return enforce8Bit;
  }

  /**
   * Retrieves the default Charset
   * @return
   */
  public Charset getDefaultCharset() {
    return defaultCharset;
  }

  /**
   * <p>Guess the encoding of the provided buffer.</p>
   * If Byte Order Markers are encountered at the beginning of the buffer, we immidiately
   * return the charset implied by this BOM. Otherwise, the file would not be a human
   * readable text file.</p>
   *
   * <p>If there is no BOM, this method tries to discern whether the file is UTF-8 or not.
   * If it is not UTF-8, we assume the encoding is the default system encoding
   * (of course, it might be any 8-bit charset, but usually, an 8-bit charset is the default one).</p>
   *
   * <p>It is possible to discern UTF-8 thanks to the pattern of characters with a multi-byte sequence.</p>
   * <pre>
   * UCS-4 range (hex.)        UTF-8 octet sequence (binary)
   * 0000 0000-0000 007F       0xxxxxxx
   * 0000 0080-0000 07FF       110xxxxx 10xxxxxx
   * 0000 0800-0000 FFFF       1110xxxx 10xxxxxx 10xxxxxx
   * 0001 0000-001F FFFF       11110xxx 10xxxxxx 10xxxxxx 10xxxxxx
   * 0020 0000-03FF FFFF       111110xx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
   * 0400 0000-7FFF FFFF       1111110x 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx 10xxxxxx
   * </pre>
   * <p>With UTF-8, 0xFE and 0xFF never appear.</p>
   *
   * @return the Charset recognized.
   */
  @SuppressWarnings({"HardCodedStringLiteral"})
  public Charset guessEncoding( int guess_length ) {
    // if the file has a Byte Order Marker, we can assume the file is in UTF-xx
    // otherwise, the file would not be human readable
    if (hasUTF8Bom(buffer)) return Charset.forName("UTF-8");
    if (hasUTF16LEBom(buffer)) return Charset.forName("UTF-16LE");
    if (hasUTF16BEBom(buffer)) return Charset.forName("UTF-16BE");

    // if a byte has its most significant bit set, the file is in UTF-8 or in the default encoding
    // otherwise, the file is in US-ASCII
    boolean highOrderBit = false;

    // if the file is in UTF-8, high order bytes must have a certain value, in order to be valid
    // if it's not the case, we can assume the encoding is the default encoding of the system
    boolean validU8Char = true;

    // TODO the buffer is not read up to the end, but up to length - 6

    int length = Math.min( buffer.length, guess_length );
    int i = 0;
    while (i < length - 6) {
      byte b0 = buffer[i];
      byte b1 = buffer[i + 1];
      byte b2 = buffer[i + 2];
      byte b3 = buffer[i + 3];
      byte b4 = buffer[i + 4];
      byte b5 = buffer[i + 5];
      if (b0 < 0) {
        // a high order bit was encountered, thus the encoding is not US-ASCII
        // it may be either an 8-bit encoding or UTF-8
        highOrderBit = true;
        // a two-bytes sequence was encoutered
        if (isTwoBytesSequence(b0)) {
          // there must be one continuation byte of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (!isContinuationChar(b1))
            validU8Char = false;
          else
            i++;
        }
        // a three-bytes sequence was encoutered
        else if (isThreeBytesSequence(b0)) {
          // there must be two continuation bytes of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (!(isContinuationChar(b1) && isContinuationChar(b2)))
            validU8Char = false;
          else
            i += 2;
        }
        // a four-bytes sequence was encoutered
        else if (isFourBytesSequence(b0)) {
          // there must be three continuation bytes of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (!(isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3)))
            validU8Char = false;
          else
            i += 3;
        }
        // a five-bytes sequence was encoutered
        else if (isFiveBytesSequence(b0)) {
          // there must be four continuation bytes of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (!(isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3) && isContinuationChar(b4)))
            validU8Char = false;
          else
            i += 4;
        }
        // a six-bytes sequence was encoutered
        else if (isSixBytesSequence(b0)) {
          // there must be five continuation bytes of the form 10xxxxxx,
          // otherwise the following characteris is not a valid UTF-8 construct
          if (!(isContinuationChar(b1) && isContinuationChar(b2) && isContinuationChar(b3) && isContinuationChar(b4) && isContinuationChar(b5)))
            validU8Char = false;
          else
            i += 5;
        } else
          validU8Char = false;
      }
      if (!validU8Char) break;
      i++;
    }
    // if no byte with an high order bit set, the encoding is US-ASCII
    // (it might have been UTF-7, but this encoding is usually internally used only by mail systems)
    if (!highOrderBit) {
      // returns the default charset rather than US-ASCII if the enforce8Bit flag is set.
      if (enforce8Bit)
        return defaultCharset;
      else
        return Charset.forName("US-ASCII");
    }
    // if no invalid UTF-8 were encountered, we can assume the encoding is UTF-8,
    // otherwise the file would not be human readable
    if (validU8Char) return Charset.forName("UTF-8");
    // finally, if it's not UTF-8 nor US-ASCII, let's assume the encoding is the default encoding
    return defaultCharset;
  }

  public static Charset guessEncoding(File f, int bufferLength) throws IOException {
    return guessEncoding(f, bufferLength, getIDEOptionsCharset());
  }

  public static Charset getIDEOptionsCharset() {
    // see SCR #5288
    Application application = ApplicationManager.getApplication();
    if (application == null) return null;
    CharsetSettings settings = CharsetSettings.getInstance();
    if (settings == null) return null;

    String charsetName = settings.getCharsetName();
    if (CharsetSettings.SYSTEM_DEFAULT_CHARSET_NAME.equals(charsetName)) return getDefaultSystemCharset();
    if (!Charset.isSupported(charsetName)) return getDefaultSystemCharset();
    Charset charset = Charset.forName(charsetName);
    return charset != null ? charset :  getDefaultSystemCharset();
  }

  public static Charset guessEncoding(File f, int bufferLength, Charset defaultCharset) throws IOException {
    FileInputStream fis = new FileInputStream(f);
    byte[] buffer = new byte[bufferLength];
    try {
      fis.read(buffer);
    }
    finally {
      fis.close();
    }
    CharsetToolkit toolkit = new CharsetToolkit(buffer);
    toolkit.setDefaultCharset(defaultCharset);
    return toolkit.guessEncoding( bufferLength );
  }

  /**
   * If the byte has the form 10xxxxx, then it's a continuation byte of a multiple byte character;
   *
   * @param b a byte.
   * @return true if it's a continuation char.
   */
  private static boolean isContinuationChar(byte b) {
    return -128 <= b && b <= -65;
  }

  /**
   * If the byte has the form 110xxxx, then it's the first byte of a two-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a two-bytes sequence.
   */
  private static boolean isTwoBytesSequence(byte b) {
    return -64 <= b && b <= -33;
  }

  /**
   * If the byte has the form 1110xxx, then it's the first byte of a three-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a three-bytes sequence.
   */
  private static boolean isThreeBytesSequence(byte b) {
    return -32 <= b && b <= -17;
  }

  /**
   * If the byte has the form 11110xx, then it's the first byte of a four-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a four-bytes sequence.
   */
  private static boolean isFourBytesSequence(byte b) {
    return -16 <= b && b <= -9;
  }

  /**
   * If the byte has the form 11110xx, then it's the first byte of a five-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a five-bytes sequence.
   */
  private static boolean isFiveBytesSequence(byte b) {
    return -8 <= b && b <= -5;
  }

  /**
   * If the byte has the form 1110xxx, then it's the first byte of a six-bytes sequence character.
   *
   * @param b a byte.
   * @return true if it's the first byte of a six-bytes sequence.
   */
  private static boolean isSixBytesSequence(byte b) {
    return -4 <= b && b <= -3;
  }

  /**
   * Retrieve the default charset of the system.
   *
   * @return the default <code>Charset</code>.
   */
  public static Charset getDefaultSystemCharset() {
    Charset charset = null;
    try {
      charset = Charset.forName(System.getProperty(FILE_ENCODING_PROPERTY));
    } catch (Exception e) {
      // Null is OK here.
    }

    return charset;
  }

  /**
   * Has a Byte Order Marker for UTF-8 (Used by Microsoft's Notepad and other editors).
   *
   * @param bom a buffer.
   * @return true if the buffer has a BOM for UTF8.
   */
  public static boolean hasUTF8Bom(byte[] bom) {
    return ArrayUtil.startsWith(bom, UTF8_BOM);
  }

  /**
   * Has a Byte Order Marker for UTF-16 Low Endian
   * (ucs-2le, ucs-4le, and ucs-16le).
   *
   * @param bom a buffer.
   * @return true if the buffer has a BOM for UTF-16 Low Endian.
   */
  public static boolean hasUTF16LEBom(byte[] bom) {
    return ArrayUtil.startsWith(bom, UTF16LE_BOM);
  }

  /**
   * Has a Byte Order Marker for UTF-16 Big Endian
   * (utf-16 and ucs-2).
   *
   * @param bom a buffer.
   * @return true if the buffer has a BOM for UTF-16 Big Endian.
   */
  public static boolean hasUTF16BEBom(byte[] bom) {
    return ArrayUtil.startsWith(bom, UTF16BE_BOM);
  }

  /**
   * Retrieves all the available <code>Charset</code>s on the platform,
   * among which the default <code>charset</code>.
   *
   * @return an array of <code>Charset</code>s.
   */
  public static Charset[] getAvailableCharsets() {
    Collection<Charset> collection = Charset.availableCharsets().values();
    return collection.toArray(new Charset[collection.size()]);
  }
}
