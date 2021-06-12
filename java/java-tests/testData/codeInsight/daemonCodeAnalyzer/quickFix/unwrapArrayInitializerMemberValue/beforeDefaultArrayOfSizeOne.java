// "Unwrap '"blah"'" "true"
class X {
  @interface MyAnnotation {
    String value() default {/*1*/"blah"/*2*/}<caret>;
  }
}