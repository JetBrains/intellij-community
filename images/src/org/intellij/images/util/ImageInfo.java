// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.util;

public class ImageInfo {
  public final int width;
  public final int height;
  public final int bpp;

  public ImageInfo(int width, int height, int bpp) {
    this.width = width;
    this.height = height;
    this.bpp = bpp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ImageInfo imageInfo = (ImageInfo)o;

    if (bpp != imageInfo.bpp) return false;
    if (height != imageInfo.height) return false;
    if (width != imageInfo.width) return false;

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
