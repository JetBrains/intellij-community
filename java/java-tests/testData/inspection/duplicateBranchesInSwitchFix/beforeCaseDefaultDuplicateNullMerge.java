// "Merge with 'case null'" "true"
class Test {
  void foo(Object o) {
    switch (o) {
      case null:
        System.out.println(42);
        break;
      case default:
        System.out.p<caret>rintln(42);
    }
  }
}
