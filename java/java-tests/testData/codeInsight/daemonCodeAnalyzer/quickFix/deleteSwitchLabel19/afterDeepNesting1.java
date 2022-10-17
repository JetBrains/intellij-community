// "Remove unreachable branches" "true"
class Test {
    void foo(Object obj) {
        switch (obj) {
            case X x -> { }
            default -> { return; }
        }

        X x1 = (X) obj;
        System.out.println(x1.x().x().x().x());
    }
}

record X(X x) { }