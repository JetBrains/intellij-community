class A {
  // IDEA-138747
  void myMethod() {
    <caret>// int myOldCodeCommentedOut;    */    with an old comment (only closure)
    int somecode;
  }
}