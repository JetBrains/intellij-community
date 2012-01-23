package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.Paths;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import java.io.*;
import java.net.URI;
import java.util.Arrays;

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
  private volatile Content myContent;
  private final File mySourceFile;

  public OutputFileObject(@NotNull JavacFileManager.Context context, @Nullable File outputRoot, String relativePath, @NotNull File file, @NotNull Kind kind, @Nullable String className, @Nullable final URI sourceUri) {
    this(context, outputRoot, relativePath, file, kind, className, sourceUri, null);
  }

  public OutputFileObject(@Nullable JavacFileManager.Context context, @Nullable File outputRoot, String relativePath, @NotNull File file, @NotNull Kind kind, @Nullable String className, @Nullable final URI srcUri, @Nullable Content content) {
    super(Paths.toURI(file.getPath()), kind);
    myContext = context;
    mySourceUri = srcUri;
    myContent = content;
    myOutputRoot = outputRoot;
    myRelativePath = relativePath;
    myFile = file;
    myClassName = className;
    mySourceFile = srcUri != null? Paths.convertToFile(srcUri) : null;
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
          myContent = new Content(buf, 0, size());
          if (myContext != null) {
            myContext.consumeOutputFile(OutputFileObject.this);
          }
        }
      }
    };
  }

  @Override
  public InputStream openInputStream() throws IOException {
    final Content bytes = myContent;
    if (bytes == null) {
      throw new FileNotFoundException(toUri().getPath());
    }
    return new ByteArrayInputStream(bytes.getBuffer(), bytes.getOffset(), bytes.getLength());
  }

  @Override
  public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
    final Content content = myContent;
    if (content == null) {
      throw new FileNotFoundException(toUri().getPath());
    }
    return new String(content.getBuffer(), content.getOffset(), content.getLength());
  }

  @Nullable
  public Content getContent() {
    return myContent;
  }

  public void updateContent(@NotNull byte[] updatedContent) {
    myContent = new Content(updatedContent, 0, updatedContent.length);
  }

  @Override
  public int hashCode() {
    return toUri().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof JavaFileObject && toUri().equals(((JavaFileObject)obj).toUri());
  }

  public static final class Content {
    private final byte[] myBuffer;
    private final int myOffset;
    private final int myLength;

    Content(byte[] buf, int off, int len) {
      myBuffer = buf;
      myOffset = off;
      myLength = len;
    }

    public byte[] getBuffer() {
      return myBuffer;
    }

    public int getOffset() {
      return myOffset;
    }

    public int getLength() {
      return myLength;
    }

    public byte[] toByteArray() {
      return Arrays.copyOfRange(myBuffer, myOffset, myOffset + myLength);
    }
  }
}
