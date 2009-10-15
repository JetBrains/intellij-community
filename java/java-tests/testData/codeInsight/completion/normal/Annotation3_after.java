@interface MyAnnotation {
  Object value();
}

@MyAnnotation(<caret>) 
class MyClass {}