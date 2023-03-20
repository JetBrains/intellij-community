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

  /**
   * Change the direction of the animation from the end to the start.
   */
  default Easing reverse() {
    return (x) -> calc(1 - x);
  }

  /**
   * Invert the curve of the easing.
   * For example, if the origin curve
   * runs faster and then slower,
   * then after inverting it will be slower and then faster.
   */
  default Easing invert() { return (x) -> 1.0 - calc(1 - x); }

  /**
   * Run animation from the start to the end and then back.
   */
  default Easing mirror() { return (x) -> calc(x < 0.5 ? (x * 2) : (1 - (x - 0.5) * 2)); }

  /**
   * Make animation wait before and after a run.
   *
   * @param before wait until this value and then start; acceptable value in range [0.0, 0.1]
   * @param after stops at this time; acceptable value in range [0.0, 0.1]
   */
  default Easing freeze(double before, double after) {
    return x -> {
      if (x < before) return 0.0;
      if (x > after) return 1.0;
      return calc((x - before) / (after - before));
    };
  }

  /**
   * Make animation start playing with value <code>start</code> and end with value <code>end</code>.
   *
   * @param start the first value to be passed in {@link #calc(double)}
   * @param end the last value to be passed in {@link #calc(double)}
   */
  default Easing coerceIn(double start, double end) {
    return (x) -> calc(x * (end - start) + start);
  }

  /**
   * Create stateful easing that keeps last updated value.
   */
  default Stateful stateful() { return new Stateful(this); }

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
