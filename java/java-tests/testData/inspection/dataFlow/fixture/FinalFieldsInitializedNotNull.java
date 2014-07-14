import java.lang.IllegalArgumentException;
import java.lang.Object;

class Doo {
  private final Object myA;
  private final Object myB;
  private final Object myC;

  public Doo(Object myA, Object myB, Object c) {
    if (myB == null) {
//      assert myA != null;
      throw new IllegalArgumentException();
    }
    assert c != null;
    this.myA = myA;
    this.myB = myB;
    myC = c;
  }

  int bar() {
    return myC.hashCode();
  }


  int foo() {
    if (<warning descr="Condition 'myB == null' is always 'false'">myB == null</warning>) {
      return 2;
    }
    if (<warning descr="Condition 'myC != null' is always 'true'">myC != null</warning>) {
      return 3;
    }

    return myA.hashCode();
  }
}
