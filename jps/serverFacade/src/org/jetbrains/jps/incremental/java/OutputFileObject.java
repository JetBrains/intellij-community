package org.jetbrains.jps.incremental.java;

import org.jetbrains.annotations.Nullable;

import javax.tools.*;
import java.io.*;
import java.util.Arrays;

/**
 * @author Eugene Zhuravlev
 *         Date: 9/24/11
 */
public class OutputFileObject extends SimpleJavaFileObject {

  private final JavacFileManager.Context myContext;
  private final File myFile;
  @Nullable
  private final String myClassName;
  private final JavaFileObject mySource;
  private volatile Content myContent;

  public OutputFileObject(JavacFileManager.Context context, File file, Kind kind, @Nullable String className, JavaFileObject source) {
    super(file.toURI(), kind);
    myContext = context;
    myFile = file;
    myClassName = className;
    mySource = source;
  }

  public File getFile() {
    return myFile;
  }

  @Nullable
  public String getClassName() {
    return myClassName;
  }

  public JavaFileObject getSource() {
    return mySource;
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
          myContext.consumeOutputFile(OutputFileObject.this);
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
      throw null;
    }
    return new String(content.getBuffer(), content.getOffset(), content.getLength());
  }

  public Content getContent() {
    return myContent;
  }

  public void updateContent(byte[] updatedContent) {
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

    private Content(byte[] buf, int off, int len) {
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
