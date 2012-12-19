public class FooBar {
    private static final class B<caret>ar {
        private Bar() {
        }
    }

    private interface I<T> {
        T create();
    }

    static void foo(I intf) {}


    public static void main(String[] args) throws Exception {
        foo(Bar::new);
    }
}
