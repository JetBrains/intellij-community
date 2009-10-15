@interface MyAnnotation {
  String documentation() default "";
}

@MyAnnotation<caret>
class MyClass {}