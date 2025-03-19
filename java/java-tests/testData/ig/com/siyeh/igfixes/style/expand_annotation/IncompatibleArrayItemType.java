class T {
  @interface A {
    String[] value() default "";
  }

  @A(<caret>42)
  void bar() {
  }
}