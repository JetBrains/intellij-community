import java.util.*;
class A<<warning descr="Type parameter 'T' is never used">T</warning>> {
}

class B<<warning descr="Type parameter 'S' is never used">S</warning>> extends A {
}

class C extends A<String> {
}


class D extends A {
}


class Main {
  public static void test(Collection<? extends A> <warning descr="Parameter 'c' is never used">c</warning>) {}

  public static void main(String[] args) {
    Collection<B> bs = new ArrayList<B>();
    test(bs);

    Collection<C> cs = new ArrayList<C>();
    test(cs);

    Collection<D> ds = new ArrayList<D>();
    test(ds);
  }
}