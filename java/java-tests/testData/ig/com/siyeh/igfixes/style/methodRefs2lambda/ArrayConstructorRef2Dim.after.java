public class Foo {
    static void foo() {
        Ar<String> a = p -> new String[p][];
    }

    interface Ar<T> {
        T[][] jjj(int p);
    }
}



