public class Npe {
  public void a(Object o) {
    if (o != null) {
      // Do something
    }

    o.<warning descr="Method invocation 'equals' may produce 'NullPointerException'">equals</warning><error descr="Expected 1 argument but found 0">()</error>;
  }
}