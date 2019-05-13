import java.lang.Object;

class A {

  class Nested {

  } <warning descr="Redundant block marker">//end class marker</warning>
  //end not a marker

  void m1() {
    Object o = new Object() {

    }; //end anonymous class this is very long comment and it's not marker
    //end not a marker
  }

  void m() {
    Object o;
    o = new Object() {

    }; <warning descr="Redundant block marker">//end marker</warning>
  }

} <warning descr="Redundant block marker">//end marker</warning>
//end not a marker