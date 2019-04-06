public class Foo {
    void f(short x) {
        System.out.println(switch (1 + x) {
            <caret>
        });
    }
}