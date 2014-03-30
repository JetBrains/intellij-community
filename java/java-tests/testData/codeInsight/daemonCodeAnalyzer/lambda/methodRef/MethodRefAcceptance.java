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
        <error descr="Incompatible types. Found: '<method reference>', required: 'Test.IFactory'">IFactory c1 = Anno::new;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'Test.IFactory'">IFactory c2 = E::new;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'Test.IFactory'">IFactory c3 = I::new;</error>
        IFactory c4 = <error descr="Unexpected wildcard">Foo<?></error>::new;
        IFactory c5 = <error descr="Cannot find class 1">1</error>::new;
        <error descr="Incompatible types. Found: '<method reference>', required: 'Test.IFactory'">IFactory c6 = ABar::new;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'Test.IFactory'">IFactory c7 = ABaz::new;</error>

        foo<error descr="'foo(Test.IFactory)' in 'Test' cannot be applied to '(<method reference>)'">(Anno::new)</error>;
        foo<error descr="'foo(Test.IFactory)' in 'Test' cannot be applied to '(<method reference>)'">(E::new)</error>;
        foo<error descr="'foo(Test.IFactory)' in 'Test' cannot be applied to '(<method reference>)'">(I::new)</error>;
        foo(<error descr="Unexpected wildcard">Foo<?></error>::new);
        foo(<error descr="Cannot find class 1">1</error>::new);
        foo<error descr="'foo(Test.IFactory)' in 'Test' cannot be applied to '(<method reference>)'">(ABar::new)</error>;
        foo<error descr="'foo(Test.IFactory)' in 'Test' cannot be applied to '(<method reference>)'">(ABaz::new)</error>;
    }
}