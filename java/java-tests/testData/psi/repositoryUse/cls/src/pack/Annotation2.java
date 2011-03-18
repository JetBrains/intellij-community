package pack;

public @interface Annotation2 {
  float f1() default Float.NEGATIVE_INFINITY;
  float f2() default Float.NaN;
  float f3() default Float.POSITIVE_INFINITY;

  double d1() default Double.NEGATIVE_INFINITY;
  double d2() default Double.NaN;
  double d3() default Double.POSITIVE_INFINITY;
}
