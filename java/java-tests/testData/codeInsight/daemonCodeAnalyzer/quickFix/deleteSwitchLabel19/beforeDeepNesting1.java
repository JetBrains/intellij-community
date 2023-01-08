// "Remove unreachable branches" "true"
class Test {
    void foo(Object obj) {
        switch (obj) {
            case X x -> { }
            default -> { return; }
        }

        switch (obj) {
            case X(X(X(X(X x)<caret>))) -> System.out.println(x);
            default -> { }
        }
    }
}

record X(X x) { }