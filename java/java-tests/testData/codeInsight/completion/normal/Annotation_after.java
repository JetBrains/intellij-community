@interface MyAnnotation {
  Object value();
  String name();
}

@MyAnnotation(value = <caret>) 
class MyClass {}