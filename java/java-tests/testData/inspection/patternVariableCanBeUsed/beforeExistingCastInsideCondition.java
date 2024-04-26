// "Replace cast expression with existing pattern variable 'i'" "true-preview"

class X {
  void test(Object obj) {
    if (obj instanceof Integer i && ((Intege<caret>r)obj).intValue() == 1) {
      doSomething(1);
    }
  }

  void doSomething(Integer i) {}
}