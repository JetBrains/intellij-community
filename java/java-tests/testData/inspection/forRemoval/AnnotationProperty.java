class Test {
  @A(
    allowed = 1,
    byAnnotation = 2,
    byDocTag = 3,
    <error descr="'forRemoval' is deprecated and marked for removal">forRemoval</error> = 4
  )
  void method() {
  }
}

@interface A {
  int allowed() default 0;

  @Deprecated
  int byAnnotation() default 0;

  /**@deprecated*/
  int byDocTag() default 0;

  @Deprecated(forRemoval = true)
  int forRemoval() default 0;
}