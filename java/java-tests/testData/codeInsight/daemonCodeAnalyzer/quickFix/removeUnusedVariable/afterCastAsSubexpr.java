// "Remove variable 'c'" "true"
class C {
  String s;

  void foo(Object o) {
    if (o instanceof C) {
        String t = ((C) o).s;
    }
  }
}