
interface Z {
    void foo();
}

class A {
    public void foo() {
    }
}

class B extends A implements Z {
    @Override
    public void foo() {

    }
}