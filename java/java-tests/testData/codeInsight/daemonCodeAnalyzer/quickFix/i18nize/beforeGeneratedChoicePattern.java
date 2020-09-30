class MyTest {
  void f(boolean prefix){
    String s = "Not a valid java identifier<caret> part in " + (prefix ? "prefix" : "<br/>suffix");
  }
}