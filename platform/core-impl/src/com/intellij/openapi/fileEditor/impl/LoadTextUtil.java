/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.lang.properties.charset.Native2AsciiCharset;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

public final class LoadTextUtil {
  private static final Key<String> DETECTED_LINE_SEPARATOR_KEY = Key.create("DETECTED_LINE_SEPARATOR_KEY");
  @Nls private static final String AUTO_DETECTED_FROM_BOM = "auto-detected from BOM";

  private LoadTextUtil() {
  }

  @NotNull
  private static Pair<CharSequence, String> convertLineSeparators(@NotNull CharBuffer buffer) {
    int dst = 0;
    char prev = ' ';
    int crCount = 0;
    int lfCount = 0;
    int crlfCount = 0;

    final int length = buffer.length();
    final char[] bufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);

    for (int src = 0; src < length; src++) {
      char c = bufferArray != null ? bufferArray[src]:buffer.charAt(src);
      switch (c) {
        case '\r':
          if(bufferArray != null) bufferArray[dst++] = '\n';
          else buffer.put(dst++, '\n');
          crCount++;
          break;
        case '\n':
          if (prev == '\r') {
            crCount--;
            crlfCount++;
          }
          else {
            if(bufferArray != null) bufferArray[dst++] = '\n';
            else buffer.put(dst++, '\n');
            lfCount++;
          }
          break;
        default:
          if(bufferArray != null) bufferArray[dst++] = c;
          else buffer.put(dst++, c);
          break;
      }
      prev = c;
    }

    String detectedLineSeparator = null;
    if (crlfCount > crCount && crlfCount > lfCount) {
      detectedLineSeparator = "\r\n";
    }
    else if (crCount > lfCount) {
      detectedLineSeparator = "\r";
    }
    else if (lfCount > 0) {
      detectedLineSeparator = "\n";
    }

