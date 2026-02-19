// "Make 'Foo.f' static" "true-preview"
public class Foo {
    int g;
    static int f;
    static void foo() {
        <caret>f = 0;
    }
}
