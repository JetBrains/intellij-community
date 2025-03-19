class C {
  void foo() {
    try {
      bar();
    } catch (NullPointerException e) {
      /*same comment*/
      // comment 1
      e.printStackTrace();
    } catch (IllegalStateException e) {
      /*same comment*/
      e.printStackTrace();
      // comment 2
    } catch (ClassCastException e) {
      /* same comment */
      // comment 3
      e.printStackTrace();
    }
  }

  void bar(){}
}