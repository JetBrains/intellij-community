@interface MyAnnotation {
  String documentation() default "";
}

@My<caret>
class MyClass {}