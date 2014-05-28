class Foo {

  void m() {

    for (int i = 0; i > -1; i--) {

    } //end this is block marker

    while (true) {

    }
    //end while (not block marker)

    while () {

    } // end block marker

    do {

    } while (true);
    //end, not a block marker

  }

}