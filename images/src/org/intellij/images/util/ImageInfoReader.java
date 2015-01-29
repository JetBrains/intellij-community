/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.images.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author spleaner
 */
public class ImageInfoReader {
  private static final Logger LOG = Logger.getInstance("#org.intellij.images.util.ImageInfoReader");

  private ImageInfoReader() {
  }

  @Nullable
  public static Info getInfo(@NotNull final String file) {
    return read(file);
  }

  @Nullable
  public static Info getInfo(@NotNull final byte[] data) {
    return read(data);
  }

  @Nullable
  private static Info read(@NotNull final String file) {
    final RandomAccessFile raf;
    try {
      //noinspection HardCodedStringLiteral
      raf = new RandomAccessFile(file, "r");
      try {
        return readFileData(raf);
      }
      finally {
        try {
          raf.close();
        }
        catch (IOException e) {
          // nothing
        }
      }
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private static Info read(@NotNull final byte[] data) {
    final DataInputStream is = new DataInputStream(new UnsyncByteArrayInputStream(data));
    try {
      return readFileData(is);
    }
    catch (IOException e) {
      return null;
    }
    finally {
      try {
        is.close();
      }
      catch (IOException e) {
        // nothing
      }
    }
  }


  @Nullable
  private static Info readFileData(@NotNull final DataInput di) throws IOException {
    final int b1 = di.readUnsignedByte();
    final int b2 = di.readUnsignedByte();

    if (b1 == 0x47 && b2 == 0x49) {
      return readGif(di);
    }

    if (b1 == 0x89 && b2 == 0x50) {
      return readPng(di);
    }

    if (b1 == 0xff && b2 == 0xd8) {
      return readJpeg(di);
    }

    //if (b1 == 0x42 && b2 == 0x4d) {
    //  return readBmp(raf);
    //}

    return null;
  }

  @Nullable
  private static Info readGif(DataInput di) throws IOException {
    final byte[] GIF_MAGIC_87A = {0x46, 0x38, 0x37, 0x61};
    final byte[] GIF_MAGIC_89A = {0x46, 0x38, 0x39, 0x61};
    byte[] a = new byte[11]; // 4 from the GIF signature + 7 from the global header

    di.readFully(a);
    if ((!eq(a, 0, GIF_MAGIC_89A, 0, 4)) && (!eq(a, 0, GIF_MAGIC_87A, 0, 4))) {
      return null;
    }

    final int width = getShortLittleEndian(a, 4);
    final int height = getShortLittleEndian(a, 6);

    int flags = a[8] & 0xff;
    final int bpp = ((flags >> 4) & 0x07) + 1;

    return new Info(width, height, bpp);
  }

  private static Info readBmp(RandomAccessFile raf) throws IOException {
    byte[] a = new byte[44];
    if (raf.read(a) != a.length) {
      return null;
    }

    final int width = getIntLittleEndian(a, 16);
    final int height = getIntLittleEndian(a, 20);

    if (width < 1 || height < 1) {
      return null;
    }

    final int bpp = getShortLittleEndian(a, 26);
    if (bpp != 1 && bpp != 4 && bpp != 8 && bpp != 16 && bpp != 24 & bpp != 32) {
      return null;
    }

    return new Info(width, height, bpp);
  }

  @Nullable
  private static Info readJpeg(DataInput di) throws IOException {
    byte[] a = new byte[13];
    while (true) {
      di.readFully(a, 0, 4);

      int marker = getShortBigEndian(a, 0);
      final int size = getShortBigEndian(a, 2);

      if ((marker & 0xff00) != 0xff00) {
        return null;
      }

      if (marker == 0xffe0) {
        if (size < 14) {
          di.skipBytes(size - 2);
          continue;
        }

        di.readFully(a, 0, 12);
        di.skipBytes(size - 14);
      }
      else if (marker >= 0xffc0 && marker <= 0xffcf && marker != 0xffc4 && marker != 0xffc8) {
        di.readFully(a, 0, 6);

        final int bpp = (a[0] & 0xff) * (a[5] & 0xff);
        final int width = getShortBigEndian(a, 3);
        final int height = getShortBigEndian(a, 1);

        return new Info(width, height, bpp);
      }
      else {
        di.skipBytes(size - 2);
      }
    }
  }

  @Nullable
  private static Info readPng(DataInput di) throws IOException {
    final byte[] PNG_MAGIC = {0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    byte[] a = new byte[27];

    di.readFully(a);
    if (!eq(a, 0, PNG_MAGIC, 0, 6)) {
      return null;
    }

    final int width = getIntBigEndian(a, 14);
    final int height = getIntBigEndian(a, 18);
    int bpp = a[22] & 0xff;
    int colorType = a[23] & 0xff;
    if (colorType == 2 || colorType == 6) {
      bpp *= 3;
    }

    return new Info(width, height, bpp);
  }

  private static int getShortBigEndian(byte[] a, int offset) {
    return (a[offset] & 0xff) << 8 | (a[offset + 1] & 0xff);
  }

  private static boolean eq(byte[] a1, int offset1, byte[] a2, int offset2, int num) {
    while (num-- > 0) {
      if (a1[offset1++] != a2[offset2++]) {
        return false;
      }
    }

    return true;
  }

  private static int getIntBigEndian(byte[] a, int offset) {
    return (a[offset] & 0xff) << 24 | (a[offset + 1] & 0xff) << 16 | (a[offset + 2] & 0xff) << 8 | a[offset + 3] & 0xff;
  }

  private static int getIntLittleEndian(byte[] a, int offset) {
    return (a[offset + 3] & 0xff) << 24 | (a[offset + 2] & 0xff) << 16 | (a[offset + 1] & 0xff) << 8 | a[offset] & 0xff;
  }

  private static int getShortLittleEndian(byte[] a, int offset) {
    return (a[offset] & 0xff) | (a[offset + 1] & 0xff) << 8;
  }

  public static class Info {
    public int width;
    public int height;
    public int bpp;

    public Info(int width, int height, int bpp) {
      this.width = width;
      this.height = height;
      this.bpp = bpp;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Info)) return false;

      Info info = (Info)o;

      if (width != info.width) return false;
      if (height != info.height) return false;
      if (bpp != info.bpp) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = width;
      result = 31 * result + height;
      result = 31 * result + bpp;
      return result;
    }
  }

}
