/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.CharsetSettings;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.IllegalCharsetNameException;

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

  @SuppressWarnings({"EmptyCatchBlock"})
  @Nullable
  public Document getDocument() {
    if( myDocument == null )
    {
      if( isBinary() ) return null;

      String text = null;
      try {
        if( CharsetSettings.SYSTEM_DEFAULT_CHARSET_NAME.equals( myCharset ))
          text = new String(myBytes);
        else
          text = new String( myBytes, myCharset );
      }
      catch( UnsupportedEncodingException e ) {}
      catch( IllegalCharsetNameException e )  {}

      //  Still NULL? only if not supported or an exception was thrown.
      //  Decode a string using the truly default encoding.
      if( text == null )
          text = new String( myBytes );
      text = LineTokenizer.correctLineSeparators( text );

      myDocument = EditorFactory.getInstance().createDocument( text );
      myDocument.setReadOnly( true );
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
