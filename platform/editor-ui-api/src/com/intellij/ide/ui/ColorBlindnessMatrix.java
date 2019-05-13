package com.intellij.ide.ui;

import com.intellij.util.Matrix;
import com.intellij.util.Vector;

/**
 * @author Sergey.Malenkov
 */
final class ColorBlindnessMatrix {
  private static final Matrix CORRECTION = Matrix.createIdentity(3);

  private static final Matrix RGB_LMS; // a matrix to convert a RGB color to the LMS space
  private static final Matrix LMS_RGB; // a matrix to convert a LMS color to the RGB space

  private static final Vector WHITE_BLUE;
  private static final Vector WHITE_RED;

  static {
    // a matrix to convert a RGB color to the XYZ space
    Matrix RGB_XYZ = Matrix.create(3,
                                   0.4124, 0.2126, 0.0193,
                                   0.3576, 0.7152, 0.1192,
                                   0.1805, 0.0722, 0.9505);

    // a matrix to convert an XYZ color to the LMS space
    Matrix XYZ_LMS = Matrix.create(3,
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
    WHITE_BLUE = cross(white, blue); // protanopia/deuteranopia
    WHITE_RED = cross(white, red); // tritanopia
  }

  private static Vector cross(Vector left, Vector right) {
    return Vector.create(
      left.get(1) * right.get(2) - left.get(2) * right.get(1),
      left.get(2) * right.get(0) - left.get(0) * right.get(2),
      left.get(0) * right.get(1) - left.get(1) * right.get(0));
  }

  private static Matrix calculate(Matrix simulation, Matrix correction) {
    // We will calculate the error between the color and the color
    // viewed by a color blind user and "spread" this error onto the healthy cones.
    // The correction matrix perform this last step and have been chosen arbitrarily.
    Matrix matrix = simulation.multiply(RGB_LMS);
    if (correction == null) correction = CORRECTION;
    return LMS_RGB.multiply(matrix.plus(correction.multiply(RGB_LMS.minus(matrix))));
  }

  static final class Protanopia {
    private static final double V1 = -WHITE_BLUE.get(1) / WHITE_BLUE.get(0);
    private static final double V2 = -WHITE_BLUE.get(2) / WHITE_BLUE.get(0);
    private static final Matrix SIMULATION = Matrix.create(3, 0, 0, 0, V1, 1, 0, V2, 0, 1);
    private static final Matrix CORRECTION = Matrix.create(3, 1, .7, .7, 0, 1, 0, 0, 0, 1);

    static final Matrix MATRIX = calculate(CORRECTION);

    static Matrix calculate(Matrix correction) {
      return ColorBlindnessMatrix.calculate(SIMULATION, correction);
    }
  }

  static final class Deuteranopia {
    private static final double V1 = -WHITE_BLUE.get(0) / WHITE_BLUE.get(1);
    private static final double V2 = -WHITE_BLUE.get(2) / WHITE_BLUE.get(1);
    private static final Matrix SIMULATION = Matrix.create(3, 1, V1, 0, 0, 0, 0, 0, V2, 1);
    private static final Matrix CORRECTION = Matrix.create(3, 1, 0, 0, .7, 1, .7, 0, 0, 1);

    static final Matrix MATRIX = calculate(CORRECTION);

    static Matrix calculate(Matrix correction) {
      return ColorBlindnessMatrix.calculate(SIMULATION, correction);
    }
  }

  static final class Tritanopia {
    private static final double V1 = -WHITE_RED.get(0) / WHITE_RED.get(2);
    private static final double V2 = -WHITE_RED.get(1) / WHITE_RED.get(2);
    private static final Matrix SIMULATION = Matrix.create(3, 1, 0, V1, 0, 1, V2, 0, 0, 0);
    private static final Matrix CORRECTION = Matrix.create(3, 1, 0, 0, 0, 1, 0, .7, .7, 1);

    static final Matrix MATRIX = calculate(CORRECTION);

    static Matrix calculate(Matrix correction) {
      return ColorBlindnessMatrix.calculate(SIMULATION, correction);
    }
  }
}
