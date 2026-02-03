public class Bar {
    {
        foo ( bar ( <caret> ) )
    }

    void foo(int x) {}
    int bar(int x) {}
}
