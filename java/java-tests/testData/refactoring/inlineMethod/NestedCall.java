class NestedCall {
    int foo(int p) { return p; }
    int <caret>bar(int p) { return foo(p); }

    {
        bar(bar(0));
    }
}