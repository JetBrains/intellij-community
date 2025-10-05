// "Replace with record pattern" "true-preview"
class X {

  record R(String oldText, String newText) {}

  void patternBug(Object obj) {
    if (obj instanceof R(String superText, String newText)) {
        System.out.println(superText);
      System.out.println(newText);
    }
  }
}
