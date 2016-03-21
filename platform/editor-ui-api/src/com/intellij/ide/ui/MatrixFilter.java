/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.util.Matrix;
import com.intellij.util.Vector;

import java.awt.image.ImageFilter;

final class MatrixFilter extends WeightFilter {
  private static final Matrix RGB_LMS; // a matrix to convert a RGB color to the LMS space
  private static final Matrix LMS_RGB; // a matrix to convert a LMS color to the RGB space

  private static final Matrix PROTANOPIA_SIMULATION;
  private static final Matrix PROTANOPIA_CORRECTION;

  private static final Matrix DEUTERANOPIA_SIMULATION;
  private static final Matrix DEUTERANOPIA_CORRECTION;

  private static final Matrix TRITANOPIA_SIMULATION;
  private static final Matrix TRITANOPIA_CORRECTION;

  static {
    Matrix RGB_XYZ = Matrix.create(3, // a matrix to convert a RGB color to the XYZ space
                                   0.4124, 0.2126, 0.0193,
                                   0.3576, 0.7152, 0.1192,
                                   0.1805, 0.0722, 0.9505);
    Matrix XYZ_LMS = Matrix.create(3, // a matrix to convert an XYZ color to the LMS space
                                   0.7328, -0.7036, 0.0030,
                                   0.4296, 1.6975, 0.0136,
                                   -0.1624, 0.0061, 0.9834);

    // create direct conversion from RGB to LMS and vice versa

    RGB_LMS = RGB_XYZ.multiply(XYZ_LMS);
    LMS_RGB = RGB_LMS.inverse();

    // To simulate color blindness we remove the data lost by the absence of a cone.
    // This cannot be done by just zeroing out the corresponding LMS component,
    // because it would create a color outside of the RGB gammut.  Instead,
    // we project the color along the axis of the missing component
    // onto a plane within the RGB gammut:
    //  - since the projection happens along the axis of the missing component,
    //    a color blind viewer perceives the projected color the same.
    //  - We use the plane defined by 3 points in LMS space: black, white and
    //    blue and red for protanopia/deuteranopia and tritanopia respectively.

    Vector red = RGB_LMS.getRow(0); // LMS space red
    Vector blue = RGB_LMS.getRow(2); // LMS space blue
    Vector white = Vector.create(1, 1, 1).multiply(RGB_LMS); // LMS space white

    // To find the planes we solve the a*L + b*M + c*S = 0 equation
    // for the LMS values of the three known points. This equation is trivially solved,
    // and has for solution the following cross-products:

    Vector p0 = cross(white, blue); // protanopia/deuteranopia
    Vector p1 = cross(white, red); // tritanopia

    // The following 3 matrices perform the projection of a LMS color
    // onto the given plane along the selected axis

    PROTANOPIA_SIMULATION = Matrix.create(3,
                                          0, 0, 0,
                                          -p0.get(1) / p0.get(0), 1, 0,
                                          -p0.get(2) / p0.get(0), 0, 1);
    DEUTERANOPIA_SIMULATION = Matrix.create(3,
                                            1, -p0.get(0) / p0.get(1), 0,
                                            0, 0, 0,
                                            0, -p0.get(2) / p0.get(1), 1);
    TRITANOPIA_SIMULATION = Matrix.create(3,
                                          1, 0, -p1.get(0) / p1.get(2),
                                          0, 1, -p1.get(1) / p1.get(2),
                                          0, 0, 0);

    // We will calculate the error between the color and the color
    // viewed by a color blind user and "spread" this error onto the healthy cones.
    // The matrices below perform this last step and have been chosen arbitrarily.
    // The amount of correction can be adjusted here.

    PROTANOPIA_CORRECTION = Matrix.create(3, 1, .7, .7, 0, 1, 0, 0, 0, 1);
    DEUTERANOPIA_CORRECTION = Matrix.create(3, 1, 0, 0, .7, 1, .7, 0, 0, 1);
    TRITANOPIA_CORRECTION = Matrix.create(3, 1, 0, 0, 0, 1, 0, .7, .7, 1);
  }

  public static final ImageFilter protanopia = forProtanopia(null, true);
  public static final ImageFilter deuteranopia = forDeuteranopia(null, true);
  public static final ImageFilter tritanopia = forTritanopia(null, true);

  public static ImageFilter forProtanopia(Double weight, boolean fix) {
    return new MatrixFilter("Protanopia", weight, PROTANOPIA_SIMULATION, fix ? PROTANOPIA_CORRECTION : null);
  }

  public static ImageFilter forDeuteranopia(Double weight, boolean fix) {
    return new MatrixFilter("Deuteranopia", weight, DEUTERANOPIA_SIMULATION, fix ? DEUTERANOPIA_CORRECTION : null);
  }

  public static ImageFilter forTritanopia(Double weight, boolean fix) {
    return new MatrixFilter("Tritanopia", weight, TRITANOPIA_SIMULATION, fix ? TRITANOPIA_CORRECTION : null);
  }

  private final Matrix myMatrix;

  private MatrixFilter(String name, Double weight, Matrix simulation, Matrix correction) {
    super(name + (correction == null ? " (simulation matrix)" : " (correction matrix)"), weight);
    Matrix matrix = simulation.multiply(RGB_LMS);
    if (correction != null) {
      Matrix minus = RGB_LMS.minus(matrix);
      Matrix multiply = correction.multiply(minus);
      matrix = matrix.plus(multiply);
    }
    myMatrix = LMS_RGB.multiply(matrix);
  }

  @Override
  int toRGB(int srcR, int srcG, int srcB) {
    Vector vector = Vector.create(srcR, srcG, srcB).multiply(myMatrix);
    return toRGB(srcR, srcG, srcB, fix(vector.get(0)), fix(vector.get(1)), fix(vector.get(2)));
  }

  private static Vector cross(Vector left, Vector right) {
    return Vector.create(
      left.get(1) * right.get(2) - left.get(2) * right.get(1),
      left.get(2) * right.get(0) - left.get(0) * right.get(2),
      left.get(0) * right.get(1) - left.get(1) * right.get(0));
  }
}
