import java.io.File;

public class Super {
  protected <T> T foo() {}
}

public class Foo extends Super {
    {
        bar(this.<File>foo());<caret>
    }

    void bar(java.io.File s) {}
}
