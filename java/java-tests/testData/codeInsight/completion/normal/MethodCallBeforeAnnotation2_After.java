class MyClass {
  Object o = new Object();
  String s = o.toString()<caret>

  @Annotation
  String myAnnotatedMethod() { return ""; }
}
