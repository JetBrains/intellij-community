package com.intellij.ide.ui;

import java.awt.image.ImageFilter;

final class DaltonizationFilter extends WeightFilter {
  public static final ImageFilter protanopia = forProtanopia(null);
  public static final ImageFilter deuteranopia = forDeuteranopia(null);
  public static final ImageFilter tritanopia = forTritanopia(null);

  public static ImageFilter forProtanopia(Double weight) {
    return new DaltonizationFilter(weight, 0, 2.02344, -2.52581, 0, 1, 0, 0, 0, 1);
  }

  public static ImageFilter forDeuteranopia(Double weight) {
    return new DaltonizationFilter(weight, 1, 0, 0, 0.494207, 0, 1.24827, 0, 0, 1);
  }

  public static ImageFilter forTritanopia(Double weight) {
    return new DaltonizationFilter(weight, 1, 0, 0, 0, 1, 0, -0.395913, 0.801109, 0);
  }

  private final double[] myMatrix;

  private DaltonizationFilter(Double weight, double... matrix) {
    super(weight);
    myMatrix = matrix;
  }

  @Override
  int toRGB(int srcR, int srcG, int srcB) {
    // RGB to LMS matrix conversion
    double L = (17.8824 * srcR) + (43.5161 * srcG) + (4.11935 * srcB);
    double M = (3.45565 * srcR) + (27.1554 * srcG) + (3.86714 * srcB);
    double S = (0.0299566 * srcR) + (0.184309 * srcG) + (1.46709 * srcB);
    // Simulate color blindness
    double l = L * myMatrix[0] + M * myMatrix[1] + S * myMatrix[2];
    double m = L * myMatrix[3] + M * myMatrix[4] + S * myMatrix[5];
    double s = L * myMatrix[6] + M * myMatrix[7] + S * myMatrix[8];
    // LMS to RGB matrix conversion
    double R = (0.0809444479 * l) + (-0.130504409 * m) + (0.116721066 * s);
    double G = (-0.0102485335 * l) + (0.0540193266 * m) + (-0.113614708 * s);
    double B = (-0.000365296938 * l) + (-0.00412161469 * m) + (0.693511405 * s);
    // Isolate invisible colors to color vision deficiency (calculate error matrix)
    R = srcR - R;
    G = srcG - G;
    B = srcB - B;
    // Shift colors towards visible spectrum (apply error modifications)
    // and add compensation to original values
    double dstR = srcR + (0.0 * R) + (0.0 * G) + (0.0 * B);
    double dstG = srcG + (0.7 * R) + (1.0 * G) + (0.0 * B);
    double dstB = srcB + (0.7 * R) + (0.0 * G) + (1.0 * B);
    return toRGB(srcR, srcG, srcB,
                 dstR < 0 ? 0 : dstR > 255 ? 255 : dstR,
                 dstR < 0 ? 0 : dstR > 255 ? 255 : dstG,
                 dstR < 0 ? 0 : dstR > 255 ? 255 : dstB);
  }
}
