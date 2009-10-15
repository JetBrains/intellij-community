public abstract class Zzz {

    {
        foo(aaaa(), <caret>)
    }

    int aaaa() {}

    void foo(int a) {}
    void foo(int a, String b) {}
}