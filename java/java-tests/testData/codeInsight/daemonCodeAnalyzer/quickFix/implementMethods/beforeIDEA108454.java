// "Implement methods" "true-preview"
class Test {
    class A<T> {
        public class Inner { }
    }

    interface B<T> {
        T foo();
    }

    <caret>class D implements B<A<String>.Inner> { }
}