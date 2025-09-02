// "Replace method reference with lambda" "true-preview"
public class MyTest {

    interface I {
        void meth(int i);
    }

    static class A {
        void m(int i) {}
    }

    static class B extends A {
        void m(int i1) {
            I i = super:<caret>:m;
        }
    }
}
