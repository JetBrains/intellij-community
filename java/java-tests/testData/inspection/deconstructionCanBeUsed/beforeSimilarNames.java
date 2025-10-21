// "Replace with record pattern" "true-preview"
class X {

  record R(String oldText, String newText) {
  }

  void patternBug(Object obj) {
    if (obj instanceof R<caret> r) {
      System.out.println(r.oldText());
      System.out.println(r.newText());
      String oldText = "foo";
      String newText = "bar";
    }
  }
}