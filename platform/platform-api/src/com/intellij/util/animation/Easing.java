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

  default Easing mirror() { return (x) -> calc(x < 0.5 ? (x * 2) : (1 - (x - 0.5) * 2)); }

  default Stateful stateful() { return new Stateful(this); }

  default Easing coerceIn(double start, double end) {
    return (x) -> calc(x * (end - start) + start);
  }

  Easing EASE = bezier(.25, .1, .25, 1);//default
  Easing LINEAR = n -> n;
  Easing EASE_IN = bezier(.42, 0, 1, 1);
  Easing EASE_OUT = bezier(0, 0, .58, 1);
  Easing EASE_IN_OUT = bezier(.42, 0, .58, 1);

  final class Stateful implements Easing {

    private final Easing delegate;
    public double value;

    private Stateful(Easing delegate) {
      this.delegate = delegate;
    }

    @Override
    public double calc(double x) {
      value = x;
      return delegate.calc(value);
    }
  }
}
