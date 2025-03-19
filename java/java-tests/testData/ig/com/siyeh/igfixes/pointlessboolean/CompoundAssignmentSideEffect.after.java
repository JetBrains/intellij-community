class CompoundAssignmentSideEffect {

  void m() {
      createSomeObject(/*1*/);// 2
      /*3*/
      /*4*/
  }

  X createSomeObject() {
    return new X();
  }

  class X {
    boolean b = false;
  }
}