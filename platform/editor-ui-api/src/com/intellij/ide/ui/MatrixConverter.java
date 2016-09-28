package com.intellij.ide.ui;

import com.intellij.util.Matrix;
import com.intellij.util.Vector;

/**
 * @author Sergey.Malenkov
 */
final class MatrixConverter extends ColorConverter {
  private final Double myWeight;
  private final Matrix myMatrix;

  public MatrixConverter(Matrix matrix) {
    this(null, matrix);
  }

  public MatrixConverter(Double weight, Matrix matrix) {
    if (weight != null && !(0 < weight && weight < 1)) throw new IllegalArgumentException("unsupported weight");
    int rows = matrix.getRows();
    if (rows != 3 && rows != 4) throw new IllegalArgumentException("unsupported rows");
    int columns = matrix.getColumns();
    if (columns != 3 && columns != 4) throw new IllegalArgumentException("unsupported columns");
    myWeight = weight;
    myMatrix = matrix;
  }

  @Override
  public int convert(int red, int green, int blue, int alpha) {
    Vector vector = myMatrix.getRows() == 4
                    ? Vector.create(red, green, blue, alpha)
                    : Vector.create(red, green, blue);

    Vector result = vector.multiply(myMatrix);
    if (myWeight != null) {
      if (vector.getSize() != result.getSize()) {
        vector = result.getSize() == 4
                 ? Vector.create(red, green, blue, alpha)
                 : Vector.create(red, green, blue);
      }
      vector = vector.multiply(1 - myWeight);
      result = result.multiply(myWeight).plus(vector);
    }
    red = (int)result.get(0);
    green = (int)result.get(1);
    blue = (int)result.get(2);
    if (result.getSize() == 4) {
      alpha = (int)result.get(3);
    }
    return super.convert(red, green, blue, alpha);
  }
}
