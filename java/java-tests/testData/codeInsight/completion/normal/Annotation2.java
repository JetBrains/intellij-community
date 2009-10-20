@interface MyAnnotation {
  Object value();
  String name();
}

@MyAnnotation(value = null, n<caret>) 
class MyClass {}