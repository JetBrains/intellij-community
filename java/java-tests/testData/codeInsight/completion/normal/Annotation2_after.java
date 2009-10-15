@interface MyAnnotation {
  Object value();
  String name();
}

@MyAnnotation(value = null, name = <caret>) 
class MyClass {}