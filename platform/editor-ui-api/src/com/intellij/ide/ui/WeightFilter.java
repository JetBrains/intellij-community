package com.intellij.ide.ui;

import java.awt.image.RGBImageFilter;

abstract class WeightFilter extends RGBImageFilter {
  private final Double myWeight;

  WeightFilter(Double weight) {
    if (weight != null && (weight < 0 || 1 < weight)) {
      throw new IllegalArgumentException("weight " + weight + " out of [0..1]");
    }
    myWeight = weight;
    canFilterIndexColorModel = true;
  }

  @Override
  public final int filterRGB(int x, int y, int rgb) {
    return (0xFF000000 & rgb) | toRGB(0xFF & (rgb >> 16), 0xFF & (rgb >> 8), 0xFF & rgb);
  }

  abstract int toRGB(int srcR, int srcG, int srcB);

  final int toRGB(int srcR, int srcG, int srcB, double dstR, double dstG, double dstB) {
    if (Double.isNaN(dstR)) dstR = 0;
    if (Double.isNaN(dstG)) dstG = 0;
    if (Double.isNaN(dstB)) dstB = 0;
    if (myWeight != null) {
      dstR = dstR * myWeight + srcR * (1 - myWeight);
      dstG = dstG * myWeight + srcG * (1 - myWeight);
      dstB = dstB * myWeight + srcB * (1 - myWeight);
    }
    srcR = (int)dstR;
    srcG = (int)dstG;
    srcB = (int)dstB;
    return (srcR << 16) | (srcG << 8) | srcB;
  }
}
