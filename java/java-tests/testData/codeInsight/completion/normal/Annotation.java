@interface MyAnnotation {
  Object value();
  String name();
}

@MyAnnotation(v<caret>) 
class MyClass {}