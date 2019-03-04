
public class TestOverloading1 {
    class A{
        void foo(){}
    }
    class B extends A{
        void foo(){}
    }

    {
        new B().<caret>foo(1);
    }
}
