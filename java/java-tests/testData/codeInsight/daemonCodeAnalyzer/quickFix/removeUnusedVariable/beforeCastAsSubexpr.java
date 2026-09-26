// "Remove local variable 'c'" "true-preview"
class C {
  String s;

  void foo(Object o) {
    if (o instanceof C) {
      C c<caret>;
      String t = (c = (C) o).s;
    }
  }
}