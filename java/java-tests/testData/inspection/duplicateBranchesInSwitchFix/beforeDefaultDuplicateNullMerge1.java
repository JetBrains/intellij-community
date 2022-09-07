// "Merge with 'case null'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case null:
        System.out.println(42);
        break;
      default:
        System.out.pri<caret>ntln(42);
    }
  }
}
