package com.intellij.database.extractors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

public class ImageInfo {
  private static final int MIN_IMAGE_BYTES = 100;
  public final String format;
  public final int width;
  public final int height;
  public final int size;
  public final byte[] bytes;

  public ImageInfo(@NotNull String format, int width, int height, int size, byte @Nullable [] bytes) {
    this.format = format;
    this.width = width;
    this.height = height;
    this.size = size;
    this.bytes = bytes;
  }

  public @Nullable BufferedImage createImage() {
    return bytes != null ? readImage(bytes) : null;
  }

  public @NotNull ImageInfo stripBytes() {
    return new ImageInfo(format, width, height, size, null);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ImageInfo info = (ImageInfo)o;

    if (width != info.width) return false;
    if (height != info.height) return false;
    if (size != info.size) return false;
    if (!format.equals(info.format)) return false;
    return Arrays.equals(bytes, info.bytes);
  }

  @Override
  public int hashCode() {
    int result = format.hashCode();
    result = 31 * result + width;
    result = 31 * result + height;
    result = 31 * result + size;
    result = 31 * result + (bytes != null ? Arrays.hashCode(bytes) : 0);
    return result;
  }

  private static @Nullable BufferedImage readImage(byte @NotNull [] bytes) {
    return extractImageData(bytes, new ImageDataExtractor<>() {
      @Override
      public BufferedImage extract(ImageReader reader) throws Exception {
        return reader.read(0, reader.getDefaultReadParam());
      }
    });
  }

  static <T> T extractImageData(byte @NotNull [] bytes, @NotNull ImageInfo.ImageDataExtractor<T> dataExtractor) {
    if (bytes.length < MIN_IMAGE_BYTES) return null;

    try {
      ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes));
      Iterator<ImageReader> readers = stream != null ? ImageIO.getImageReaders(stream) : Collections.emptyIterator();
      ImageReader reader = readers.hasNext() ? readers.next() : null;
      try {
        if (reader != null) {
          reader.setInput(stream, true, true);
          return dataExtractor.extract(reader);
        }
      }
      finally {
        if (reader != null) {
          reader.dispose();
        }
        if (stream != null) {
          stream.close();
        }
      }
    }
    catch (Exception ignored) {
    }

    return null;
  }

  abstract static class ImageDataExtractor<T> {
    public abstract T extract(ImageReader reader) throws Exception;
  }

  public static @Nullable ImageInfo tryDetectImage(final byte @NotNull [] bytes) {
    return extractImageData(bytes, new ImageInfo.ImageDataExtractor<>() {
      @Override
      public ImageInfo extract(ImageReader reader) throws Exception {
        String format = reader.getFormatName();
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        return new ImageInfo(format, width, height, bytes.length, bytes);
      }
    });
  }
}
