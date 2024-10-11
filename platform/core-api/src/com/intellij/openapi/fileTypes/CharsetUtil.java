// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CharsetUtil {
  private static final Map<String, Boolean> ourSupportsCharsetDetection = new ConcurrentHashMap<>();

  private static boolean overridesExtractCharsetFromContent(LanguageFileType fileType) {
    Class<?> ftClass = fileType.getClass();
    String methodName = "extractCharsetFromFileContent";
    Class<?> declaring1 = ReflectionUtil.getMethodDeclaringClass(ftClass, methodName, Project.class, VirtualFile.class, String.class);
    Class<?> declaring2 = ReflectionUtil.getMethodDeclaringClass(ftClass, methodName, Project.class, VirtualFile.class, CharSequence.class);
    return !LanguageFileType.class.equals(declaring1) || !LanguageFileType.class.equals(declaring2);
  }

  public static Charset extractCharsetFromFileContent(@Nullable Project project,
                                                      @Nullable VirtualFile virtualFile,
                                                      @Nullable FileType fileType,
                                                      @NotNull CharSequence text) {
    if (fileType instanceof LanguageFileType &&
        // otherwise the default implementations will always convert CharSequence to String unnecessarily, producing garbage
        ourSupportsCharsetDetection.computeIfAbsent(fileType.getName(),
                                                    __ -> overridesExtractCharsetFromContent((LanguageFileType)fileType))) {
      return ((LanguageFileType)fileType).extractCharsetFromFileContent(project, virtualFile, text);
    }
    return null;
  }

  /**
   * Checks if the given text contains characters that cannot be mapped to the specified charset during encoding.
   *
   * @param text    the character sequence to be checked
   * @param charset the charset to be used for encoding
   * @return a {@code TextRange} representing the range of unmappable characters, or {@code null} if all characters can be mapped
   */
  public static @Nullable TextRange findUnmappableCharacters(@Nullable CharSequence text, @NotNull Charset charset) {
    if (text == null || text.length() == 0) return null;

    CharBuffer inputBuffer = text instanceof CharBuffer ? (CharBuffer)text : CharBuffer.wrap(text);
    CharsetEncoder encoder = charset.newEncoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT);

    int remainingChars = inputBuffer.remaining();
    ByteBuffer encodedBuffer = ByteBuffer.allocate((int)(encoder.maxBytesPerChar() * remainingChars));
    inputBuffer.rewind();
    encodedBuffer.clear();

    CoderResult encodeResult;

    while (true) {
      encodeResult = encoder.encode(inputBuffer, encodedBuffer, true);
      if (encodeResult.isUnderflow()) {
        encodeResult = encoder.flush(encodedBuffer);
      }
      if (!encodeResult.isOverflow()) {
        break;
      }

      ByteBuffer tempBuffer = ByteBuffer.allocate(2 * encodedBuffer.capacity());
      encodedBuffer.flip();
      tempBuffer.put(encodedBuffer);
      encodedBuffer = tempBuffer;
    }

    if (encodeResult.isError()) {
      return TextRange.from(inputBuffer.position(), encodeResult.length());
    }

    encodedBuffer.flip();
    CharBuffer decodedBuffer = CharBuffer.allocate(encodedBuffer.remaining());
    TextRange range = findUnmappableRange(encodedBuffer, charset, decodedBuffer);

    if (range != null) {
      return range;
    }

    if (decodedBuffer.position() != remainingChars) {
      return TextRange.from(Math.min(remainingChars, decodedBuffer.position()), 1);
    }

    inputBuffer.rewind();
    decodedBuffer.rewind();
    int commonPrefixLength = StringUtil.commonPrefixLength(inputBuffer, decodedBuffer);

    return commonPrefixLength == remainingChars ? null : TextRange.from(commonPrefixLength, 1);
  }

  /**
   * Checks if the given byte buffer contains unmappable characters for the specified charset.
   *
   * @param byteBuffer the byte buffer to be checked
   * @param charset    the charset to be used for decoding
   * @return a {@code TextRange} representing the range of unmappable characters, or {@code null} if all characters can be mapped
   */
  public static @Nullable TextRange findUnmappableCharacters(@NotNull ByteBuffer byteBuffer, @NotNull Charset charset) {
    return findUnmappableRange(byteBuffer, charset, CharBuffer.allocate(byteBuffer.remaining()));
  }

  /**
   * Identifies the range of unmappable characters in the byte buffer during decoding with the specified charset.
   *
   * @param byteBuffer    the input byte buffer to be checked
   * @param charset       the charset used for decoding
   * @param decodedBuffer the buffer to store the result of decoding; must have enough capacity to hold the decoded characters
   * @return a {@code TextRange} object representing the range of unmappable characters, or {@code null} if all characters are mappable
   */
  private static @Nullable TextRange findUnmappableRange(@NotNull ByteBuffer byteBuffer,
                                                         @NotNull Charset charset,
                                                         @NotNull CharBuffer decodedBuffer) {
    CharsetDecoder decoder = charset.newDecoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT);

    CoderResult result = decoder.decode(byteBuffer, decodedBuffer, true);
    if (result.isError()) {
      return TextRange.from(byteBuffer.position(), result.length());
    }

    result = decoder.flush(decodedBuffer);
    if (result.isError()) {
      return TextRange.from(byteBuffer.position(), result.length());
    }

    return null;
  }
}
