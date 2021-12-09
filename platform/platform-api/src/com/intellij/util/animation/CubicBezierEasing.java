// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.animation;

import java.util.Arrays;

import static java.lang.Math.min;

public final class CubicBezierEasing implements Easing {

  private final double[] xs;
  private final double[] ys;

  public CubicBezierEasing(double c1x, double c1y, double c2x, double c2y) {
    this(c1x, c1y, c2x, c2y, 1024);
  }

  public CubicBezierEasing(double c1x, double c1y, double c2x, double c2y, int size) {
    xs = new double[size];
    ys = new double[size];
    update(c1x, c1y, c2x, c2y);
  }

  public void update(double c1x, double c1y, double c2x, double c2y) {
    for (int i = 0; i < xs.length; i++) {
      xs[i] = bezier(i * 1. / (xs.length - 1), c1x, c2x);
      ys[i] = bezier(i * 1. / (ys.length - 1), c1y, c2y);
    }
  }

  public int getSize() {
    assert xs.length == ys.length;
    return xs.length;
  }

  @Override
  public double calc(double x) {
    int res = Arrays.binarySearch(xs, x);
    if (res < 0) {
      res = -res - 1;
    }
    return ys[min(res, ys.length - 1)];
  }

  private static double bezier(double t, double u1, double u2) {
    double v = 1 - t;
    return 3 * u1 * v * v * t + 3 * u2 * v * t * t + t * t * t;
  }
}
