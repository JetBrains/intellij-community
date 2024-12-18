value class One {

  <error descr="Variable 'value' might not have been initialized">private int value</error>;

  <error descr="Modifier 'synchronized' not allowed here">synchronized</error> void x() {}
}
value class Two extends <error descr="Cannot inherit from non-abstract value class 'One'">One</error> {}
value class Three extends <error descr="Value classes may only extend abstract value classes or 'java.lang.Object'">java.util.ArrayList</error> {}
abstract value class Four {}
value class Five extends Four {}
class Six extends Four {} // it's valid to extend a value class with an identity class
<error descr="Modifier 'value' not allowed here">value</error> interface Seven {}
<error descr="Modifier 'value' not allowed here">value</error> enum Eight {}
value record Nine(int no) {}
<error descr="Illegal combination of modifiers 'sealed' and 'final'">sealed</error> value class Ten {}
<error descr="Modifier 'non-sealed' not allowed on classes that do not have a sealed superclass">non-sealed</error> value class Eleven {}
abstract sealed value class Twelve {}
value class Thirteen extends Twelve {
  void x() {
    synchronized (this) {
      System.out.println();
    }
  }
}

