public class Foo {
    {
        bar(foo());<caret>
    }

    <T> T foo() {}

    void bar(Object s) {}
}
