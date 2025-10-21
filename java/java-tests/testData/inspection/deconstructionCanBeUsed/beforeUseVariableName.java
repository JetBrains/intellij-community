// "Replace with record pattern" "true-preview"
class X {

  record R(String oldText, String newText) {}

  void patternBug(Object obj) {
    if (obj instanceof R<caret> r) {
      String superText = r.oldText();
      System.out.println(superText);
      System.out.println(r.newText());
    }
  }
}
