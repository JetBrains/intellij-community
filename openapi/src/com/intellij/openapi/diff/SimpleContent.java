/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.diff;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.*;

/**
 * Allows to compare some text not associated with file or document.
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

  /**
   * Constructs content with given text and null type
   */
  public SimpleContent(String text) {
    this(text, null);
  }

  public SimpleContent(String text, FileType type) {
    myOriginalBytes = text.getBytes();
    myOriginalText = myLineSeparators.correctText(text);
    myDocument = EditorFactory.getInstance().createDocument(myOriginalText);
    setReadOnly(true);
    myType = type;
  }

  /**
   * Make this content editable or not. By default SimpleContent isn't editable.
   */
  public void setReadOnly(boolean readOnly) { myDocument.setReadOnly(readOnly); }

  /**
   * @return modified text.
   * @see #setReadOnly(boolean)
   */
  public String getText() { return myLineSeparators.restoreText(myDocument.getText()); }
  public Document getDocument() { return myDocument; }

  /**
   * @return null
   */
  public OpenFileDescriptor getOpenFileDescriptor(int offset) { return null; }

  /**
   * @return null
   */
  public VirtualFile getFile() { return null; }
  public FileType getContentType() { return myType; }

  /**
   * @return Encodes using default encoding
   * @throws IOException
   */
  public byte[] getBytes() throws IOException {
    String currentText = getText();
    if (myOriginalText.equals(myDocument.getText())) return myOriginalBytes; 
    else return currentText.getBytes();
  }

  /**
   *
   * @param text text of content
   * @param fileName used to determine content type
   * @return
   */
  public static SimpleContent forFileContent(String text, String fileName) {
    FileType fileType;
    if (fileName != null) fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName);
    else fileType = null;
    return new SimpleContent(text, fileType);
  }

  /**
   *
   * @param bytes binary text representaion
   * @param charset name of charset. If null IDE default charset will be used
   * @param fileType content type. If null file name will be used to select file type
   * @return content representing bytes as text
   * @throws UnsupportedEncodingException
   */
  public static SimpleContent fromBytes(byte[] bytes, String charset, FileType fileType) throws UnsupportedEncodingException {
    if (charset == null) charset = CharsetToolkit.getDefaultSystemCharset().name();
    return new SimpleContent(new String(bytes, charset), fileType);
  }

  /**
   *
   * @param file should exist and not to be a directory
   * @param charset name of file charset. If null IDE default charset will be used
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
    } finally { stream.close(); }
  }

  private static class LineSeparators {
    private String mySeparator;

    public String correctText(String text) {
      LineTokenizer lineTokenizer = new LineTokenizer(text);
      String[] lines = lineTokenizer.execute();
      mySeparator = lineTokenizer.getLineSeparator();
      LOG.assertTrue(mySeparator == null || mySeparator.length() > 0);
      if (mySeparator == null) mySeparator = System.getProperty("line.separator");
      return LineTokenizer.concatLines(lines);
    }

    public String restoreText(String text) {
      if (mySeparator == null) throw new NullPointerException();
      return text.replaceAll("\n", mySeparator);
    }
  }
}
