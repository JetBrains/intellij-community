import java.lang.Object;

class A {

  class Nested {

  } //end class marker
  //end not a marker

  void m() {
    Object o = new Object() {

    }; //end anonymous class
    //end not a marker
  }

} //end marker
//end not a marker