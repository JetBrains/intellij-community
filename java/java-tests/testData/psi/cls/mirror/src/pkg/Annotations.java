package pkg;

abstract class Annotations {
  @interface A1 { }

  @interface A2 {
    String value() default "-";
  }

  @interface A3 {
    A1 a1();
    A2 a2();
  }

  @interface A4 {
    int[] ids() default { };
  }

  @interface A5 {
    boolean b() default false;
    Class<? extends Number> value() default Integer.class;
  }

  @A1 abstract void m1();

  @A2() abstract void m2a();
  @A2("+") abstract void m2b();

  @A3(a1 = @A1, a2 = @A2) abstract void m3();

  @A4 abstract void m4a();
  @A4(ids = {42, 84}) abstract void m4b();

  @A5(b = true, value = Integer.class) abstract void m5();

  @interface IndeterminateAnno {
    float f1() default Float.NEGATIVE_INFINITY;
    float f2() default Float.NaN;
    float f3() default Float.POSITIVE_INFINITY;
    double d1() default Double.NEGATIVE_INFINITY;
    double d2() default Double.NaN;
    double d3() default Double.POSITIVE_INFINITY;
  }
}