import java.util.function.Supplier;

class A {
  {
    new <warning descr="Type arguments are redundant for the non-generic method call"><Integer, Long, String></warning>A();
    A.<warning descr="Type arguments are redundant for the non-generic method call"><Integer, Long, String></warning>create();
    Supplier<A> a = A::<warning descr="Type arguments are redundant for the non-generic method reference"><Integer, Long, String></warning>create;
    Supplier<A> b = A::<warning descr="Type arguments are redundant for the non-generic method reference"><Integer, Long, String></warning>new;
  }

  static A create() {
    return new A();
  }
}