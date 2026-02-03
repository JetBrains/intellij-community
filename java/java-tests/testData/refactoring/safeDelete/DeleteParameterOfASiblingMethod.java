
interface Z {
    void foo(int a);
}

class A {
    public void foo(int <caret>a) {
    }
}

class B extends A implements Z {
    @Override
    public void foo(int a) {

    }
}