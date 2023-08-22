// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.util;

import com.intellij.ui.svg.SvgKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

public final class ImageInfoReader {
  @Nullable
  public static Info getInfo(byte @NotNull [] data) {
    Info info = getSvgInfo(data);
    if (info != null) return info;

    return read(new ByteArrayInputStream(data));
  }

  @Nullable
  private static Info getSvgInfo(byte @NotNull [] data) {
    for (int i = 0; i < Math.min(data.length, 100); i++) {
      byte b = data[i];
      if (b == '<') {
        Info info = getSvgSize(data);
        if (info != null) {
          return info;
        }
      }
      if (!Character.isWhitespace(b)) {
        break;
      }
    }
    return null;
  }

  private static Info getSvgSize(byte[] data) {
    try {
      Rectangle2D.Float size = SvgKt.getSvgDocumentSize(data);
      return new Info((int)Math.round(size.getWidth()), (int)Math.round(size.getHeight()), 32, true);
    }
    catch (Throwable e) {
      return null;
    }
  }

  @Nullable
  private static Info read(@NotNull Object input) {
    ImageIO.setUseCache(false);
    try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
      if (isAppleOptimizedPNG(iis)) {
        // They are not supported by PNGImageReader
        return null;
      }
      Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
      ImageReader reader = it.hasNext() ? it.next() : null;
      if (reader != null) {
        reader.setInput(iis, true);
        int w = reader.getWidth(0);
        int h = reader.getHeight(0);
        Iterator<ImageTypeSpecifier> it2 = reader.getImageTypes(0);
        int bpp = it2 != null && it2.hasNext() ? it2.next().getColorModel().getPixelSize() : -1;
        return new Info(w, h, bpp, false);
      }
    }
    catch (Throwable ignore) {}
    return null;
  }

  public static class Info extends ImageInfo {
    private final boolean myIsSvg;

    public Info(int width, int height, int bpp, boolean isSvg) {
      super(width, height, bpp);
      myIsSvg = isSvg;
    }

    public boolean isSvg() {
      return myIsSvg;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      Info info = (Info)o;
      return myIsSvg == info.myIsSvg;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), myIsSvg);
    }
  }

  private static final byte[] APPLE_PNG_SIGNATURE = {-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 4, 67, 103, 66, 73};

  private static boolean isAppleOptimizedPNG(@NotNull ImageInputStream iis) throws IOException {
    try {
      byte[] signature = new byte[APPLE_PNG_SIGNATURE.length];
      if (iis.read(signature) != APPLE_PNG_SIGNATURE.length) {
        return false;
      }
      return Arrays.equals(signature, APPLE_PNG_SIGNATURE);
    }
    finally {
      iis.seek(0);
    }
  }
}
