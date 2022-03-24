public class FooBar {
    static final class Bar1 {
        private Bar1() {
        }
    }

    private interface I<T> {
        T create();
    }

    static void foo(I intf) {}


    public static void main(String[] args) throws Exception {
        foo(Bar1::new);
    }
}

class FooBarBaz {
     public static void main(String[] args) throws Exception {
        foo(FooBar.Bar1::new);
    }
}
