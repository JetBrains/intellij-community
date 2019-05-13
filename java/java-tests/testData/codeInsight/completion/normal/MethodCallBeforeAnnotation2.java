class MyClass {
  Object o = new Object();
  String s = o.<caret>

  @Annotation
  String myAnnotatedMethod() { return ""; }
}
