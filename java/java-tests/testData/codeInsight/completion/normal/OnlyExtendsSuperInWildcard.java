package abcdef;

public class Foo {
    void foo() {
        Class<String> classOfString = (Class<? <caret>>)aClass.cast(Long.class);
    }

    static class NClass {}
}
