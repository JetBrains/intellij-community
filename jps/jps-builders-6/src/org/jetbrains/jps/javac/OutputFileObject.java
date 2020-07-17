// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.BinaryContent;

import javax.tools.JavaFileManager;
import java.io.*;
import java.net.URI;

/**
 * @author Eugene Zhuravlev
 */
public final class OutputFileObject extends JpsFileObject {
  @Nullable
  private final JpsJavacFileManager.Context myContext;
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

  public OutputFileObject(@NotNull JpsJavacFileManager.Context context,
                          @Nullable File outputRoot,
                          String relativePath,
                          @NotNull File file,
                          @NotNull Kind kind,
                          @Nullable String className,
                          @Nullable final URI sourceUri,
                          @Nullable final String encodingName, final JavaFileManager.Location location) {
    this(context, outputRoot, relativePath, file, kind, className, sourceUri, encodingName, null, location);
  }

  public OutputFileObject(@Nullable JpsJavacFileManager.Context context,
                          @Nullable File outputRoot,
                          String relativePath,
                          @NotNull File file,
                          @NotNull Kind kind,
                          @Nullable String className,
                          @Nullable final URI srcUri,
                          @Nullable final String encodingName,
                          @Nullable BinaryContent content, final JavaFileManager.Location location) {
    super(file.toURI(), kind, location);
    myContext = context;
    mySourceUri = srcUri;
    myContent = content;
    myOutputRoot = outputRoot;
    myRelativePath = relativePath;
    myFile = file;
    myClassName = className != null? className.replace('/', '.') : null;
    mySourceFile = srcUri != null && "file".equalsIgnoreCase(srcUri.getScheme())? new File(srcUri) : null;
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
  @Nullable
  protected String inferBinaryName(Iterable<? extends File> path, boolean caseSensitiveFS) {
    return null; // this will cause FileManager to delegate to JVM implementation
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
      return encoding == null ?
             new String(content.getBuffer(), content.getOffset(), content.getLength()) :
             new String(content.getBuffer(), content.getOffset(), content.getLength(), encoding);
    }
    return loadCharContent(myFile, encoding);
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

}
