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
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Represents bytes as content. May has text representaion.
 */
public class BinaryContent extends DiffContent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.BinaryContent");
  private final FileType myFileType;
  private final byte[] myBytes;
  private final String myCharset;
  private Document myDocument = null;

  /**
   *
   * @param bytes
   * @param charset use to convert bytes to String. null means bytes can't be converted to text.
   * Has no sense if fileType.isBinary()
   * @param fileType type of content
   */
  public BinaryContent(byte[] bytes, String charset, FileType fileType) {
    myFileType = fileType;
    myBytes = bytes;
    if (fileType != null && fileType.isBinary()) myCharset = null;
    else myCharset = charset;
  }

  public Document getDocument() {
    if (myDocument != null) return myDocument;
    if (isBinary()) return null;
    try {
      String text = LineTokenizer.correctLineSeparators(new String(myBytes, myCharset));
      myDocument = EditorFactory.getInstance().createDocument(text);
      myDocument.setReadOnly(true);
    }
    catch (UnsupportedEncodingException e) {
      LOG.error(e);
    }
    return myDocument;
  }

  /**
   * @return null
   */
  public OpenFileDescriptor getOpenFileDescriptor(int offset) {
    return null;
  }

  /**
   * @return null
   */
  public VirtualFile getFile() {
    return null;
  }

  public FileType getContentType() {
    return myFileType;
  }

  public byte[] getBytes() throws IOException {
    return myBytes;
  }

  public boolean isBinary() {
    return myCharset == null;
  }
}
