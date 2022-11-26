public class Foo {
    public static void main(String[] args) {
        <caret>foo(new X());
    }

    static void foo(X x) {
        System.out.println(1);
        System.out.println(x);
    }

    static class X {
        X() {
            System.out.println(0);
        }

        @Override
        public String toString() {
            return "2";
        }
    }
}