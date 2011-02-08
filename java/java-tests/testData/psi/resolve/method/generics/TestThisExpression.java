public class WithInner<T> {
    T foo () {
        return null;
    }

    class Inner {
        T foo () {
            return  <ref>WithInner.this.foo();
        }
    }

}
