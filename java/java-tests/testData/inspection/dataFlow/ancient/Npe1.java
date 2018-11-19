public class Npe {
  public void a(Object o) {
    if (o != null) {
      // Do something
    }

    o.<warning descr="Method invocation 'equals' may produce 'NullPointerException'">equals</warning><error descr="'equals(java.lang.Object)' in 'java.lang.Object' cannot be applied to '()'">()</error>;
  }
}