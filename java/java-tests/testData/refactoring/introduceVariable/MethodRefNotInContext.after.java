import java.util.function.IntConsumer;

class Foo {
    void test() {
        IntConsumer l = System::exit;
    }
}