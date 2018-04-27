public class Foo {
    void m(): Foo {
        m().new <caret>

        return new Foo();
    }
}