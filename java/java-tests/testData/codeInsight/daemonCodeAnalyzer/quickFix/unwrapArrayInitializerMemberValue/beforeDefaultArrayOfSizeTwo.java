// "Unwrap '"one"'" "false"
class X {
  @interface MyAnnotation {
    String value() default {"one", "two"}<caret>;
  }
}