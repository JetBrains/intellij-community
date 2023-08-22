// "Make 'Foo.f' static" "true-preview"
public class Foo {
    int g,f;
    static void foo() {
        <caret>f = 0;
    }
}
