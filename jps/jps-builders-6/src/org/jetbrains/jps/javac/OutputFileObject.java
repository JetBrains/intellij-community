/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.javac;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.PathUtils;
import org.jetbrains.jps.incremental.BinaryContent;

import javax.tools.*;
import java.io.*;
import java.net.URI;

/**
 * @author Eugene Zhuravlev
 */
public final class OutputFileObject extends SimpleJavaFileObject {
  @Nullable
  private final JavacFileManager.Context myContext;
  @Nullable
  private final File myOutputRoot;
  private final String myRelativePath;
  private final File myFile;
  @Nullable
  private final String myClassName;
  @Nullable private final URI mySourceUri;
  private volatile BinaryContent myContent;
  private final File mySourceFile;
  private final String myEncodingName;

  public OutputFileObject(@NotNull JavacFileManager.Context context,
                          @Nullable File outputRoot,
                          String relativePath,
                          @NotNull File file,
                          @NotNull Kind kind,
                          @Nullable String className,
                          @Nullable final URI sourceUri,
                          @Nullable final String encodingName) {
    this(context, outputRoot, relativePath, file, kind, className, sourceUri, encodingName, null);
  }

  public OutputFileObject(@Nullable JavacFileManager.Context context,
                          @Nullable File outputRoot,
                          String relativePath,
                          @NotNull File file,
                          @NotNull Kind kind,
                          @Nullable String className,
                          @Nullable final URI srcUri,
                          @Nullable final String encodingName, 
                          @Nullable BinaryContent content) {
    super(PathUtils.toURI(file.getPath()), kind);
    myContext = context;
    mySourceUri = srcUri;
    myContent = content;
    myOutputRoot = outputRoot;
    myRelativePath = relativePath;
    myFile = file;
    myClassName = className != null? className.replace('/', '.') : null;
    mySourceFile = srcUri != null? PathUtils.convertToFile(srcUri) : null;
    myEncodingName = encodingName;
  }

  @Nullable
  public File getOutputRoot() {
    return myOutputRoot;
  }

  public String getRelativePath() {
    return myRelativePath;
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  @Nullable
  public String getClassName() {
    return myClassName;
  }

  @Nullable
  public File getSourceFile() {
    return mySourceFile;
  }

  @Nullable
  public URI getSourceUri() {
    return mySourceUri;
  }

  @Override
  public ByteArrayOutputStream openOutputStream() {
    return new ByteArrayOutputStream() {
      @Override
      public void close() throws IOException {
        try {
          super.close();
        }
        finally {
          myContent = new BinaryContent(buf, 0, size());
          if (myContext != null) {
            myContext.consumeOutputFile(OutputFileObject.this);
          }
        }
      }
    };
  }

  @Override
  public InputStream openInputStream() throws IOException {
    final BinaryContent bytes = myContent;
    if (bytes != null) {
      return new ByteArrayInputStream(bytes.getBuffer(), bytes.getOffset(), bytes.getLength());
    }
    return new BufferedInputStream(new FileInputStream(myFile));
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    final BinaryContent content = myContent;
    final String encoding = myEncodingName;
    if (content != null) {
      return encoding == null? 
             new String(content.getBuffer(), content.getOffset(), content.getLength()) : 
             new String(content.getBuffer(), content.getOffset(), content.getLength(), encoding);
    }
    return FileUtilRt.loadFile(myFile, encoding, false);
  }

  @Override
  public Writer openWriter() throws IOException {
    final String encoding = myEncodingName;
    return encoding != null? new OutputStreamWriter(openOutputStream(), encoding) : super.openWriter();
  }

  @Nullable
  public BinaryContent getContent() {
    return myContent;
  }

  public void updateContent(@NotNull byte[] updatedContent) {
    myContent = new BinaryContent(updatedContent, 0, updatedContent.length);
  }

  @Override
  public int hashCode() {
    return toUri().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof JavaFileObject && toUri().equals(((JavaFileObject)obj).toUri());
  }
}
