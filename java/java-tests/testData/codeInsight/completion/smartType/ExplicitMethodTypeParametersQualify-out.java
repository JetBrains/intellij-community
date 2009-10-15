import java.io.File;

public class Foo {
    {
        bar(this.<File>foo());<caret>
    }

    <T> T foo() {}

    void bar(java.io.File s) {}
}
