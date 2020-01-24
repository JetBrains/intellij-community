import java.util.ArrayList;
import java.util.List;

interface Iface {}
class Cls implements Iface {}
class Foo<T> {}

class InstanceOfNonReified {
  void test(List<Cls> o) {
    boolean b1 = o instanceof ArrayList<Cls>;
    boolean b2 = <error descr="Inconvertible types; cannot cast 'java.util.List<Cls>' to 'java.util.ArrayList<Iface>'">o instanceof ArrayList<Iface></error>;
    boolean b3 = o instanceof <error descr="'List<Cls>' cannot be safely cast to 'Foo<Cls>'">Foo<Cls></error>;
  }

  public static void main(String [] args) {

    Object o = new ArrayList<Object>();
    if (o instanceof <error descr="'Object' cannot be safely cast to 'ArrayList<Integer>'">ArrayList<Integer></error> ai) {
      System.out.println("");
    }
  }
}