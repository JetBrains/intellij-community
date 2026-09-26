// "Remove local variable 'n'" "true-preview"
class C {
  void foo(Object o) {
    if (o instanceof Integer) {
        int i = (Integer) o + 1;
    }
  }
}