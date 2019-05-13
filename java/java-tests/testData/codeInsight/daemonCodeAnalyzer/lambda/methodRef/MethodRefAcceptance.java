class Test {
    interface IFactory {
        Object m();
    }

    @interface Anno {}

    enum E {}

    interface I {}

    static class Foo<X> { }

    static abstract class ABar {
      protected ABar() {
      }
    }

    static abstract class ABaz {
    }

    void foo(IFactory cf) { }

    void testAssign() {
        IFactory c1 = <error descr="'Anno' is abstract; cannot be instantiated">Anno::new</error>;
        IFactory c2 = <error descr="Enum types cannot be instantiated">E::new</error>;
        IFactory c3 = <error descr="'I' is abstract; cannot be instantiated">I::new</error>;
        IFactory c4 = <error descr="Unexpected wildcard">Foo<?></error>::new;
        IFactory c5 = <error descr="Cannot find class 1">1</error>::new;
        IFactory c6 = <error descr="'ABar' is abstract; cannot be instantiated">ABar::new</error>;
        IFactory c7 = <error descr="'ABaz' is abstract; cannot be instantiated">ABaz::new</error>;

        foo(<error descr="'Anno' is abstract; cannot be instantiated">Anno::new</error>);
        foo(<error descr="Enum types cannot be instantiated">E::new</error>);
        foo(<error descr="'I' is abstract; cannot be instantiated">I::new</error>);
        foo(<error descr="Unexpected wildcard">Foo<?></error>::new);
        foo(<error descr="Cannot find class 1">1</error>::new);
        foo(<error descr="'ABar' is abstract; cannot be instantiated">ABar::new</error>);
        foo(<error descr="'ABaz' is abstract; cannot be instantiated">ABaz::new</error>);
    }
}