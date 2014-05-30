// "Add missing annotation parameter 'value'" "false"
class Test {

  @MyAnnotati<caret>on
  void m() {

  }

  @interface MyAnnotation {
    String value() default "";
  }

}