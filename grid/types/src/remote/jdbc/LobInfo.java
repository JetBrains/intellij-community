package com.intellij.database.remote.jdbc;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * @author gregsh
 */
public abstract class LobInfo<T extends LobInfo<?>> implements Comparable<T>, Serializable {
  public final long length;

  private boolean isFullyReloaded;

  private static final ThreadLocal<byte[]> BUFFER = ThreadLocal.withInitial(() -> new byte[8192]);

  public LobInfo(long length) {
    this.length = length;
  }

  public boolean isTruncated() {
    return length != getLoadedDataLength();
  }

  public boolean isFullyReloaded() {
      return isFullyReloaded;
  }

  public void setFullyReloaded(boolean fullyReloaded) {
    this.isFullyReloaded = fullyReloaded;
  }

  public abstract long getLoadedDataLength();

  @Override
  public int compareTo(T o) {
    return Long.compare(length, o.length);
  }

  public static void freeLob(Clob lob) {
    try {
      lob.free();
    }
    catch (Exception | LinkageError e) {
      // nothing
    }
  }

  public static void freeLob(Blob lob) {
    try {
      lob.free();
    }
    catch (Exception | LinkageError e) {
      // nothing
    }
  }

  public static Object fromClob(Clob lob, final int maxLobLength) throws Exception {
    try {
      long length = lob.length();
      int subLength = maxLobLength < length ? maxLobLength : (int)length;
      return new ClobInfo(length, length != subLength && subLength <= 0 ? null :
                                  subLength > 0 ? lob.getSubString(1, subLength) : "");
    }
    finally {
      freeLob(lob);
    }
  }

  public static Object fromString(String string, int maxLobLength) {
    int length = string.length();
    int subLength = Math.min(maxLobLength, length);
    return subLength == length ? string :
           new ClobInfo(length, subLength <= 0 ? null : string.substring(0, subLength));
  }

  public static Object fromBlob(Blob lob, int maxLobLength) throws Exception {
    try {
      long length = lob.length();
      int subLength = maxLobLength < length ? maxLobLength : (int)length;
      return new BlobInfo(length, getLob(lob, length, subLength));
    }
    finally {
      freeLob(lob);
    }
  }

  private static byte @Nullable [] getLob(Blob lob, long length, int subLength) throws SQLException, IOException {
    if (length != subLength && subLength <= 0) {
      return null;
    }
    if (subLength <= 0) {
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
    if (length >= Integer.MAX_VALUE) {
      // for oracle driver we can't get data from lob.getBytes because of overflow of some integer variable,
      // so we try to read it as stream to array
      try (InputStream input = lob.getBinaryStream()) {
        return loadBytes(input, subLength);
      }
    }
    return lob.getBytes(1, subLength);
  }

  private static byte[] loadBytes(@NotNull InputStream o, int maxLength) throws IOException {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    byte[] bytes = BUFFER.get();
    int count = 0;
    while (true) {
      int read = o.read(bytes);
      if (read <= 0) break;
      count += read;
      boolean limit = count >= maxLength;
      int toCopy = limit ? read - (count - maxLength) : read;
      stream.write(bytes, 0, toCopy);
      if (limit) break;
    }
    return stream.toByteArray();
  }

  private static int realMaxLength(int maxLength) {
    return maxLength < 0 ? Integer.MAX_VALUE : maxLength;
  }

  public static @NotNull Object fromInputStream(InputStream o, int maxLength) throws IOException {
    return fromByteArray(loadBytes(o, realMaxLength(maxLength)), maxLength);
  }

  public static @NotNull Object fromReader(Reader o, int maxLength) throws IOException {
    return fromString(String.valueOf(FileUtilRt.loadText(o, realMaxLength(maxLength))), maxLength);
  }

  public static Object fromByteArray(byte[] data, int maxLobLength) {
    int length = data.length;
    int subLength = Math.min(maxLobLength, length);
    return subLength == length ? data :
           new BlobInfo(length, subLength <= 0 ? null : Arrays.copyOf(data, subLength));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LobInfo)) return false;

