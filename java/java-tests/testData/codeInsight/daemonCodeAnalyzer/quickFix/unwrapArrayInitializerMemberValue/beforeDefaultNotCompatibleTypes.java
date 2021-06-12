// "Unwrap '"blah"'" "false"
class X {
  @interface MyAnnotation {
    int value() default {"blah"}<caret>;
  }
}