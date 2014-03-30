class Some {
  void foo(Object o) {
    if (o instanceof Foo1) System.out.println(o.hashCode());
    if (o instanceof Foo2) System.out.println(o.hashCode());
    if (o instanceof Foo3) System.out.println(o.hashCode());
    if (o instanceof Foo4) System.out.println(o.hashCode());
    if (o instanceof Foo5) System.out.println(o.hashCode());
    if (o instanceof Foo6) System.out.println(o.hashCode());
    if (o instanceof Foo7) System.out.println(o.hashCode());
    if (o instanceof Foo8) System.out.println(o.hashCode());
    if (o instanceof Foo9) System.out.println(o.hashCode());
    if (o instanceof Foo10) System.out.println(o.hashCode());
  }

}

interface Foo1 {}
interface Foo2 {}
interface Foo3 {}
interface Foo4 {}
interface Foo5 {}
interface Foo6 {}
interface Foo7 {}
interface Foo8 {}
interface Foo9 {}
interface Foo10 {}
