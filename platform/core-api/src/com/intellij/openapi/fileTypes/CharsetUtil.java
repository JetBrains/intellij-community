// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
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
    if(text == null || text.length() == 0) return null;
    return findUnmappableRange(CharBuffer.wrap(text), Ref.create(), CharBuffer.allocate(text.length()), charset);
  }

  /**
   * Identifies the range of characters that either fail to encode or decode properly with the specified charset.
   *
   * @param inputBuffer  the input character buffer to be checked
   * @param encodedBufferRef a reference to the output byte buffer for storing encoded bytes
   * @param decodedBuffer a character buffer to hold the decoded characters
   * @param charset the charset used for encoding and decoding
   * @return a {@code TextRange} object representing the range of unmappable characters, or {@code null} if all characters are mappable
   */
  private static @Nullable TextRange findUnmappableRange(@NotNull CharBuffer inputBuffer,
                                                         @NotNull Ref<ByteBuffer> encodedBufferRef,
                                                         @NotNull CharBuffer decodedBuffer,
                                                         @NotNull Charset charset) {
    CharsetEncoder encoder = charset.newEncoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT);
    int remainingChars = inputBuffer.limit();

    ByteBuffer encodedBuffer = encodedBufferRef.get();
    if (encodedBuffer == null) {
      encodedBufferRef.set(encodedBuffer = ByteBuffer.allocate((int)(encoder.averageBytesPerChar() * remainingChars)));
    }
    encodedBuffer.rewind();
    encodedBuffer.limit(encodedBuffer.capacity());
    inputBuffer.rewind();
    inputBuffer.position(0);
    CoderResult encodeResult;

    while (true) {
      encodeResult = inputBuffer.hasRemaining() ? encoder.encode(inputBuffer, encodedBuffer, true) : CoderResult.UNDERFLOW;
      if (encodeResult.isUnderflow()) {
        encodeResult = encoder.flush(encodedBuffer);
      }
      if (!encodeResult.isOverflow()) {
        break;
      }

      ByteBuffer tempBuffer = ByteBuffer.allocate(3 * encodedBuffer.capacity() / 2 + 1);
      encodedBuffer.flip();
      tempBuffer.put(encodedBuffer);
      encodedBufferRef.set(encodedBuffer = tempBuffer);
    }

    if (encodeResult.isError()) {
      return TextRange.from(inputBuffer.position(), encodeResult.length());
    }

    int encodedLength = encodedBuffer.position();
    CharsetDecoder decoder = charset.newDecoder()
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .onMalformedInput(CodingErrorAction.REPORT);
    encodedBuffer.rewind();
    encodedBuffer.limit(encodedLength);
    decodedBuffer.rewind();

    CoderResult decodeResult = decoder.decode(encodedBuffer, decodedBuffer, true);
    if (decodeResult.isError()) {
      return TextRange.from(decodedBuffer.position(), decodeResult.length());
    }

    if (decodedBuffer.position() != remainingChars) {
      return TextRange.from(Math.min(remainingChars, decodedBuffer.position()), 1);
    }

    inputBuffer.rewind();
    inputBuffer.position(0);
    decodedBuffer.rewind();
    int commonPrefixLength = StringUtil.commonPrefixLength(inputBuffer, decodedBuffer);
    return commonPrefixLength == remainingChars ? null : TextRange.from(commonPrefixLength, 1);
  }
}
