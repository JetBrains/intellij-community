// "Convert to local" "false"
class A {
    void foo(int <caret>i) {
        i = 3;
        System.out.println("i = " + i);
    }
}

class B extends A {
    @Override
    void foo(int i) {
        System.out.println("i = " + i); // becomes uncompilable
    }
}