@interface MyAnnotation {
  int value();
  String name();
}

@MyAnnotation(value = 0, name = <caret>)
class MyClass {}