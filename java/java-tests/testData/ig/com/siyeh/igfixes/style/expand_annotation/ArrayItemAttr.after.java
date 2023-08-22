class T {
  @interface A {
    String[] value() default {};
    String type() default "";
  }

  @A(value = "a")
  void bar() {
  }
}