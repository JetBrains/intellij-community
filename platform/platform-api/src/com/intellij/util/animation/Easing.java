// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.animation;

@FunctionalInterface
public interface Easing {

  double calc(double x);

  static Easing bezier(double c1x, double c1y, double c2x, double c2y) {
    return new CubicBezierEasing(c1x, c1y, c2x, c2y);
  }

  default Easing convert(double t, double b, double c, double d) {
    return x -> c * calc(t / d) + b;
  }

  default Easing reverse() {
    return (x) -> calc(1 - x);
  }

  default Easing invert() { return (x) -> 1.0 - calc(1 - x); }

  Easing LINEAR = n -> n;
}
