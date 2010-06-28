// "Make 'f' static" "true"
public class Foo {
    int g,f;
    static void foo() {
        <caret>f = 0;
    }
}
