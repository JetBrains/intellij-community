// "Change 1st parameter of method 'foo' from 'String' to 'void'" "false"
public class S {
    void bar() {}
    void foo(String s) {}
    {
        foo(b<caret>ar());
    }
}
  