class T {
  @interface A {
    String[] value() default {};
    String type() default "";
  }

  @A(<caret>"a")
  void bar() {
  }
}