// "Unwrap '"blah"'" "true-preview"
class X {
  @interface MyAnnotation {
    String value() default /*1*/ /*2*/ "blah";
  }
}