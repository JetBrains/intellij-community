public class Super {
  protected <T> T foo() {}
}

public class Foo extends Super {
    {
        bar(fo<caret>)
    }

    void bar(java.io.File s) {}
}
