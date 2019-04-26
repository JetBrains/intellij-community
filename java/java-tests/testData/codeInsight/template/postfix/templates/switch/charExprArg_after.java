public class Foo {
    void f(char x) {
        System.out.println(switch (x) {
            <caret>
        });
    }
}