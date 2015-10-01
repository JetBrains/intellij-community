package com.intellij.ide.ui;

import java.awt.image.ImageFilter;

final class SimulationFilter extends WeightFilter {
  public static final ImageFilter protanopia = forProtanopia(null);
  public static final ImageFilter deuteranopia = forDeuteranopia(null);
  public static final ImageFilter tritanopia = forTritanopia(null);
  public static final ImageFilter achromatopsia = forAchromatopsia(null);

  public static ImageFilter forProtanopia(Double weight) {
    return new SimulationFilter(weight, 0.7465, 0.2535, 1.273463, -0.073894);
  }
  
  public static ImageFilter forDeuteranopia(Double weight) {
    return new SimulationFilter(weight, 1.4, -0.4, 0.968437, 0.003331);
  }

  public static ImageFilter forTritanopia(Double weight) {
    return new SimulationFilter(weight, 0.1748, 0, 0.062921, 0.292119);
  }

  public static ImageFilter forAchromatopsia(Double weight) {
    return new WeightFilter(weight) {
      @Override
      int toRGB(int srcR, int srcG, int srcB) {
        double gray = 0.212656 * srcR + 0.715158 * srcG + 0.072186 * srcB;
        return toRGB(srcR, srcG, srcB, gray, gray, gray);
      }
    };
  }

  private final double myConfuseX;
  private final double myConfuseY;
  private final double myConfuseM;
  private final double myConfuseYint;

  private SimulationFilter(Double weight, double x, double y, double m, double yint) {
    super(weight);
    myConfuseX = x;
    myConfuseY = y;
    myConfuseM = m;
    myConfuseYint = yint;
  }

  @Override
  int toRGB(int srcR, int srcG, int srcB) {
    // Convert source color into XYZ color space
    double powR = Math.pow(srcR, 2.2);
    double powG = Math.pow(srcG, 2.2);
    double powB = Math.pow(srcB, 2.2);
    // RGB->XYZ (sRGB:D65)
    double X = 0.4124240 * powR + 0.357579 * powG + 0.1804640 * powB;
    double Y = 0.2126560 * powR + 0.715158 * powG + 0.0721856 * powB;
    double Z = 0.0193324 * powR + 0.119193 * powG + 0.9504440 * powB;
    // Convert XYZ into xyY Chromacity Coordinates (xy) and Luminance (Y)
    double chroma_x = X / (X + Y + Z);
    double chroma_y = Y / (X + Y + Z);
    // Generate the "Confusion Line" between the source color and the Confusion Point
    double m = (chroma_y - myConfuseY) / (chroma_x - myConfuseX); // slope of Confusion Line
    double yint = chroma_y - chroma_x * m; // y-intercept of confusion line (x-intercept = 0.0)
    // How far the xy coords deviate from the simulation
    double deviate_x = (myConfuseYint - yint) / (m - myConfuseM);
    double deviate_y = (m * deviate_x) + yint;
    // Compute the simulated color's XYZ coords
    X = deviate_x * Y / deviate_y;
    Z = (1.0 - (deviate_x + deviate_y)) * Y / deviate_y;
    // Neutral grey calculated from luminance (in D65)
    double neutral_X = Y * 0.312713 / 0.329016;
    double neutral_Z = Y * 0.358271 / 0.329016;
    // Difference between simulated color and neutral grey
    double diffX = neutral_X - X;
    double diffZ = neutral_Z - Z;
    // XYZ->RGB (sRGB:D65)
    double diffR = +3.2407100 * diffX - 0.4985710 * diffZ;
    double diffG = -0.9692580 * diffX + 0.0415557 * diffZ;
    double diffB = +0.0556352 * diffX + 1.0570700 * diffZ;
    // Convert to RGB color space
    // XYZ->RGB (sRGB:D65)
    double dstR = +3.2407100 * X - 1.537260 * Y - 0.4985710 * Z;
    double dstG = -0.9692580 * X + 1.875990 * Y + 0.0415557 * Z;
    double dstB = +0.0556352 * X - 0.203996 * Y + 1.0570700 * Z;
    // Compensate simulated color towards a neutral fit in RGB space
    double fitR = ((dstR < 0 ? 0 : 1) - dstR) / diffR;
    double fitG = ((dstG < 0 ? 0 : 1) - dstG) / diffG;
    double fitB = ((dstB < 0 ? 0 : 1) - dstB) / diffB;
    double adjust = Math.max(Math.max( // highest value
                    (fitR < 0 || 1 < fitR) ? 0 : fitR,
                    (fitG < 0 || 1 < fitG) ? 0 : fitG),
                    (fitB < 0 || 1 < fitB) ? 0 : fitB);
    // Shift proportional to the greatest shift
    dstR += adjust * diffR;
    dstG += adjust * diffG;
    dstB += adjust * diffB;
    // Apply gamma correction
    dstR = Math.pow(dstR, 1 / 2.2);
    dstG = Math.pow(dstG, 1 / 2.2);
    dstB = Math.pow(dstB, 1 / 2.2);
    return toRGB(srcR, srcG, srcB, dstR, dstG, dstB);
  }
}
