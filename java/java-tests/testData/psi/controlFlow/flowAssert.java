// LocalsOrMyInstanceFieldsControlFlowPolicy
class A {
  void f(boolean b) {<caret>
    assert b;
    System.out.println();
  }
}