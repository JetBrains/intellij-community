// "Remove variable 'n'" "true"
class C {
  void foo(Object o) {
    if (o instanceof Integer) {
      Integer n<caret>;
      int i = (n = (Integer) o) + 1;
    }
  }
}