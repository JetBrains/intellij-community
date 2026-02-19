package com.intellij.database.extractors;

import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


public class TextInfo {
  public final String text;
  public final byte[] bytes;
  public final Charset charset;

  public TextInfo(@NotNull String text, byte @NotNull [] bytes, @NotNull Charset charset) {
    this.text = text;
    this.bytes = bytes;
    this.charset = charset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TextInfo info = (TextInfo)o;

    if (!text.equals(info.text)) return false;
    if (!charset.equals(info.charset)) return false;
    return Arrays.equals(bytes, info.bytes);
  }

  @Override
  public int hashCode() {
    int result = text.hashCode();
    result = 31 * result + charset.hashCode();
    result = 31 * result + Arrays.hashCode(bytes);
    return result;
  }

  public static @Nullable TextInfo tryDetectString(byte @NotNull [] bytes) {
    Charset charset = guessCharsetFromContent(bytes);
    String text = charset == null ? null : validateDetectedString(CharsetToolkit.tryDecodeString(bytes, charset));
    return text == null ? null : new TextInfo(text, bytes, charset);
  }

  private static @Nullable Charset guessCharsetFromContent(byte @NotNull [] content) {
    if (content.length == 0) return StandardCharsets.UTF_8;
    // can't use CharsetToolkit.guessEncoding here because of false-positive INVALID_UTF8
    CharsetToolkit toolkit = new CharsetToolkit(content, Charset.defaultCharset(), false);

    Charset fromBOM = toolkit.guessFromBOM();
    if (fromBOM != null) return fromBOM;

    CharsetToolkit.GuessedEncoding guessedEncoding = toolkit.guessFromContent(content.length);
    return switch (guessedEncoding) {
      case SEVEN_BIT, VALID_UTF8 -> StandardCharsets.UTF_8;
      default -> null;
    };
  }

  private static @Nullable String validateDetectedString(@Nullable String string) {
    if (string == null) return null;
    for (int i = 0; i < string.length(); i++) {
      char c = string.charAt(i);
      if (c < 32 && c != '\n' && c != '\r' && c != '\t') return null;
    }
    return string;
  }
}
