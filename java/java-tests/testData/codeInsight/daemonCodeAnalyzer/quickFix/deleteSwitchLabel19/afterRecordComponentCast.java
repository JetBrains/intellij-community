// "Remove unreachable branches" "true"
class Test {
    void foo(Rec rec) {
        switch (rec) {
            case Rec(A a) -> a.doA();
            default -> { return; }
        }

        ((A) rec.i()).doA();
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