class Foo {

  void m() {

    if (true) {

    } else {

    } //endif

    if (true) {

    } // end if

    if (true) {

    } else {

    } //it's not block comment (it's not starts wigh "end")

    if (true) {

    }
    // it's not block comment (it's located under if block)

    if (true) { // it's not block comment


    }
  }

}