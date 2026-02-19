class C {
  void foo() {
    try {
      bar();
    }
    catch (NumberFormatException e) {
      e = null;
      //non final
    }
    catch (NullPointerException | ClassCastException | IllegalStateException e) {
      /*same comment*/
      // comment 1
      e.printStackTrace();
    } // comment 2
 //line comment
    // comment 3

  }

  void bar(){}
}