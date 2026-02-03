@interface MyAnnotation {
  int value();
  String name();
}

@MyAnnotation(value = 0, n<caret>)
class MyClass {}