    CharSequence result = buffer.length() == dst ? buffer : buffer.subSequence(0, dst);
    return Pair.create(result, detectedLineSeparator);
  }

  private static Charset detectCharset(@NotNull VirtualFile virtualFile, @NotNull byte[] content) {
    Charset charset = null;

    Trinity<Charset,CharsetToolkit.GuessedEncoding, byte[]> guessed = guessFromContent(virtualFile, content, content.length);
    if (guessed != null && guessed.first != null) {
      charset = guessed.first;
    }
    else {
      FileType fileType = virtualFile.getFileType();
      String charsetName = fileType.getCharset(virtualFile, content);

      if (charsetName == null) {
        Charset specifiedExplicitly = EncodingRegistry.getInstance().getEncoding(virtualFile, true);
        if (specifiedExplicitly != null) {
          charset = specifiedExplicitly;
        }
      }
      else {
        charset = CharsetToolkit.forName(charsetName);
      }
    }

    charset = charset == null ? EncodingRegistry.getInstance().getDefaultCharset() : charset;
    if (EncodingRegistry.getInstance().isNative2Ascii(virtualFile)) {
      charset = Native2AsciiCharset.wrap(charset);
    }
    virtualFile.setCharset(charset);
    return charset;
  }

  @NotNull
  public static Charset detectCharsetAndSetBOM(@NotNull VirtualFile virtualFile, @NotNull byte[] content) {
    return doDetectCharsetAndSetBOM(virtualFile, content, true).getFirst();
  }

  @NotNull
  private static Pair<Charset, byte[]> doDetectCharsetAndSetBOM(@NotNull VirtualFile virtualFile, @NotNull byte[] content, boolean saveBOM) {
    Charset charset = virtualFile.isCharsetSet() ? virtualFile.getCharset() : detectCharset(virtualFile, content);
    Pair<Charset,byte[]> bomAndCharset = getBOMAndCharset(content, charset);
    final byte[] bom = bomAndCharset.second;
    if (saveBOM && bom != null && bom.length != 0) {
      virtualFile.setBOM(bom);
      setCharsetWasDetectedFromBytes(virtualFile, AUTO_DETECTED_FROM_BOM);
    }
    return bomAndCharset;
  }

  @Nullable("null means no luck, otherwise it's tuple(guessed encoding, hint about content if was unable to guess, BOM)")
  public static Trinity<Charset, CharsetToolkit.GuessedEncoding, byte[]> guessFromContent(@NotNull VirtualFile virtualFile, @NotNull byte[] content, int length) {
    EncodingRegistry settings = EncodingRegistry.getInstance();
    boolean shouldGuess = settings != null && settings.isUseUTFGuessing(virtualFile);
    CharsetToolkit toolkit = shouldGuess ? new CharsetToolkit(content, EncodingRegistry.getInstance().getDefaultCharset()) : null;
    String detectedFromBytes = null;
    try {
      if (shouldGuess) {
        toolkit.setEnforce8Bit(true);
        Charset charset = toolkit.guessFromBOM();
        if (charset != null) {
          detectedFromBytes = AUTO_DETECTED_FROM_BOM;
          byte[] bom = CharsetToolkit.getBom(charset);
          if (bom == null) bom = CharsetToolkit.UTF8_BOM;
          return Trinity.create(charset, null, bom);
        }
        CharsetToolkit.GuessedEncoding guessed = toolkit.guessFromContent(length);
        if (guessed == CharsetToolkit.GuessedEncoding.VALID_UTF8) {
          detectedFromBytes = "auto-detected from bytes";
          return Trinity.create(CharsetToolkit.UTF8_CHARSET, guessed, null); //UTF detected, ignore all directives
        }
        return Trinity.create(null, guessed,null);
      }
      return null;
    }
    finally {
      setCharsetWasDetectedFromBytes(virtualFile, detectedFromBytes);
    }
  }

  @NotNull
  private static Pair<Charset,byte[]> getBOMAndCharset(@NotNull byte[] content, final Charset charset) {
    if (charset != null && charset.name().contains(CharsetToolkit.UTF8) && CharsetToolkit.hasUTF8Bom(content)) {
      return Pair.create(charset, CharsetToolkit.UTF8_BOM);
    }
    try {
      Charset fromBOM = CharsetToolkit.guessFromBOM(content);
      if (fromBOM != null) {
        return Pair.create(fromBOM, CharsetToolkit.getBom(fromBOM));
      }
    }
    catch (UnsupportedCharsetException ignore) {
    }

    return Pair.create(charset, ArrayUtil.EMPTY_BYTE_ARRAY);
  }

  /**
   * Overwrites file with text and sets modification stamp and time stamp to the specified values.
   * <p/>
   * Normally you should not use this method.
   *
   * @param project
   * @param virtualFile
   * @param requestor            any object to control who called this method. Note that
   *                             it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                             See {@link com.intellij.openapi.vfs.VirtualFileEvent#getRequestor}
   * @param text
   * @param newModificationStamp new modification stamp or -1 if no special value should be set @return <code>Writer</code>
   * @throws java.io.IOException if an I/O error occurs
   * @see VirtualFile#getModificationStamp()
   */
  public static void write(@Nullable Project project,
                           @NotNull VirtualFile virtualFile,
                           @NotNull Object requestor,
                           @NotNull String text,
                           long newModificationStamp) throws IOException {
    Charset existing = virtualFile.getCharset();
    Pair<Charset, byte[]> chosen = charsetForWriting(project, virtualFile, text, existing);
    Charset charset = chosen.first;
    byte[] buffer = chosen.second;
    if (charset != null) {
      if (!charset.equals(existing)) {
        virtualFile.setCharset(charset);
      }
    }
    setDetectedFromBytesFlagBack(virtualFile, buffer);

    OutputStream outputStream = virtualFile.getOutputStream(requestor, newModificationStamp, -1);
    try {
      outputStream.write(buffer);
    }
    finally {
      outputStream.close();
    }
  }


  @NotNull
  private static Pair<Charset, byte[]> charsetForWriting(@Nullable Project project,
                                                         @NotNull VirtualFile virtualFile,
                                                         @NotNull String text,
                                                         @Nullable Charset existing) {
    Charset specified = extractCharsetFromFileContent(project, virtualFile, text);
    Pair<Charset, byte[]> chosen = chooseMostlyHarmlessCharset(existing, specified, text);
    Charset charset = chosen.first;

    // in case of "UTF-16", OutputStreamWriter sometimes adds BOM on it's own.
    // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6800103
    byte[] bom = virtualFile.getBOM();
    Charset fromBom = bom == null ? null : CharsetToolkit.guessFromBOM(bom);
    if (fromBom != null && !fromBom.equals(charset)) {
      chosen = Pair.create(fromBom, toBytes(text, fromBom));
    }
    return chosen;
  }

  public static void setDetectedFromBytesFlagBack(@NotNull VirtualFile virtualFile, @NotNull byte[] content) {
    if (virtualFile.getBOM() == null) {
      guessFromContent(virtualFile, content, content.length);
    }
    else {
      // prevent file to be reloaded in other encoding after save with BOM
      setCharsetWasDetectedFromBytes(virtualFile, AUTO_DETECTED_FROM_BOM);
    }
  }

  @NotNull
  public static Pair<Charset, byte[]> chooseMostlyHarmlessCharset(Charset existing, Charset specified, @NotNull String text) {
    if (existing == null) return Pair.create(specified, toBytes(text, specified));
    if (specified == null || specified.equals(existing)) return Pair.create(specified, toBytes(text, existing));

    byte[] out = isSupported(specified, text);
    if (out != null) return Pair.create(specified, out); //if explicitly specified encoding is safe, return it
    out = isSupported(existing, text);
    if (out != null) return Pair.create(existing, out);   //otherwise stick to the old encoding if it's ok
    return Pair.create(specified, toBytes(text, specified)); //if both are bad there is no difference
  }

  @NotNull
  private static byte[] toBytes(@NotNull String text, @Nullable Charset charset) {
    return charset == null ? text.getBytes() : text.getBytes(charset);
  }

  @Nullable("null means not supported, otherwise it is converted byte stream")
  private static byte[] isSupported(@NotNull Charset charset, @NotNull String str) {
    if (!charset.canEncode()) return null;
    byte[] bytes = str.getBytes(charset);
    if (!str.equals(new String(bytes, charset))) {
      return null;
    }

    return bytes;
  }

  public static Charset extractCharsetFromFileContent(@Nullable Project project, @NotNull VirtualFile virtualFile, @NotNull String text) {
    Charset charset = charsetFromContentOrNull(project, virtualFile, text);
    if (charset == null) charset = virtualFile.getCharset();
    return charset;
  }

  @Nullable("returns null if cannot determine from content")
  public static Charset charsetFromContentOrNull(@Nullable Project project, @NotNull VirtualFile virtualFile, @NotNull String text) {
    FileType fileType = virtualFile.getFileType();
    if (fileType instanceof LanguageFileType) {
      return ((LanguageFileType)fileType).extractCharsetFromFileContent(project, virtualFile, text);
    }
    return null;
  }

  @NotNull
  public static CharSequence loadText(@NotNull VirtualFile file) {
    if (file instanceof LightVirtualFile) {
      CharSequence content = ((LightVirtualFile)file).getContent();
      if (StringUtil.indexOf(content, '\r') == -1) return content;

      CharBuffer buffer = CharBuffer.allocate(content.length());
      buffer.append(content);
      buffer.rewind();
      return convertLineSeparators(buffer).first;
    }

    assert !file.isDirectory() : "'"+file.getPresentableUrl() + "' is directory";
    final FileType fileType = file.getFileType();

    if (fileType.isBinary()) {
      final BinaryFileDecompiler decompiler = BinaryFileTypeDecompilers.INSTANCE.forFileType(fileType);
      if (decompiler != null) {
        CharSequence text = decompiler.decompile(file);
        StringUtil.assertValidSeparators(text);
        return text;
      }

      throw new IllegalArgumentException("Attempt to load text for binary file, that doesn't have decompiler plugged in: "+file.getPresentableUrl());
    }

    try {
      byte[] bytes = file.contentsToByteArray();
      return getTextByBinaryPresentation(bytes, file);
    }
    catch (IOException e) {
      return ArrayUtil.EMPTY_CHAR_SEQUENCE;
    }
  }

  @NotNull
  public static CharSequence getTextByBinaryPresentation(@NotNull final byte[] bytes, @NotNull VirtualFile virtualFile) {
    return getTextByBinaryPresentation(bytes, virtualFile, true, true);
  }

  @NotNull
  public static CharSequence getTextByBinaryPresentation(@NotNull byte[] bytes,
                                                         @NotNull VirtualFile virtualFile,
                                                         boolean saveDetectedSeparators,
                                                         boolean saveBOM) {
    Pair<Charset, byte[]> pair = doDetectCharsetAndSetBOM(virtualFile, bytes, saveBOM);
    final Charset charset = pair.getFirst();
    byte[] bom = pair.getSecond();
    int offset = bom == null ? 0 : bom.length;

    final Pair<CharSequence, String> result = convertBytes(bytes, charset, offset);
    if (saveDetectedSeparators) {
      virtualFile.putUserData(DETECTED_LINE_SEPARATOR_KEY, result.getSecond());
    }
    return result.getFirst();
  }

  /**
   * Get detected line separator, if the file never been loaded, is loaded if checkFile parameter is specified.
   *
   * @param file      the file to check
   * @param checkFile if the line separator was not detected before, try to detect it
   * @return the detected line separator or null
   */
  @Nullable
  public static String detectLineSeparator(@NotNull VirtualFile file, boolean checkFile) {
    String lineSeparator = getDetectedLineSeparator(file);
    if (lineSeparator == null && checkFile) {
      try {
        getTextByBinaryPresentation(file.contentsToByteArray(), file);
        lineSeparator = getDetectedLineSeparator(file);
      }
      catch (IOException e) {
        // null will be returned
      }
    }
    return lineSeparator;
  }

  static String getDetectedLineSeparator(@NotNull VirtualFile file) {
    return file.getUserData(DETECTED_LINE_SEPARATOR_KEY);
  }

  @NotNull
  public static CharSequence getTextByBinaryPresentation(@NotNull byte[] bytes, Charset charset) {
    Pair<Charset, byte[]> pair = getBOMAndCharset(bytes, charset);
    byte[] bom = pair.getSecond();
    int offset = bom == null ? 0 : bom.length;

    final Pair<CharSequence, String> result = convertBytes(bytes, charset, offset);
    return result.getFirst();
  }

  // do not need to think about BOM here. it is processed outside
  @NotNull
  private static Pair<CharSequence, String> convertBytes(@NotNull byte[] bytes, Charset charset, final int startOffset) {
    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, startOffset, bytes.length - startOffset);

    if (charset == null) {
      charset = CharsetToolkit.getDefaultSystemCharset();
    }
    if (charset == null) {
      charset = Charset.forName("ISO-8859-1");
    }
    CharBuffer charBuffer = charset.decode(byteBuffer);
    return convertLineSeparators(charBuffer);
  }

  private static final Key<String> CHARSET_WAS_DETECTED_FROM_BYTES = Key.create("CHARSET_WAS_DETECTED_FROM_BYTES");
  @Nullable("null if was not detected, otherwise the reason it was")
  public static String wasCharsetDetectedFromBytes(@NotNull VirtualFile virtualFile) {
    return virtualFile.getUserData(CHARSET_WAS_DETECTED_FROM_BYTES);
  }

  public static void setCharsetWasDetectedFromBytes(@NotNull VirtualFile virtualFile,
                                                    @Nullable("null if was not detected, otherwise the reason it was") String reason) {
    virtualFile.putUserData(CHARSET_WAS_DETECTED_FROM_BYTES, reason);
  }
}
