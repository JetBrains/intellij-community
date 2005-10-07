/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package jetbrains.fabrique.util;

import java.io.*;

/**
 *
 * @author Oleg Melnik
 */
public class StreamUtil {
  /**
   * @param inputStream source stream
   * @param outputStream destination stream
   * @return bytes copied
   * @throws IOException
   */
  public static int copyStreamContent(InputStream inputStream, OutputStream outputStream) throws IOException {
    final byte[] buffer = new byte[10 * 1024];
    int count, total = 0;
    while ((count = inputStream.read(buffer)) > 0) {
      outputStream.write(buffer, 0, count);
      total += count;
    }
    return total;
  }

  public static byte[] loadFromStream(InputStream inputStream) throws IOException {
    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    copyStreamContent(inputStream, outputStream);
    inputStream.close();
    final byte[] result = outputStream.toByteArray();
    return result;
  }

  public static String readText(InputStream inputStream) throws IOException {
    final byte[] data = loadFromStream(inputStream);
    final String result = new String(data);
    return result;
  }

  public static String convertSeparators(String s) {
    try {
      return new String(readTextAndConvertSeparators(new StringReader(s)));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static char[] readTextAndConvertSeparators(Reader reader) throws IOException {
    char[] buffer = readText(reader);

    int dst = 0;
    char prev = ' ';
    for( int src = 0; src < buffer.length; src++ ) {
      char c = buffer[src];
      switch( c ) {
        case '\r':
          buffer[dst++] = '\n';
          break;
        case '\n':
          if( prev != '\r' ) {
            buffer[dst++] = '\n';
          }
          break;
        default:
          buffer[dst++] = c;
          break;
      }
      prev = c;
    }

    char chars[] = new char[dst];
    System.arraycopy(buffer, 0, chars, 0, chars.length);
    return chars;
  }

  private static char[] readText(Reader reader) throws IOException {
    CharArrayWriter writer = new CharArrayWriter();

    {
      char[] buffer = new char[2048];
      while (true) {
        int read = reader.read(buffer);
        if (read < 0) break;
        writer.write(buffer, 0, read);
      }
    }

    char[] buffer = writer.toCharArray();
    return buffer;
  }
}
