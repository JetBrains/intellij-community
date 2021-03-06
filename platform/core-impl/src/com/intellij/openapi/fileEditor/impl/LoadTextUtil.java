// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.CharsetUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingRegistry;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.*;
import com.intellij.util.text.ByteArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class LoadTextUtil {
  private static final Logger LOG = Logger.getInstance(LoadTextUtil.class);

  public enum AutoDetectionReason { FROM_BOM, FROM_BYTES }

  private static final int UNLIMITED = -1;

  private LoadTextUtil() { }

  @NotNull
  private static ConvertResult convertLineSeparatorsToSlashN(@NotNull CharBuffer buffer) {
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

    CharSequence result = buffer.length() == dst ? buffer : buffer.subSequence(0, dst);
    return new ConvertResult(result, crCount, lfCount, crlfCount);
  }

  private static final char UNDEFINED_CHAR = 0xFDFF;

  @NotNull
  private static ConvertResult convertLineSeparatorsToSlashN(byte @NotNull [] charsAsBytes, int startOffset, int endOffset) {
    int lineBreak = findLineBreakOrWideChar(charsAsBytes, startOffset, endOffset);
    if (!BitUtil.isSet(lineBreak, CR) && !BitUtil.isSet(lineBreak, WIDE)) {
      // optimisation: if there is no CR in the file, no line separator conversion is necessary. we can re-use the passed byte buffer inplace
      ByteArrayCharSequence sequence = new ByteArrayCharSequence(charsAsBytes, startOffset, endOffset);
      return new ConvertResult(sequence, 0, BitUtil.isSet(lineBreak, LF) ? 1 : 0, 0);
    }

    if (BitUtil.isSet(lineBreak, WIDE)) {
      // characters outside AS_ASCII range found, insert UNDEFINED_CHAR
      return convertWideCharacters(charsAsBytes, startOffset, endOffset);
    }
    // convert \r\n to \n, \r to \n
    int dst = 0;
    char prev = ' ';
    int crCount = 0;
    int lfCount = 0;
    int crlfCount = 0;
    byte[] result = new byte[endOffset-startOffset];

    for (int src = startOffset; src < endOffset; src++) {
      char c = (char)charsAsBytes[src];
      switch (c) {
        case '\r':
          result[dst++] = '\n';
          crCount++;
          break;
        case '\n':
          if (prev == '\r') {
            crCount--;
            crlfCount++;
          }
          else {
            result[dst++] = '\n';
            lfCount++;
          }
          break;
        default:
          result[dst++] = (byte)c;
          break;
      }
      prev = c;
    }

    ByteArrayCharSequence sequence = new ByteArrayCharSequence(result, 0, dst);
    return new ConvertResult(sequence, crCount, lfCount, crlfCount);
  }

  @NotNull
  private static ConvertResult convertWideCharacters(byte @NotNull [] charsAsBytes, int startOffset, int endOffset) {
    // convert \r\n to \n, \r to \n, wide char to UNDEFINED_CHAR
    char prev = ' ';
    int crCount = 0;
    int lfCount = 0;
    int crlfCount = 0;
    StringBuilder result = new StringBuilder(endOffset - startOffset);

    for (int src = startOffset; src < endOffset; src++) {
      char c = (char)charsAsBytes[src];
      if (c >= 128) {
        result.append(UNDEFINED_CHAR);
        continue;
      }
      switch (c) {
        case '\r':
          result.append('\n');
          crCount++;
          break;
        case '\n':
          if (prev == '\r') {
            crCount--;
            crlfCount++;
          }
          else {
            result.append('\n');
            lfCount++;
          }
          break;
        default:
          result.append(c);
          break;
      }
      prev = c;
    }
    return new ConvertResult(result, crCount, lfCount, crlfCount);
  }

  private static final int CR = 1;
  private static final int LF = 2;
  private static final int WIDE = 4;
  @MagicConstant(flags = {CR, LF, WIDE})
  private static int findLineBreakOrWideChar(byte @NotNull [] ints, int start, int end) {
    int flags = 0;
    for (int i = start; i < end; i++) {
      byte c = ints[i];
      if (c == (byte)'\r') flags |= CR;
      if (c == (byte)'\n') flags |= LF;
      if (c < 0) flags |= WIDE;
    }
    return flags;
  }

  // private fake charsets for files which have one-byte-for-ascii-characters encoding but contain seven bits characters only. used for optimization since we don't have to encode-decode bytes here.
  private static final Charset INTERNAL_SEVEN_BIT_UTF8 = new SevenBitCharset(StandardCharsets.UTF_8);
  private static final Charset INTERNAL_SEVEN_BIT_ISO_8859_1 = new SevenBitCharset(StandardCharsets.ISO_8859_1);
  private static final Charset INTERNAL_SEVEN_BIT_WIN_1251 = new SevenBitCharset(CharsetToolkit.WIN_1251_CHARSET);
  private static class SevenBitCharset extends Charset {
    private final Charset myBaseCharset;

    /**
     * should be {@code this.name().contains(CharsetToolkit.UTF8)} for {@link #getOverriddenCharsetByBOM(byte[], Charset)} to work
     */
    SevenBitCharset(@NotNull Charset baseCharset) {
      super("IJ__7BIT_"+baseCharset.name(), ArrayUtilRt.EMPTY_STRING_ARRAY);
      myBaseCharset = baseCharset;
    }

    @Override
    public boolean contains(Charset cs) {
      throw new IllegalStateException();
    }

    @Override
    public CharsetDecoder newDecoder() {
      throw new IllegalStateException();
    }

    @Override
    public CharsetEncoder newEncoder() {
      throw new IllegalStateException();
    }
  }

  public static class DetectResult {
    public final Charset hardCodedCharset;
    public final CharsetToolkit.GuessedEncoding guessed;
    public final byte @Nullable [] BOM;

    DetectResult(Charset hardCodedCharset, CharsetToolkit.GuessedEncoding guessed, byte @Nullable [] BOM) {
      this.hardCodedCharset = hardCodedCharset;
      this.guessed = guessed;
      this.BOM = BOM;
    }
  }

  // guess from file type or content
  @NotNull
  private static DetectResult detectHardCharset(@NotNull VirtualFile virtualFile,
                                                byte @NotNull [] content,
                                                int length,
                                                @NotNull FileType fileType) {
    String charsetName = fileType.getCharset(virtualFile, content);
    Charset charset = charsetName == null ? null : CharsetToolkit.forName(charsetName);
    DetectResult guessed = guessFromContent(virtualFile, content, length);
    Charset hardCodedCharset = charset == null ? guessed.hardCodedCharset : charset;

    if (hardCodedCharset == null && guessed.guessed == CharsetToolkit.GuessedEncoding.VALID_UTF8) {
      return new DetectResult(StandardCharsets.UTF_8, guessed.guessed, guessed.BOM);
    }
    return new DetectResult(hardCodedCharset, guessed.guessed, guessed.BOM);
  }

  @NotNull
  public static Charset detectCharsetAndSetBOM(@NotNull VirtualFile virtualFile, byte @NotNull [] content, @NotNull FileType fileType) {
    Charset internalCharset = detectInternalCharsetAndSetBOM(virtualFile, content, content.length, true, fileType).hardCodedCharset;
    return internalCharset instanceof SevenBitCharset ? ((SevenBitCharset)internalCharset).myBaseCharset : internalCharset;
  }

  @NotNull
  private static Charset getDefaultCharsetFromEncodingManager(@NotNull VirtualFile virtualFile) {
    Charset specifiedExplicitly = EncodingRegistry.getInstance().getEncoding(virtualFile, true);
    return ObjectUtils.notNull(specifiedExplicitly, EncodingRegistry.getInstance().getDefaultCharset());
  }

  @NotNull
  private static DetectResult detectInternalCharsetAndSetBOM(@NotNull VirtualFile file,
                                                             byte @NotNull [] content,
                                                             int length,
                                                             boolean saveBOM,
                                                             @NotNull FileType fileType) {
    DetectResult info = detectHardCharset(file, content, length, fileType);

    Charset charset;
    if (info.hardCodedCharset == null) {
      charset = file.isCharsetSet() ? file.getCharset() : getDefaultCharsetFromEncodingManager(file);
    }
    else {
      charset = info.hardCodedCharset;
    }

    byte[] bom = info.BOM;
    if (saveBOM && bom != null && bom.length != 0) {
      file.setBOM(bom);
      setCharsetAutoDetectionReason(file, AutoDetectionReason.FROM_BOM);
    }

    file.setCharset(charset);

    Charset result = charset;
    // optimisation
    if (info.guessed == CharsetToolkit.GuessedEncoding.SEVEN_BIT) {
      if (charset == StandardCharsets.UTF_8) {
        result = INTERNAL_SEVEN_BIT_UTF8;
      }
      else if (charset == StandardCharsets.ISO_8859_1) {
        result = INTERNAL_SEVEN_BIT_ISO_8859_1;
      }
      else if (charset == CharsetToolkit.WIN_1251_CHARSET) {
        result = INTERNAL_SEVEN_BIT_WIN_1251;
      }
    }

    return new DetectResult(result, info.guessed, bom);
  }


  @NotNull
  public static DetectResult guessFromContent(@NotNull VirtualFile virtualFile, byte @NotNull [] content) {
    return guessFromContent(virtualFile, content, content.length);
  }

  private static final boolean GUESS_UTF = Boolean.parseBoolean(System.getProperty("idea.guess.utf.encoding", "true"));
  @NotNull
  private static DetectResult guessFromContent(@NotNull VirtualFile virtualFile, byte @NotNull [] content, int length) {
    AutoDetectionReason detectedFromBytes = null;
    try {
      DetectResult info;
      if (GUESS_UTF) {
        info = guessFromBytes(content, 0, length, getDefaultCharsetFromEncodingManager(virtualFile));
        if (info.BOM != null) {
          detectedFromBytes = AutoDetectionReason.FROM_BOM;
        }
        else if (info.guessed == CharsetToolkit.GuessedEncoding.VALID_UTF8) {
          detectedFromBytes = AutoDetectionReason.FROM_BYTES;
        }
      }
      else {
        info = new DetectResult(null, null, null);
      }
      return info;
    }
    finally {
      setCharsetAutoDetectionReason(virtualFile, detectedFromBytes);
    }
  }

  @NotNull
  private static DetectResult guessFromBytes(byte @NotNull [] content,
                                             int startOffset, int endOffset,
                                             @NotNull Charset defaultCharset) {
    if (content.length == 0) {
      return new DetectResult(null, CharsetToolkit.GuessedEncoding.SEVEN_BIT, null);
    }
    CharsetToolkit toolkit = new CharsetToolkit(content, defaultCharset, true);
    Charset charset = toolkit.guessFromBOM();
    if (charset != null) {
      byte[] bom = ObjectUtils.notNull(CharsetToolkit.getMandatoryBom(charset), CharsetToolkit.UTF8_BOM);
      return new DetectResult(charset, null, bom);
    }
    CharsetToolkit.GuessedEncoding guessed = toolkit.guessFromContent(startOffset, endOffset);
    if (guessed == CharsetToolkit.GuessedEncoding.VALID_UTF8) {
      return new DetectResult(StandardCharsets.UTF_8, CharsetToolkit.GuessedEncoding.VALID_UTF8, null); //UTF detected, ignore all directives
    }
    return new DetectResult(null, guessed, null);
  }

  /**
   * Tries to detect text in the {@code bytes} and call the {@code fileTextProcessor} with the text (if detected) or with null if not
   */
  public static String getTextFromBytesOrNull(byte @NotNull [] bytes, int startOffset, int endOffset) {
    Charset defaultCharset = EncodingManager.getInstance().getDefaultCharset();
    DetectResult info = guessFromBytes(bytes, startOffset, endOffset, defaultCharset);
    Charset charset;
    if (info.hardCodedCharset != null) {
      charset = info.hardCodedCharset;
    }
    else {
      switch (info.guessed) {
        case SEVEN_BIT:
          charset = StandardCharsets.US_ASCII;
          break;
        case VALID_UTF8:
          charset = StandardCharsets.UTF_8;
          break;
        case INVALID_UTF8:
        case BINARY:
          // the charset was not detected so the file is likely binary
          return null;
        default:
          throw new IllegalStateException(String.valueOf(info.guessed));
      }
    }
    byte[] bom = info.BOM;
    ConvertResult result = convertBytes(bytes, Math.min(startOffset + (bom == null ? 0 : bom.length), endOffset), endOffset, charset);
    return result.text.toString();
  }

  @NotNull
  private static Pair.NonNull<Charset,byte[]> getOverriddenCharsetByBOM(byte @NotNull [] content, @NotNull Charset charset) {
    if (charset.name().contains(CharsetToolkit.UTF8) && CharsetToolkit.hasUTF8Bom(content)) {
      return Pair.createNonNull(charset, CharsetToolkit.UTF8_BOM);
    }
    Charset charsetFromBOM = CharsetToolkit.guessFromBOM(content);
    if (charsetFromBOM != null) {
      byte[] bom = ObjectUtils.notNull(CharsetToolkit.getMandatoryBom(charsetFromBOM), ArrayUtilRt.EMPTY_BYTE_ARRAY);
      return Pair.createNonNull(charsetFromBOM, bom);
    }

    return Pair.createNonNull(charset, ArrayUtilRt.EMPTY_BYTE_ARRAY);
  }

  public static void changeLineSeparators(@Nullable Project project,
                                          @NotNull VirtualFile file,
                                          @NotNull String newSeparator,
                                          @NotNull Object requestor) throws IOException {
    CharSequence currentText = getTextByBinaryPresentation(file.contentsToByteArray(), file, true, false);
    String newText = StringUtil.convertLineSeparators(currentText.toString(), newSeparator);
    file.setDetectedLineSeparator(newSeparator);
    write(project, file, requestor, newText, -1);
  }

  /**
   * Overwrites file with text and sets modification stamp and time stamp to the specified values.
   * <p/>
   * Normally you should not use this method.
   *
   * @param requestor            any object to control who called this method. Note that
   *                             it is considered to be an external change if {@code requestor} is {@code null}.
   *                             See {@link VirtualFileEvent#getRequestor}
   * @param newModificationStamp new modification stamp or -1 if no special value should be set @return {@code Writer}
   * @throws IOException if an I/O error occurs
   * @see VirtualFile#getModificationStamp()
   */
  public static void write(@Nullable Project project,
                           @NotNull VirtualFile virtualFile,
                           @NotNull Object requestor,
                           @NotNull String text,
                           long newModificationStamp) throws IOException {
    Charset existing = virtualFile.getCharset();
    Pair.NonNull<Charset, byte[]> chosen = charsetForWriting(project, virtualFile, text, existing);
    Charset charset = chosen.first;
    byte[] buffer = chosen.second;
    if (!charset.equals(existing)) {
      virtualFile.setCharset(charset);
    }
    setDetectedFromBytesFlagBack(virtualFile, buffer);

    try (OutputStream stream = virtualFile.getOutputStream(requestor, newModificationStamp, -1)) {
      stream.write(buffer);
    }
  }

  @NotNull
  public static Pair.NonNull<Charset, byte[]> charsetForWriting(@Nullable Project project,
                                                                @NotNull VirtualFile virtualFile,
                                                                @NotNull String text,
                                                                @NotNull Charset existing) {
    Charset specified = extractCharsetFromFileContent(project, virtualFile, text);
    Pair.NonNull<Charset, byte[]> chosen = chooseMostlyHarmlessCharset(existing, specified, text);
    Charset charset = chosen.first;

    // in case of "UTF-16", OutputStreamWriter sometimes adds BOM on it's own.
    // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6800103
    byte[] bom = virtualFile.getBOM();
    Charset fromBom = bom == null ? null : CharsetToolkit.guessFromBOM(bom);
    if (fromBom != null && !fromBom.equals(charset)) {
      chosen = Pair.createNonNull(fromBom, text.getBytes(fromBom));
    }
    return chosen;
  }

  private static void setDetectedFromBytesFlagBack(@NotNull VirtualFile virtualFile, byte @NotNull [] content) {
    if (virtualFile.getBOM() == null) {
      guessFromContent(virtualFile, content);
    }
    else {
      // prevent file to be reloaded in other encoding after save with BOM
      setCharsetAutoDetectionReason(virtualFile, AutoDetectionReason.FROM_BOM);
    }
  }

  @NotNull
  public static Pair.NonNull<Charset, byte[]> chooseMostlyHarmlessCharset(@NotNull Charset existing, @NotNull Charset specified, @NotNull String text) {
    try {
      if (specified.equals(existing)) {
        return Pair.createNonNull(specified, text.getBytes(existing));
      }

      byte[] out = isSupported(specified, text);
      if (out != null) {
        return Pair.createNonNull(specified, out); //if explicitly specified encoding is safe, return it
      }
      out = isSupported(existing, text);
      if (out != null) {
        return Pair.createNonNull(existing, out);   //otherwise stick to the old encoding if it's ok
      }
      return Pair.createNonNull(specified, text.getBytes(specified)); //if both are bad there is no difference
    }
    catch (RuntimeException e) {
      Charset defaultCharset = Charset.defaultCharset();
      return Pair.createNonNull(defaultCharset, text.getBytes(defaultCharset)); //if both are bad and there is no hope, use the default charset
    }
  }

  private static byte @Nullable("null means not supported, otherwise it is converted byte stream") [] isSupported(@NotNull Charset charset, @NotNull String str) {
    try {
      if (!charset.canEncode()) return null;
      byte[] bytes = str.getBytes(charset);
      if (!str.equals(new String(bytes, charset))) {
        return null;
      }

      return bytes;
    }
    catch (Exception e) {
      return null;//wow, some charsets throw NPE inside .getBytes() when unable to encode (JIS_X0212-1990)
    }
  }

  @NotNull
  public static Charset extractCharsetFromFileContent(@Nullable Project project, @NotNull VirtualFile virtualFile, @NotNull CharSequence text) {
    return ObjectUtils.notNull(charsetFromContentOrNull(project, virtualFile, text), virtualFile.getCharset());
  }

  @Nullable("returns null if cannot determine from content")
  public static Charset charsetFromContentOrNull(@Nullable Project project, @NotNull VirtualFile virtualFile, @NotNull CharSequence text) {
    return CharsetUtil.extractCharsetFromFileContent(project, virtualFile, virtualFile.getFileType(), text);
  }

  @NotNull
  public static CharSequence loadText(@NotNull final VirtualFile file) {
    FileType type = file.getFileType();
    if (type.isBinary()) {
      final BinaryFileDecompiler decompiler = BinaryFileTypeDecompilers.getInstance().forFileType(type);
      if (decompiler != null) {
        CharSequence text = decompiler.decompile(file);
        try {
          StringUtil.assertValidSeparators(text);
        }
        catch (AssertionError e) {
          LOG.error(e);
        }
        return text;
      }

      throw new IllegalArgumentException("Attempt to load text for binary file which doesn't have a decompiler plugged in: " +
                                         file.getPresentableUrl() + ". File type: " + type.getName());
    }
    return loadText(file, UNLIMITED);
  }

  /**
   * Loads content of given virtual file. If limit is {@value UNLIMITED} then full CharSequence will be returned. Else CharSequence
   * will be truncated by limit if it has bigger length.
   * @param file Virtual file for content loading
   * @param limit Maximum characters count or {@value UNLIMITED}
   * @throws IllegalArgumentException for binary files
   * @return Full or truncated CharSequence with file content
   */
  @NotNull
  public static CharSequence loadText(@NotNull final VirtualFile file, int limit) {
    FileType type = file.getFileType();
    if (type.isBinary()) throw new IllegalArgumentException(
      "Attempt to load truncated text for binary file: " + file.getPresentableUrl() + ". File type: " + type.getName()
    );

    if (file instanceof LightVirtualFile) {
      return limitCharSequence(((LightVirtualFile)file).getContent(), limit);
    }

    if (file.isDirectory()) {
      throw new AssertionError("'" + file.getPresentableUrl() + "' is a directory");
    }
    try {
      byte[] bytes = limit == UNLIMITED ? file.contentsToByteArray() :
                     FileUtil.loadFirstAndClose(file.getInputStream(), limit);
      return getTextByBinaryPresentation(bytes, file);
    }
    catch (IOException e) {
      return Strings.EMPTY_CHAR_SEQUENCE;
    }
  }

  @NotNull
  private static CharSequence limitCharSequence(@NotNull CharSequence sequence, int limit) {
    return limit == UNLIMITED ? sequence : sequence.subSequence(0, Math.min(limit, sequence.length()));
  }

  @NotNull
  public static CharSequence getTextByBinaryPresentation(final byte @NotNull [] bytes, @NotNull VirtualFile virtualFile) {
    return getTextByBinaryPresentation(bytes, virtualFile, true, true);
  }

  @NotNull
  public static CharSequence getTextByBinaryPresentation(byte @NotNull [] bytes,
                                                         @NotNull VirtualFile virtualFile,
                                                         boolean saveDetectedSeparators,
                                                         boolean saveBOM) {
    DetectResult info = detectInternalCharsetAndSetBOM(virtualFile, bytes, bytes.length, saveBOM, virtualFile.getFileType());
    ConvertResult result = convertBytesAndSetSeparator(bytes, bytes.length, virtualFile, saveDetectedSeparators, info, info.hardCodedCharset);
    return result.text;
  }

  @NotNull
  static Set<String> detectAllLineSeparators(@NotNull VirtualFile virtualFile) {
    byte[] bytes;
    try {
      bytes = virtualFile.contentsToByteArray();
    }
    catch (IOException e) {
      return Collections.emptySet();
    }
    DetectResult info = detectInternalCharsetAndSetBOM(virtualFile, bytes, bytes.length, false, virtualFile.getFileType());
    byte[] bom = info.BOM;
    ConvertResult result = convertBytes(bytes, Math.min(bom == null ? 0 : bom.length, bytes.length), bytes.length, info.hardCodedCharset);
    return result.allLineSeparators();
  }

  // written in push way to make sure no-one stores the CharSequence because it came from thread-local byte buffers which will be overwritten soon
  @NotNull
  public static FileType processTextFromBinaryPresentationOrNull(@NotNull ByteSequence bytes,
                                                                 @NotNull VirtualFile virtualFile,
                                                                 boolean saveDetectedSeparators,
                                                                 boolean saveBOM,
                                                                 @NotNull FileType fileType,
                                                                 @NotNull NotNullFunction<? super CharSequence, ? extends FileType> fileTextProcessor) {
    byte[] buffer = ((ByteArraySequence)bytes).getInternalBuffer();
    DetectResult info = detectInternalCharsetAndSetBOM(virtualFile, buffer, bytes.length(), saveBOM, fileType);
    Charset internalCharset = info.hardCodedCharset;
    CharsetToolkit.GuessedEncoding guessed = info.guessed;
    CharSequence toProcess;
    if (internalCharset == null || guessed == CharsetToolkit.GuessedEncoding.BINARY || guessed == CharsetToolkit.GuessedEncoding.INVALID_UTF8) {
      // the charset was not detected so the file is likely binary
      toProcess = null;
    }
    else {
      ConvertResult result = convertBytesAndSetSeparator(buffer, bytes.length(), virtualFile, saveDetectedSeparators, info, internalCharset);
      toProcess = result.text;
    }
    return fileTextProcessor.fun(toProcess);
  }

  @NotNull
  private static ConvertResult convertBytesAndSetSeparator(byte @NotNull [] bytes,
                                                           int length,
                                                           @NotNull VirtualFile virtualFile,
                                                           boolean saveDetectedSeparators,
                                                           @NotNull DetectResult info,
                                                           @NotNull Charset internalCharset) {
    byte[] bom = info.BOM;
    int BOMEndOffset = Math.min(length, bom == null ? 0 : bom.length);
    ConvertResult result = convertBytes(bytes, BOMEndOffset, length, internalCharset);
    if (saveDetectedSeparators) {
      String separator = result.majorLineSeparator();
      // when in doubt, leave old separator
      if (separator != null) {
        virtualFile.setDetectedLineSeparator(separator);
      }
    }
    return result;
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
    String lineSeparator = file.getDetectedLineSeparator();
    if (lineSeparator == null && checkFile) {
      try {
        getTextByBinaryPresentation(file.contentsToByteArray(), file);
        lineSeparator = file.getDetectedLineSeparator();
      }
      catch (IOException e) {
        // null will be returned
      }
    }
    return lineSeparator;
  }

  @NotNull
  public static CharSequence getTextByBinaryPresentation(byte @NotNull [] bytes, @NotNull Charset charset) {
    Pair.NonNull<Charset, byte[]> pair = getOverriddenCharsetByBOM(bytes, charset);
    byte[] bom = pair.getSecond();

    final ConvertResult result = convertBytes(bytes, Math.min(bom.length, bytes.length), bytes.length, pair.first);
    return result.text;
  }

  @NotNull
  private static ConvertResult convertBytes(byte @NotNull [] bytes,
                                            final int startOffset, int endOffset,
                                            @NotNull Charset internalCharset) {
    assert startOffset >= 0 && startOffset <= endOffset && endOffset <= bytes.length: startOffset + "," + endOffset+": "+bytes.length;
    if (internalCharset instanceof SevenBitCharset || internalCharset == StandardCharsets.US_ASCII) {
      // optimisation: skip byte-to-char conversion for ascii chars
      return convertLineSeparatorsToSlashN(bytes, startOffset, endOffset);
    }

    ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, startOffset, endOffset - startOffset);

    CharBuffer charBuffer;
    try {
      charBuffer = internalCharset.decode(byteBuffer);
    }
    catch (Exception e) {
      // esoteric charsets can throw any kind of exception
      charBuffer = CharBuffer.wrap(ArrayUtilRt.EMPTY_CHAR_ARRAY);
    }
    return convertLineSeparatorsToSlashN(charBuffer);
  }

  private static class ConvertResult {
    @NotNull private final CharSequence text;
    private final int CR_count;
    private final int LF_count;
    private final int CRLF_count;

    ConvertResult(@NotNull CharSequence text, int CR_count, int LF_count, int CRLF_count) {
      this.text = text;
      this.CR_count = CR_count;
      this.LF_count = LF_count;
      this.CRLF_count = CRLF_count;
    }

    String majorLineSeparator () {
      String detectedLineSeparator = null;
      if (CRLF_count > CR_count && CRLF_count > LF_count) {
        detectedLineSeparator = "\r\n";
      }
      else if (CR_count > LF_count) {
        detectedLineSeparator = "\r";
      }
      else if (LF_count > 0) {
        detectedLineSeparator = "\n";
      }
      return detectedLineSeparator;
    }

    @NotNull
    Set<String> allLineSeparators() {
      Set<String> result = new HashSet<>();
      if (CR_count > 0) result.add(LineSeparator.CR.getSeparatorString());
      if (LF_count > 0) result.add(LineSeparator.LF.getSeparatorString());
      if (CRLF_count > 0) result.add(LineSeparator.CRLF.getSeparatorString());
      return result;
    }
  }

  private static final Key<AutoDetectionReason> CHARSET_WAS_DETECTED_FROM_BYTES = Key.create("CHARSET_WAS_DETECTED_FROM_BYTES");
  @Nullable("null if was not detected, otherwise the reason it was")
  public static AutoDetectionReason getCharsetAutoDetectionReason(@NotNull VirtualFile virtualFile) {
    return virtualFile.getUserData(CHARSET_WAS_DETECTED_FROM_BYTES);
  }

  private static void setCharsetAutoDetectionReason(@NotNull VirtualFile virtualFile,
                                                    @Nullable("null if was not detected, otherwise the reason it was") AutoDetectionReason reason) {
    virtualFile.putUserData(CHARSET_WAS_DETECTED_FROM_BYTES, reason);
  }

  public static void clearCharsetAutoDetectionReason(@NotNull VirtualFile virtualFile) {
    virtualFile.putUserData(CHARSET_WAS_DETECTED_FROM_BYTES, null);
  }
}
