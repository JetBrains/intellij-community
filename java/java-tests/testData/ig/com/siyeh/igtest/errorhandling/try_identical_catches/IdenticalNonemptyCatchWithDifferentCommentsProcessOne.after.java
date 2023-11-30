class C {
  void foo() {
    try {
      bar();
    } catch (NullPointerException | ClassCastException e) {
      /*same comment*/
      // comment 1
      e.printStackTrace();
    } catch (IllegalStateException e) {
      /*same comment*/
      e.printStackTrace();
      // comment 2
    } // comment 3

  }

  void bar(){}
}