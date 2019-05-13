import java.util.function.Consumer;

class Foo {
    void test() {
        Consumer<Integer> l = System::exit;
    }
}