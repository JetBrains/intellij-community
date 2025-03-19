class Test {
  String test(String s) {
    if (s != null) {
      String res = process(s);
      <caret>if (res != null) return res;
    }
    return null;
  }
  
  native String process(String s);
}