// "Replace with record pattern" "true-preview"
class X {

  record R(String oldText, String newText) {
  }

  void patternBug(Object obj) {
    if (obj instanceof R(String text, String newText1)) {
      System.out.println(text);
      System.out.println(newText1);
      String oldText = "foo";
      String newText = "bar";
    }
  }
}