    LobInfo<?> info = (LobInfo<?>)o;
    return !isTruncated() && !info.isTruncated() && length == info.length;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(length);
  }

  public static class ClobInfo extends LobInfo<ClobInfo> {
    public final String data;

    public ClobInfo(long length, String data) {
      super(length);
      this.data = data;
    }

    @Override
    public long getLoadedDataLength() {
      return data == null ? 0 : data.length();
    }

    @Override
    public int compareTo(ClobInfo o) {
      final int superVal = super.compareTo(o);
      if (superVal != 0) return superVal;
      return Comparing.compare(data, o.data);
    }

    public int compareTo(@NotNull String str) {
      final int lenVal = Long.compare(length, str.length());
      if (lenVal != 0 || length == 0) return lenVal;
      return Comparing.compare(data, str);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ClobInfo)) return false;
      if (!super.equals(o)) return false;

      ClobInfo info = (ClobInfo)o;

      if (!Objects.equals(data, info.data)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (data != null ? data.hashCode() : 0);
      return result;
    }
  }

  public static class BlobInfo extends LobInfo<BlobInfo> {
    public final byte[] data;

    public BlobInfo(long length, byte[] data) {
      super(length);
      this.data = data;
    }

    @Override
    public long getLoadedDataLength() {
      return data != null ? data.length : 0;
    }

    @Override
    public int compareTo(BlobInfo o) {
      final int superVal = super.compareTo(o);
      if (superVal != 0 || length == 0) return superVal;
      return Comparing.compare(data, o.data);
    }

    public int compareTo(byte @NotNull [] bytes) {
      final int lenVal = Long.compare(length, bytes.length);
      if (lenVal != 0 || length == 0) return lenVal;
      return Comparing.compare(data, bytes);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof BlobInfo)) return false;
      if (!super.equals(o)) return false;

      BlobInfo info = (BlobInfo)o;

      if (!Arrays.equals(data, info.data)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (data != null ? Arrays.hashCode(data) : 0);
      return result;
    }
  }

  public static final class FileBlobInfo extends BlobInfo {
    public final File file;

    public FileBlobInfo(File file) {
      super(file.length(), ArrayUtilRt.EMPTY_BYTE_ARRAY);
      this.file = file;
    }

    @Override
    public boolean isTruncated() {
      return false;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FileBlobInfo)) return false;
      if (!super.equals(o)) return false;

      FileBlobInfo info = (FileBlobInfo)o;
      return areFilePathsEqual(file, info.file);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + computeFilePathHashcode(file);
      return result;
    }
  }

  public static final class FileClobInfo extends ClobInfo {
    public final File file;
    public final String charset;

    public FileClobInfo(File file, String charset) {
      super(file.length(), "");
      this.file = file;
      this.charset = charset;
    }

    @Override
    public boolean isTruncated() {
      return false;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FileClobInfo)) return false;
      if (!super.equals(o)) return false;

      FileClobInfo info = (FileClobInfo)o;

      if (!areFilePathsEqual(file, info.file)) return false;
      if (!Objects.equals(charset, info.charset)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + computeFilePathHashcode(file);
      result = 31 * result + (charset != null ? charset.hashCode() : 0);
      return result;
    }
  }

  private static boolean areFilePathsEqual(File file1, File file2) {
    String path1 = file1.getPath();
    String path2 = file2.getPath();
    return SystemInfoRt.isFileSystemCaseSensitive ? path1.equals(path2) : path1.equalsIgnoreCase(path2);
  }

  private static int computeFilePathHashcode(File file) {
    String path = file.getPath();
    return SystemInfoRt.isFileSystemCaseSensitive ? path.hashCode() : path.toLowerCase(Locale.ENGLISH).hashCode();
  }
}
