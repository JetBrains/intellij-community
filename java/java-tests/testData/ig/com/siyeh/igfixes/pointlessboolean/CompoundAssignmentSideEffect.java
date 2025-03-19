class CompoundAssignmentSideEffect {

  void m() {
    createSomeObject(/*1*/).b // 2
      &= /*3*//*4*/ true<caret>;
  }

  X createSomeObject() {
    return new X();
  }

  class X {
    boolean b = false;
  }
}