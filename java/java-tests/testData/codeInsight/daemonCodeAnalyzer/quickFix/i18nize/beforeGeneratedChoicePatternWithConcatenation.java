class MyTest {
  void f(String lang, boolean prefix, String article){
    String s = "Not a valid " + lang + " identifier<caret> part in " + (prefix ? article + " prefix" : "suffix");
  }
}