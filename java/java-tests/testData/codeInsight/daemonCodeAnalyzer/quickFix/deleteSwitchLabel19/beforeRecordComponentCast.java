// "Remove unreachable branches" "true"
class Test {
    void foo(Rec rec) {
        switch (rec) {
            case Rec(A a) -> a.doA();
            default -> { return; }
        }

        switch (rec) {
            case R<caret>ec(A a) when true -> a.doA();
            default -> {}
        }
    }
}

record Rec(I i) {}

interface I {}

class A implements I {
    void doA() {}
}

class B implements I {
    void doB() {}
}