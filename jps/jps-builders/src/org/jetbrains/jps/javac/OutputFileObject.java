package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.Utils;

import javax.tools.*;
import java.io.*;
import java.net.URI;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/24/11
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

  public OutputFileObject(@NotNull JavacFileManager.Context context, @Nullable File outputRoot, String relativePath, @NotNull File file, @NotNull Kind kind, @Nullable String className, @Nullable final URI sourceUri) {
    this(context, outputRoot, relativePath, file, kind, className, sourceUri, null);
  }

  public OutputFileObject(@Nullable JavacFileManager.Context context, @Nullable File outputRoot, String relativePath, @NotNull File file, @NotNull Kind kind, @Nullable String className, @Nullable final URI srcUri, @Nullable BinaryContent content) {
    super(Utils.toURI(file.getPath()), kind);
    myContext = context;
    mySourceUri = srcUri;
    myContent = content;
    myOutputRoot = outputRoot;
    myRelativePath = relativePath;
    myFile = file;
    myClassName = className != null? className.replace('/', '.') : null;
    mySourceFile = srcUri != null? Utils.convertToFile(srcUri) : null;
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
    if (bytes == null) {
      throw new FileNotFoundException(toUri().getPath());
    }
    return new ByteArrayInputStream(bytes.getBuffer(), bytes.getOffset(), bytes.getLength());
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    final BinaryContent content = myContent;
    if (content == null) {
      throw new FileNotFoundException(toUri().getPath());
    }
    return new String(content.getBuffer(), content.getOffset(), content.getLength());
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
