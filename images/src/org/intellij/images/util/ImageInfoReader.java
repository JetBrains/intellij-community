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
import com.intellij.util.SVGLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import java.awt.geom.Dimension2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * @author spleaner
 */
public class ImageInfoReader {
  private static final Logger LOG = Logger.getInstance("#org.intellij.images.util.ImageInfoReader");

  private ImageInfoReader() {
  }

  @Nullable
  public static Info getInfo(@NotNull byte[] data) {
    return getInfo(data, null);
  }

  @Nullable
  public static Info getInfo(@NotNull byte[] data, @Nullable String inputName) {
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
    return read(new ByteArrayInputStream(data), inputName);
  }

  private static Info getSvgSize(byte[] data) {
    try {
      Dimension2D size = SVGLoader.getDocumentSize(null, new ByteArrayInputStream(data), 1.0f);
      return new Info((int)Math.round(size.getWidth()), (int)Math.round(size.getHeight()), 32);
    }
    catch (Throwable e) {
      return null;
    }
  }

  @Nullable
  private static Info read(@NotNull Object input, @Nullable String inputName) {
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
        return new Info(w, h, bpp);
      }
    }
    catch (Throwable e) {
      LOG.warn(inputName, e);
    }
    return null;
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
