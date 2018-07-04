/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  public SimpleContent(@NotNull String text, @Nullable FileType type) {
    this(text, type, EditorFactory.getInstance());
  }

  public SimpleContent(@NotNull String text, FileType type, EditorFactory f) {
    myOriginalBytes = text.getBytes();
    myOriginalText = myLineSeparators.correctText(text);
    myDocument = f.createDocument(myOriginalText);
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
    return currentText.getBytes();
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
   * @param text     text of content
   * @param fileName used to determine content type
   */
  public static SimpleContent forFileContent(String text, String fileName) {
    FileType fileType;
    if (fileName != null) {
      fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    }
    else {
      fileType = null;
    }
    return new SimpleContent(text, fileType);
  }

  /**
   * @param bytes    binary text representaion
   * @param charset  name of charset. If null IDE default charset will be used
   * @param fileType content type. If null file name will be used to select file type
   * @return content representing bytes as text
   * @throws UnsupportedEncodingException
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
   * @throws IOException
   */
  public static DiffContent fromIoFile(File file, String charset, FileType fileType) throws IOException {
    if (file.isDirectory()) throw new IllegalArgumentException(file.toString());
    if (fileType == null) fileType = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
    BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file));
    try {
      byte[] bytes = new byte[(int)file.length()];
      int bytesRead = stream.read(bytes, 0, bytes.length);
      LOG.assertTrue(file.length() == bytesRead);
      return fromBytes(bytes, charset, fileType);
    }
    finally {
      stream.close();
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
