
public final class A {
  interface Consumer {
    void accept(Object o);
  }

  public static void f() {
    Consumer consumer = null;
    var binding = <error descr="Cannot resolve symbol 'UNKNOWN_EXPRESSION'">UNKNOWN_EXPRESSION</error>;
    Object binding1 = binding;

    consumer.accept(binding);
    consumer.accept(binding1);
  }
}