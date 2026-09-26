 
class A {
    public boolean foo() {
        return Math.random() > 0.5;
    }
}

class B extends A {
    @Override
    public boolean foo() {
        if (!super.foo()) {
            return false;
        }
        return Math.random() > 0.5;
    }
}

class Test extends B {
}