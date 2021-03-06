package com.intellij.ide.ui;

import org.jetbrains.annotations.NonNls;

import java.awt.image.RGBImageFilter;

abstract class WeightFilter extends RGBImageFilter {
  private final String myName;
  private final Double myWeight;

  WeightFilter(String name, Double weight) {
    if (weight != null) {
      if (weight < 0 || 1 < weight) {
        throw new IllegalArgumentException("weight " + weight + " out of [0..1]");
      }
      name = name + " weight: " + weight;
    }
    myName = name;
    myWeight = weight;
    canFilterIndexColorModel = true;
  }

  @Override
  @NonNls
  public String toString() {
    return myName;
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

  static double fix(double value) {
    return Double.isNaN(value) || value < 0 ? 0 : value > 255 ? 255 : value;
  }
}
