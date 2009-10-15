@interface MyAnnotation {
  Class value();
}

@MyAnnotation(value = <caret>A.class)
class MyClass {}