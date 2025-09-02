// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.BinaryContent;
import org.jetbrains.jps.util.Iterators;
import org.jetbrains.jps.util.Iterators.Function;

import javax.tools.JavaFileManager;
import java.io.*;
import java.net.URI;

public final class OutputFileObject extends JpsFileObject {
  @Nullable
  private final JpsJavacFileManager.Context myContext;
  @Nullable
  private final File myOutputRoot;
  private final String myRelativePath;
  private final File myFile;
  @Nullable
  private final String myClassName;
  private final Iterable<URI> mySources;
  private volatile BinaryContent myContent;
  private final String myEncodingName;
  private final boolean myIsGenerated;

  OutputFileObject(@Nullable JpsJavacFileManager.Context context,
                          @Nullable File outputRoot,
                          String relativePath,
                          @NotNull File file,
                          @NotNull Kind kind,
                          @Nullable String className,
                          @NotNull final Iterable<URI> sources,
                          @Nullable final String encodingName,
                          @Nullable BinaryContent content,
                          final JavaFileManager.Location location,
                          boolean isFromGeneratedSource) {
    super(DefaultFileOperations.fileToUri(file), kind, location);
    myContext = context;
    mySources = sources;
    myContent = content;
    myOutputRoot = outputRoot;
    myRelativePath = relativePath;
    myFile = file;
    myClassName = className != null? className.replace('/', '.') : null;
    myEncodingName = encodingName;
    myIsGenerated = isFromGeneratedSource;
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

  public boolean isGenerated() {
    return myIsGenerated;
  }

  @NotNull
  public Iterable<File> getSourceFiles() {
    return Iterators.filter(Iterators.map(getSourceUris(), new Function<URI, File>() {
      @Override
      public File fun(URI uri) {
        return "file".equalsIgnoreCase(uri.getScheme())? new File(uri) : null;
      }
    }), Iterators.<File>notNullFilter());
  }

  @NotNull
  public Iterable<URI> getSourceUris() {
    return mySources;
  }

  @Override
  @Nullable
  protected String inferBinaryName(Iterable<? extends File> path, boolean caseSensitiveFS) {
    return null; // this will cause FileManager to delegate to JVM implementation
  }

  @Override
  public ByteArrayOutputStream openOutputStream() {
    return new ByteArrayOutputStream() {
      private boolean isClosed = false;

      private synchronized boolean markClosed() {
        return !isClosed && (isClosed = true);
      }
      
      @Override
      public void close() throws IOException {
        if (markClosed()) {
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
