// "Implement methods" "true"
class Test {
    class A<T> {
        public class Inner { }
    }

    interface B<T> {
        T foo();
    }

    class D implements B<A<String>.Inner> {
        @Override
        public A<String>.Inner foo() {
            return null;
        }
    }
}