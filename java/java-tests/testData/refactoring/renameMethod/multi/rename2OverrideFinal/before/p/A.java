package p;
public class A {

        public final void finalMethod() {
        }
}

class B extends A implements I {

        public void method() {
        }
}

interface I {
        void method();
}