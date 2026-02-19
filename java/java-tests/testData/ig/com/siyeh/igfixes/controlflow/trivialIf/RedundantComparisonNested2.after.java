class Test {
  String test(String s) {
    if (s != null) {
      s = process(s);
    <caret>}
    return s;
  }
  
  native String process(String s);
}