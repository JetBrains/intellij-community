// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.string.DiffString;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.LineSeparator;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Allows to compare some text not associated with file or document.
 *
 * @see #getText()
 * @see #setReadOnly(boolean)
 */
public class SimpleContent extends DiffContent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.SimpleContent");

  private final byte[] myOriginalBytes;
  private final String myOriginalText;
  private final LineSeparators myLineSeparators = new LineSeparators();
  private final Document myDocument;
  private final FileType myType;
  private Charset myCharset;
  private byte[] myBOM;

  /**
   * Constructs content with given text and null type
   */
  public SimpleContent(String text) {
    this(text, null);
  }

  public SimpleContent(@NotNull String text, FileType type) {
    myOriginalBytes = text.getBytes(StandardCharsets.UTF_8);
    myOriginalText = myLineSeparators.correctText(text);
    myDocument = EditorFactory.getInstance().createDocument(myOriginalText);
    setReadOnly(true);
    myType = type;
  }

  public static SimpleContent createEmpty() {
    final SimpleContent content = new SimpleContent("");
    content.setIsEmpty(true);
    return content;
  }

  /**
   * Make this content editable or not. By default SimpleContent isn't editable.
   */
  public void setReadOnly(boolean readOnly) {
    myDocument.setReadOnly(readOnly);
  }

  /**
   * @return modified text.
   * @see #setReadOnly(boolean)
   */
  public String getText() {
    return myLineSeparators.restoreText(myDocument.getText());
  }

  @Override
  public Document getDocument() {
    return myDocument;
  }

  /**
   * @return null
   */
  @Override
  public Navigatable getOpenFileDescriptor(int offset) {
    return null;
  }

  /**
   * @return null
   */
  @Override
  public VirtualFile getFile() {
    return null;
  }

  @Override
  @Nullable
  public FileType getContentType() {
    return myType;
  }

  /**
   * @return Encodes using default encoding
   */
  @Override
  public byte[] getBytes() {
    String currentText = getText();
    if (myOriginalText.equals(myDocument.getText()) && myCharset == null) {
      return myOriginalBytes;
    }
    else if (myCharset != null) {
      final ByteBuffer buffer = myCharset.encode(currentText).compact();
      int bomLength = myBOM != null ? myBOM.length : 0;
      final int encodedLength = buffer.position();
      byte[] result = new byte[encodedLength + bomLength];
      if (bomLength > 0) {
        System.arraycopy(myBOM, 0, result, 0, bomLength);
      }
      buffer.position(0);
      buffer.get(result, bomLength, encodedLength);
      return result;
    }
    return currentText.getBytes(StandardCharsets.UTF_8);
  }

  @NotNull
  @Override
  public LineSeparator getLineSeparator() {
    return LineSeparator.fromString(myLineSeparators.mySeparator);
  }

  public Charset getCharset() {
    return myCharset;
  }

  public void setCharset(final Charset charset) {
    myCharset = charset;
  }

  /**
   * @param bytes    binary text representation
   * @param charset  name of charset. If null IDE default charset will be used
   * @param fileType content type. If null file name will be used to select file type
   * @return content representing bytes as text
   * @throws UnsupportedEncodingException On encoding errors.
   */
  public static SimpleContent fromBytes(byte[] bytes, String charset, FileType fileType) throws UnsupportedEncodingException {
    if (charset == null) charset = CharsetToolkit.getDefaultSystemCharset().name();
    return new SimpleContent(new String(bytes, charset), fileType);
  }

  /**
   * @param file     should exist and not to be a directory
   * @param charset  name of file charset. If null IDE default charset will be used
   * @param fileType content type. If null file name will be used to select file type
   * @return Content representing text in file
   * @throws IOException On I/O errors.
   */
  public static DiffContent fromIoFile(File file, String charset, FileType fileType) throws IOException {
    if (file.isDirectory()) throw new IllegalArgumentException(file.toString());
    if (fileType == null) fileType = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
    try (BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file))) {
      byte[] bytes = new byte[(int)file.length()];
      int bytesRead = stream.read(bytes, 0, bytes.length);
      LOG.assertTrue(file.length() == bytesRead);
      return fromBytes(bytes, charset, fileType);
    }
  }

  public void setBOM(final byte[] BOM) {
    myBOM = BOM;
  }

  private static class LineSeparators {
    private String mySeparator;

    @NotNull
    public String correctText(@NotNull String text) {
      DiffString.LineTokenizer lineTokenizer = new DiffString.LineTokenizer(DiffString.create(text));
      DiffString[] lines = lineTokenizer.execute();
      mySeparator = lineTokenizer.getLineSeparator();
      LOG.assertTrue(mySeparator == null || !mySeparator.isEmpty());
      if (mySeparator == null) mySeparator = SystemProperties.getLineSeparator();
      return DiffString.concatenate(lines).toString();
    }

    @NotNull
    public String restoreText(@NotNull String text) {
      if (mySeparator == null) throw new NullPointerException();
      return text.replaceAll("\n", mySeparator);
    }
  }
}
