class Foo {

  void m() {

    if (true) {

    } else {

    } <warning descr="Redundant block marker">//endif</warning>

    if (true) {

    } <warning descr="Redundant block marker">// end if</warning>

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