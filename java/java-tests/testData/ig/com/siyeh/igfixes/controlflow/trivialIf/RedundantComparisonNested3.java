class Test {
  String test(String s) {
    if (s != null) {
      String res = process(s);
      <caret>if (res != null) return res;
    } else {
      return "";
    }
    return null;
  }
  
  native String process(String s);
}