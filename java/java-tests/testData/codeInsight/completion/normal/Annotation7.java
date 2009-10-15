@interface MyAnnotation {
  Class value();
}

@MyAnnotation(<caret>A.class)
class MyClass {}