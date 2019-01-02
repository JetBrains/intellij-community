public class WithInner<T> {
    T foo () {
        return null;
    }

    class Inner {
        T foo () {
            return  <caret>WithInner.this.foo();
        }
    }

}
