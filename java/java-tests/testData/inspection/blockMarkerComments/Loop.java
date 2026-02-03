class Foo {

  void m() {
    for (int i = 0; i > -1; i--) {

    } <warning descr="Redundant block marker">//end</warning>
  }

  void m1() {
    while (true) {

    }
    //end while
    // (not block marker)
  }

  void m2() {
    while (true) {

    } <warning descr="Redundant block marker">// endwhile</warning>
  }

  void m3() {
    do {

    } while (true);
    //end
    //not a block marker
  }

}